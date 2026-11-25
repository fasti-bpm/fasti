English | [Tiếng Việt](four-eyes.vi.md)

# Four-eyes control for approvals

> **New in 1.1.0.** The 1.0.x releases and images do not have this feature; its settings have no
> effect there.

In regulated work an approval only counts if someone other than the person who prepared the
work gives it. Fasti BPM lets a model say so on the approving user task, and the engine
enforces it: the person who completed one of the named earlier steps of the same process
instance cannot complete the approval. The web UI says so before anyone tries.

## Declaring the rule

Add the extension property `fasti:fourEyes` to the user task that checks, with the ids of
the earlier user tasks it checks, comma separated:

```xml
<bpmn:userTask id="approveContract" name="Approve contract" camunda:candidateGroups="legal">
  <bpmn:extensionElements>
    <camunda:properties>
      <camunda:property name="fasti:fourEyes" value="draftContract, reviewContract" />
    </camunda:properties>
  </bpmn:extensionElements>
</bpmn:userTask>
```

- The named steps must be user tasks of the same process (also inside embedded
  sub-processes). A name that is not, or an empty list, fails the deployment with a parse
  error that points at the task.
- A task may name itself: then every completion of it needs another person, for example
  "two different people approve" with a parallel multi-instance task, or a step that comes
  round again in a loop.
- Several user tasks of a process may have rules, and a task can be checked and check at
  the same time (draft → review checks draft → approve checks review).
- In the modeler of the web UI, the properties panel of a user task has a **Four-eyes
  check** section with one check box per user task of the process and one for the task
  itself. The checks before deploying refuse what the engine would refuse.

The `camunda` namespace and a `camunda:property` are standard Camunda 7 extensions, so the
model stays readable by any Camunda 7 tool; there is no new XML namespace.

## Who "did" a step

A person did a step when they **completed** it, as

- the authenticated user who completed it (also without claiming it first), or
- its **assignee** at completion (for example when a manager completed it for them).

Both count, which is the stricter reading. A step that ended otherwise (cancelled by a
boundary event, deleted with its instance, ended with a BPMN error) was not done.

With [substitutes during absence](absence.md), the substitute who held and completed a step
is the one who did it, not the absent user.

## What the engine refuses

When a user task with a rule is about to be **completed**, its **form submitted**, or ended
with a **BPMN error** or an **escalation**, the engine looks at two people: the
authenticated user and the assignee of the task. If either of them did one of the checked
steps of the same process instance, the engine refuses with a `FourEyesViolationException`
(an `AuthorizationException`), and nothing changes. If neither exists while a checked step
has been done, the engine refuses as well, because it cannot tell who approves.

Allowed on purpose:

- **Claiming and assigning** are not refused: assignment rules (for example
  `camunda:assignee="${initiator}"`) run while the previous step completes and must not fail
  it. The tasklist warns instead, and the completion is refused.
- **Resolving** a delegated task (its form, or `resolve`) hands it back to its owner and is
  not a completion.
- There is **no administrator override**. Members of `camunda-admin` are refused like
  anyone else. If nobody allowed is left, reassign the task to a person who did not do the
  checked steps. Whoever repairs the instance with a modification counts as having done the
  checked steps (see the next point), so another person completes the guarded task afterwards.
- **Starting in the middle counts.** Whoever places a token into the process by hand may skip
  the checked steps that way: a start at another activity than the start event
  (`startInstructions`), a restart, a process instance modification that starts an activity, or a
  migration that maps an active activity to a different one or into a task that the target
  version guards. From 1.3.1 that user counts as having done
  every checked step of the instance, so the task that checks them needs another person. For an
  asynchronous restart, modification or migration (a batch) both the user who created the batch
  and the user who ran its jobs count. Calls of the Java API without an authenticated user are not
  recorded, and instances changed this way before 1.3.1 carry no record.

Scope: one process instance. A called process (call activity) is a process instance of its
own; its rules name its own steps.

## In the web UI

- **Task details**: if you completed a checked step, a notice says "You completed an earlier
  step of this case that this task checks (…), so another person must complete it." and the
  form cannot be submitted. If the assignee did, the notice names them and asks to assign the
  task to another person. On a task with a rule that does not stop you, a short line says
  who may not complete it.
- A refusal by the engine (for example from another window) is explained in words instead of
  as a missing permission.
- **Modeler**: the **Four-eyes check** section of a user task (see above).

## REST API

