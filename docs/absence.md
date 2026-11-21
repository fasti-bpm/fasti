English | [Tiếng Việt](absence.vi.md)

# Substitutes during absence

> **Coming in 1.1.0, not in the 1.0.1 release.** This feature is on the `main` branch and will be
> released with Fasti 1.1.0. The 1.0.1 release and the published 1.0.1 image do not have it; its
> settings have no effect there.

Work should not wait for someone on leave. A user records an absence (from, until, a substitute
and an optional note); while the absence is active, user tasks created for or assigned to that
user go to the substitute. Asked for on the forum in
[2788](https://forum.camunda.io/t/2788) and [38199](https://forum.camunda.io/t/38199).

## What happens during an absence

- **A task for the absent user goes to the substitute**: when a BPMN user task or a CMMN human
  task is created with the absent user as assignee, or is assigned to them later (task API,
  delegation, the web UI), the engine assigns it to the substitute instead.
- **Absent substitutes are followed**: if the substitute is away as well, their substitute
  gets the task, at most `max-hops` users far (default 5). The first present user wins. If the
  way leads back to a user already passed, a substitute no longer exists, or every user in
  reach is away, the task stays with the absent user (nothing is lost).
- **Absent candidate users**: when a task is created, the substitute of each absent candidate
  user becomes a candidate user as well; the absent user stays one
  (`substitute-candidates`, default `true`).
- **The absent user is evidently there** when they assign a task to themselves (claim): that is
  never redirected.
- **Substitutions are recorded**: when the assignee is substituted, the task gets a comment
  such as "Assigned to bob as substitute for alice, who is absent." and the local task variables
  `fasti_absentAssignee` (the absent user) and `fasti_absenceSubstitute` (who got the task). A
  substitute added as candidate user leaves a comment only ("Candidate user bob added as
  substitute for alice, who is absent."), no variables. Comments need history: at history level
  `none` no comment is written, so there only the variables mark a substituted assignee.
  The variables can be queried (`POST /task` with
  `{"taskVariables": [{"name": "fasti_absentAssignee", "operator": "eq", "value": "alice"}]}`;
  the `GET` syntax `name_operator_value` cannot hold the underscores of the names) and stay in
  the history (`includeDeleted`). They are removed once the task moves on from the substitute, for example back to
  the absent user after their return.
- **Tasks the user had before the absence** are not moved automatically: the user (or an
  administrator) hands them over explicitly with **Hand over my open tasks now**. Suspended
  tasks stay. When a user is authenticated, each handover is in the operation log as
  "Assign".

Not covered: tasks created without a process or case (`TaskService#newTask`) are only moved by
the handover, and candidate users added to a task after it was created get no substitute.

## In the web UI

- **Preferences → Absence**: set from (empty for now), until, the substitute (with user search)
  and a note; end the absence; hand over your open tasks now.
- **Tasklist**: a banner says when you are away or about to be, and for whom you stand in.
  Cards of tasks held for an absent user show "For *name*"; the task details say
  "Assigned to you as substitute for *name*".
- **Admin → Users → a user → Absence**: the same for any user, for administrators.

## REST API

All below `/engine-rest/user/{id}/absence` (also in the OpenAPI document):

| Method and path | What it does |
|---|---|
| `GET /user/{id}/absence` | The absence (`status`: `none`, `upcoming`, `active`, `ended`), `enabled` and, while active, `effectiveSubstituteId` |
| `PUT /user/{id}/absence` | Sets it: `{"from": "...", "until": "...", "substituteId": "bob", "note": "..."}`; `from` optional |
| `DELETE /user/{id}/absence` | Ends it |
| `POST /user/{id}/absence/handover?maxTasks=500` | Hands the open tasks over now: `{"substituteId", "handedOver", "remaining"}` |
| `GET /user/{id}/absence/substituting` | The absences active now whose tasks go to this user |

Dates use the date format of the REST API (`yyyy-MM-dd'T'HH:mm:ss.SSSZ`, with time zone). The
engine compares them with its clock in UTC, so absences work across time zones.

Validation (`400`): an end is required, after the start and in the future; the substitute
must exist and differ from the user; a substitute who is absent at the same time with
substitutes leading back to the user is refused; the note has at most 255 characters.

**Authorization**: a user manages their own absence. The absence of another user needs
`READ` (to see) or `UPDATE` (to change, hand over) on that user (resource `USER`), which
members of `camunda-admin` have. The handover checks this permission, not the permissions on
each task. Without authorization checks, as everywhere in the engine, anyone may.

Java: `Absences.of(processEngine)` returns the `AbsenceService` with the same operations.

## Turning it on

| Distribution | Setting |
|---|---|
| Fasti BPM Run | On in `default.yml` (`fasti.absence.enabled: true`); off in `production.yml` until you uncomment it |
| Spring Boot starter | `fasti.absence.enabled=true` (off by default) |
| Container image | `FASTI_ABSENCE_ENABLED=true` (off by default) |
| Tomcat, WildFly | Uncomment the plugin `dev.fasti.engine.absence.AbsenceSubstitutionPlugin` in `bpm-platform.xml` / `standalone.xml` |

Further properties: `fasti.absence.max-hops` (default `5`) and
`fasti.absence.substitute-candidates` (default `true`); for Tomcat and WildFly the plugin
properties `maxHops` and `substituteCandidates`.

Why off by default for embedded engines and existing databases: the setting changes how tasks
are assigned, and every node of a cluster should agree on it. Nothing changes until a user
records an absence; the cost is one small query of the user info per task assignment.

## Storage and compatibility

Absences are stored as user info of the identity service (table `ACT_ID_INFO`, keys
`fasti.absence.from`, `fasti.absence.until`, `fasti.absence.substitute`, `fasti.absence.note`,
times as ISO-8601 instants in UTC). **The database schema stays that of Camunda 7.24**: no new
table or column, rollback stays possible.

- **Camunda 7.24 nodes** on the same database ignore absences (they do not substitute) and
  keep the user info untouched. Turn the feature on for every Fasti node at once.
- **LDAP and other read-only identity providers**: users cannot be changed through the engine,
  but their user info lives in the engine database, so absences work. The substitute must be a
  user the identity provider knows.
- **Single sign-on**: the same; the substitute must be known to the identity service.
- **Without the identity tables** (`dbIdentityUsed=false` and no `ACT_ID_*` tables) absences
  cannot be stored: the plugin logs why, the REST API answers `enabled: false` with the reason,
  and the web UI says the feature is not available.
- Deleting a user (through the engine) deletes their absence with the other user info.
- Ended absences are removed the next time any absence is set or ended, so the table holds
  current and upcoming absences only. Until then `GET /user/{id}/absence` still answers
  `status: ended`.
- `ACT_ID_INFO` has no index on the user or the key (and gets none: the schema is not changed),
  so each lookup reads the table. A command looks a user up once, a task with several candidate
  users and the list "who do I stand in for" read all absences with one statement.
- Writes of absences hold the exclusive lock of the engine (the row of `ACT_GE_PROPERTY` that
  deployments lock), so two first writes for the same user at the same time cannot mix, and two
  users who name each other at the same time are not both accepted (the check for a way back
  runs under the lock). Like the engine for deployments, Fasti takes no such lock on H2, which
  is meant for development.
