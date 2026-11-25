English | [Tiếng Việt](personal-data.vi.md)

# Personal data and retention

Fasti BPM stores what your processes give it. This page says where personal data ends up, how
long it stays, and what to set so that it does not stay longer than you need (for example under
the GDPR). It describes the engine; your processes decide what goes into it.

## Where personal data is stored

| Data | Tables | Removed by |
|---|---|---|
| User accounts (id, name, e-mail, salted password hash), groups, memberships | `ACT_ID_*` | deleting the user in Admin or through the REST API; not used with single sign-on when users come from the token |
| Who works on what: assignees, candidate users, owners of tasks | `ACT_RU_TASK`, `ACT_RU_IDENTITYLINK` while running; `ACT_HI_TASKINST`, `ACT_HI_IDENTITYLINK` afterwards | the end of the task; history cleanup |
| Process variables, business keys, form data | `ACT_RU_VARIABLE` while running; `ACT_HI_VARINST`, `ACT_HI_DETAIL` afterwards; large values in `ACT_GE_BYTEARRAY` | the end of the instance; history cleanup |
| Comments and attachments | `ACT_HI_COMMENT`, `ACT_HI_ATTACHMENT`, `ACT_GE_BYTEARRAY` | history cleanup |
| Who did what (user operation log) | `ACT_HI_OP_LOG` | history cleanup |
| Four-eyes records: who completed a checked step | the process variable `fasti_completedBy_<step>` | as other variables |
| Audit trail (if switched on) | `FASTI_AUDIT_EVENT`, `FASTI_AUDIT_HEAD` | **not** history cleanup; the audit purge (`POST /audit/purge`, see [audit trail](../audit-trail.md)) |
| Absences and substitutes (if switched on) | user info `ACT_ID_INFO`; task variables `fasti_absentAssignee`, `fasti_absenceSubstitute` | deleting the absence; history cleanup |

Process analytics compute from the history above and keep only a short cache in memory
(`FASTI_ANALYTICS_CACHE_TTL`, 60 s by default); they store nothing of their own. Logs contain user
ids (for example of failed logins) but no passwords or process data. Fasti sends no telemetry.

## Keeping history no longer than needed

History is removed only by **history cleanup**, and only where a time to live is set:

1. Give every process and decision definition a history time to live, in the model
   (`camunda:historyTimeToLive="P180D"` or a number of days) or for all deployments with the
   engine property `historyTimeToLive`.
2. Set a cleanup window, for example 22:00–06:00, with the engine properties
   `historyCleanupBatchWindowStartTime` and `historyCleanupBatchWindowEndTime` (image: a mounted file
   named in `FASTI_CONFIG`, see the comment in `fasti-docker.yml`; the Tomcat bundle sets 00:01 in
   `bpm-platform.xml`). Without a window, cleanup only runs when started by hand.
3. Check that it runs: `GET /history/cleanup/configuration` shows the window and
   `GET /history/cleanup/jobs` the cleanup jobs; the history tables should stop growing once the
   first instances pass their time to live.

The image and the Tomcat bundle do not set a time to live for you: how long records must be kept
(or may be kept) depends on your obligations. See [tuning the runtime](../tuning-runtime.md) for
the removal-time strategy and its trade-offs.

## Erasing one person's data

There is no single "forget this person" switch. User ids are plain strings in many history
tables. To erase a person before the time to live: delete the user; delete or anonymize the
process instances that hold their data (`DELETE /history/process-instance/{id}` deletes an
instance's history); note that the audit trail, if it is on, keeps its records until they are purged by date
(`POST /audit/purge`), because it is built so that single records cannot be removed. Keep personal data out
of variables where you can, store a reference to your own system instead, and that system keeps
the data and its erasure in one place.

## Recommendations

- Store references, not documents, in variables; never store passwords or card numbers there.
- Set a history time to live on every definition and a cleanup window.
- Choose the history level by what you need: `activity` keeps no variables, `audit` keeps their
  last values without the change log, `full` keeps every change.
- Restrict `READ_HISTORY` to the people who need it: it also gives access to comments, attachments
  and the analytics of that process.

Not affiliated with Camunda Services GmbH; Camunda is a trademark of Camunda Services GmbH.