| Method and path | What it does |
|---|---|
| `GET /task/{id}/four-eyes` | For the user of the request: `enabled`, `guarded`, `earlierSteps` (`id`, `name`), `userExcluded` and `userSteps`, `assignee`, `assigneeExcluded` and `assigneeSteps`, and `blocked` (whether the engine would refuse the completion now). Needs `READ` on the task; `404` for a missing task and, like `GET /task/{id}`, for one the user may not read |
| `POST /task/{id}/complete`, `/submit-form`, `/bpmnError`, `/bpmnEscalation` | Refused with `403` and `{"type": "FourEyesViolationException", "message": "Four-eyes control: …"}` |
| Any request that sets or removes a variable `fasti_completedBy_…` of a process instance (`PUT`, `POST`, `DELETE` on the variables of a task, process instance or execution; `variables` of `complete`, `submit-form`, a message, a modification, …) | Refused with `403` and `{"type": "AuthorizationException", "message": "Four-eyes control: variable … is written by the engine only; …"}`, see [Limits](#limits) |

Java: `FourEyes.of(processEngine)` returns the `FourEyesService` with `isEnabled()` and
`getStatus(taskId)`; the exception is `dev.fasti.engine.foureyes.FourEyesViolationException`
with the task, the excluded users and the steps they did.

## Turning it on

| Distribution | Setting |
|---|---|
| Fasti BPM Run | On in `default.yml` and `production.yml` (`fasti.four-eyes.enabled: true`) |
| Container image | On; `FASTI_FOUR_EYES_ENABLED=false` turns it off |
| Tomcat, WildFly | On: plugin `dev.fasti.engine.foureyes.FourEyesPlugin` in `bpm-platform.xml` / `standalone.xml` |
| Spring Boot starter | `fasti.four-eyes.enabled=true` (off by default, like every Fasti engine feature in the starter) |

Why on in the distributions: the plugin only acts on user tasks whose model declares a rule,
and a declared control that is not enforced is the worse failure. Without the plugin the
property is an ordinary extension property: nothing is checked, nothing is recorded, and
`GET /task/{id}/four-eyes` answers `enabled: false`.

Cost: per completion of a checked or checking task, one read of the process instance
variables (usually already loaded), one query of the historic task instances and one of
the operation log of that instance (both on indexed columns), and one extra update of the
process instance row.

## Storage and compatibility

**The database schema stays that of Camunda 7.24.** The plugin records who completed a
checked step in a process instance variable, `fasti_completedBy_<id of the step>`, a JSON
array of user ids (`["alice","bob"]`). Where history is kept, it also reads the assignees of
the completed historic task instances (`activity` and above) and the users of the
`Complete` entries of the user operation log (`full`). The union of all three counts.

- **Every history level works**, `none` included, because the variable exists at every
  level. With `none`, the variable is the only record.
- **Camunda 7.24 nodes and nodes without the plugin** on the same database do not enforce
  rules and do not write the variable. A step completed on such a node still counts on a
  Fasti node wherever history is kept (the historic assignee, or the operation log at level
  `full`). Turn the plugin on for every node.
- **Rollback to Camunda 7.24**: the variables stay as ordinary string variables; nothing
  else was written.
- **Deployed without the plugin**: a model with a wrong rule that was deployed while the
  plugin was off is not refused when a Fasti node loads it, so its instances keep running;
  the wrong names are left out and logged as a warning.
- **Concurrency**: completing a checked step and a step that checks it in the same instance
  at the same time (parallel branches) cannot both commit: both update the process instance,
  so one of them fails with an `OptimisticLockingException` and, retried, is refused.
  Completions of checked steps of one instance are serialized the same way.

<a id="limits"></a>

Limits:

- **The record is reserved.** On a node with the plugin, a variable named
  `fasti_completedBy_…` in a process instance is written by the control only. Every other
  attempt to set or remove it is refused (`403`, in Java a `ReservedVariableException`, an
  `AuthorizationException`), whichever way it comes: the variables of a task, process instance
  or execution (REST `PUT`, `POST`, `DELETE`; `setVariable`, `removeVariable` and their
  variants), the variables passed to `complete`, a form, a message, a signal, a modification,
  a batch or an external task, a new instance started with such a variable, and delegates,
  listeners, scripts and mappings of the model. No permission allows it, also not for
  administrators. (Before this rule, any candidate or assignee could empty the record through
  the variables of their task.)
  - Setting a record to the value it already has is accepted and changes nothing, so forms
    and workers that send back every variable they read keep working.
  - Copies of all variables leave the records out: a call activity with
    `variables="all"` (both directions) and the restart of a process instance.
    `removeVariables()` in a delegate keeps them.
  - A local variable of a task with such a name is not the record and is not restricted.
- **What the engine cannot prevent:** a change in the database itself, and a node without the
  plugin (Camunda 7.24, or the plugin turned off) on the same database, where the variable is
  an ordinary one. Where history is kept, the historic data still counts when the variable is
  gone or damaged; with history `none` it does not.
- The variable holds each user once; more than about a hundred different people completing
  the same checked step in one instance may exceed the size of a string variable on Oracle
  (2000 characters).
- CMMN human tasks are not covered.
- Admin-only completion paths outside the task service (process instance modification,
  deleting the instance) are not completions and are not checked: a modification can still move
  the token past the guarded task without completing it. Grant `UPDATE_INSTANCE` accordingly.
