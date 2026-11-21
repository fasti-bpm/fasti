English | [Tiếng Việt](audit-trail.vi.md)

# Immutable audit trail

> **New in 1.1.0.** The 1.0.x releases and images do not have this feature; its settings have no
> effect there.

Auditors want to know who did what, when, to which case, and that nobody changed that record
afterwards. The history and the operation log of the engine answer the first question, but any
user with database access can change them without a trace, and history cleanup removes them.
Fasti BPM can keep an **audit trail** next to them: every record is chained to the one before
it by a SHA-256 hash, so a record that is changed, removed or inserted is found when the trail
is verified. The application never updates or deletes records, except in an explicit purge
that leaves a record of itself.

## What is recorded

Next to the normal history (which stays as it is), the plugin turns the events the engine
produces into records, in the transaction of what they record: if the command rolls back, so
do its records, and if a record cannot be written, the command fails.

| Category (`type`) | Actions | Facts in `details` |
|---|---|---|
| `process` | `start`, `update` (suspend, activate), `end` (also when deleted), `migrate` | state, start user, start and end activity, delete reason, super instance |
| `task` | `create`, `assign` (with the previous assignee), `update`, `complete`, `delete` (ended with its instance), `identity-link-add` / `identity-link-delete` (candidate users and groups, owner) | assignee, owner, task definition key, delete reason |
| `variable` | `create`, `update`, `delete`, `migrate` | type, task, execution, revision, and the value as set by `variable-values` (below) |
| `deployment` | `create` (with the SHA-256 of every resource), `delete` | source, resource hashes |
| `operation` | every entry of the user operation log: `Claim`, `Complete`, `SetVariable`, `Create` / `Update` / `Delete` of users, groups, memberships, tenants and authorizations, suspensions, job retries, … | operation id, category, property, old and new value, annotation |
| `incident` | `create`, `resolve`, `delete`, `update`, `migrate` | message, activity, configuration, state |
| `decision` | `evaluate` | decision definition, activity |
| `activity` (off by default) | `start`, `end` of every activity | activity type and name |
| `audit` | `checkpoint`, `verify`, `export`, `purge` | what the audit trail did itself, and who asked |

Every record has the **actor** (the authenticated user, or empty for the engine itself: job
executor, timers, startup), the **time** of the engine clock (milliseconds since 1970 UTC), the
**engine**, the **tenant**, the process definition, the process instance and its **business
key**.

The plugin records what the engine produces, and that depends on the **history level**:

| History level | Recorded |
|---|---|
| `full` (the default of Run and the container image) | everything above |
| `audit` | `process`, `task` (`create`, `assign`, `update`, `complete`, `delete`), `variable`, `deployment`, `audit` and, if turned on, `activity`. Not recorded: `operation` (who claimed, identity and authorization changes), `incident`, `decision`, and the task actions `identity-link-add` / `identity-link-delete` (candidate users and groups, owner) |
| `activity` | as `audit`, and no `variable` records either |
| `none` | only `deployment` and the records of the audit trail itself (`audit`) |

The plugin logs a warning below `full`. The user operation log needs an authenticated user, as
in Camunda 7.

**Variable values** (`fasti.audit.variable-values`):

- `hash` (default): the SHA-256 of the stored value; with a signing key the HMAC-SHA256 of it,
  so that short values (yes/no, amounts) cannot be guessed from the hash. The value itself is
  not stored, but a later export of the history can be checked against it.
- `value`: text and numbers in clear (at most 4000 characters, the rest only as hash); binary
  and serialized values only as hash and size.
- `none`: only that a variable changed.

Variables whose names match `fasti.audit.masked-variables` (default
`*password*,*secret*,*token*,*credential*,*apikey*,*api_key*,*privatekey*,*private_key*`,
case-insensitive, `*` and `?` as wildcards) are recorded with `"masked": true` and no value or
hash. An update records the new value; the old one is the value of the previous record of the
same variable instance (`entityId`).

## How tampering is found

Each record stores `HASH_ = SHA-256(PREV_HASH_ + "\n" + canonical form of the record)`: the
previous hash (64 hexadecimal characters; 64 zeros for the first record of a chain), a line feed
and the canonical form, as UTF-8 bytes. The canonical form names every field on its own line
(escaped, `null` told apart from text) and starts with the version `fasti-audit-v1`. Records form **chains**: positions 1, 2, 3, … without gaps, each
naming the hash of the one before. A table `FASTI_AUDIT_HEAD` holds the position and hash of the
last record of each chain.

**Concurrency without a global lock.** Each node writes to `fasti.audit.chains` chains of its
own (default 4), named `<node id>/<slot>`; a transaction takes the slot of its thread. Appending
locks only the head row of that chain (an `UPDATE`, which every database supports the same way),
at the end of the command, until the commit. So commands on other slots and other nodes never
wait for each other, and commands of one slot are serialized only for the moment of their
commit. A nested transaction ("requires new") that starts while outer ones of the same thread
hold chains takes its slot in the next band: with `chains` = 4 the outermost transactions use
the slots 0 to 3, transactions nested in them 4 to 7, and so on (chains `<node id>/4` … appear on
first use). A transaction therefore only waits for transactions of its own nesting depth, never
for a suspended outer one, so nested transactions of several threads cannot block each other in
a way the database cannot see. A transaction that spans several commands holds its chain from
its first record to its commit, so such transactions of one slot run one after the other. Such
a transaction (a command that joins an outer Spring transaction) writes its records before the
statements of the engine and uses chains of its own, `<node id>/ext-<slot>` (created on first
use), while a command whose transaction ends with it writes its records last, on
`<node id>/<slot>` ([runtime tuning](tuning-runtime.md#audit-trail) explains why). The
wait for a chain is limited: after `fasti.audit.lock-timeout-seconds` (default 60) the command
fails with nothing written, instead of hanging. The trade-off: there is no
single global order of all records (each chain is ordered; across chains the time orders them),
and verifying reads every chain.

The **node id** (`fasti.audit.node-id`) defaults to `<engine name>@<host name>`. Give each node
a stable, unique id; two nodes with the same id share chains, which stays correct (the head lock
serializes them) but makes them wait for each other.

**Verifying** (`POST /audit/verify`, or the button in the web UI) recomputes every chain, page
by page, and reports per chain whether it is intact and where it first breaks:

| Reason | Meaning |
|---|---|
| `hash-mismatch` | the record was changed after it was written |
| `previous-hash-mismatch` | the record does not follow the one before it: one of them was changed, or a record was removed or inserted between them |
| `sequence-gap` | positions are missing: records were removed |
| `missing-start` | the chain does not start at 1 and no purge record names the records before it |
| `purge-unattested` | a key is set, the chain does not start at 1, and the purge record that names the records before it carries no valid signature of a key of the engine |
| `head-mismatch` | the head names another last record: records at the end were removed, or the head was changed |
| `head-missing` | records exist for a chain without head |
| `checkpoint-mismatch` | the HMAC of a signed checkpoint does not match: the chain before it was rewritten |
| `checkpoint-unsigned` | a key is set and a checkpoint carries no signature |
| `checkpoint-unknown-key` | a key is set and a checkpoint names a key that is neither the current nor a retired one |
| `checkpoint-missing` | a key is set and no checkpoint is where the interval puts one: checkpoints were removed |

The verification itself becomes a record (who verified, the result). An engine runs one
verification at a time, as it reads and hashes every record; a second request is refused
(`409` over REST, `AuditVerificationRunningException` in Java) until the first is done. The
guard is per engine, not per cluster.

**Two verdicts.** A verification answers two questions, per chain and for the whole trail:

- **`intact`**: nothing that can be checked with the settings of the engine failed. The hashes
  are consistent from the first record of the chain to its head. With a key, in addition, every
  checkpoint and every purge record that explains a removed start carries a valid signature (or
  one that the settings list as retired, below), and no checkpoint is missing.
- **`authenticated`**: intact, a key is set, and every checkpoint and the purge record of a
  removed start were *checked* with a key the engine holds. Only this rules out that someone
  with write access to the database rewrote a chain up to its last checkpoint or removed its
  start. It is `false` without a key, for chains with signatures that are accepted unchecked
  (retired key ids), and for a chain whose removed start is named by a purge record without
  checked signature (`purgeAttested: false`). Per chain, `authenticatedThrough` is the last
  record a checked signature covers (the record before the last checkpoint; `0` before the first
  checkpoint of a chain): the records after it are protected by the hashes and the head only.

So without a key, "intact" means "the hashes are consistent"; the web UI says so, and says when
an intact trail is not authenticated.

**Signed checkpoints.** Someone with write access to the database could rewrite a whole chain
from some record on, with correct hashes, and move the head. To make that detectable, set a key
of at least 32 bytes (`fasti.audit.hmac-key`, best as the environment variable
`FASTI_AUDIT_HMAC_KEY`, or a file in `fasti.audit.hmac-key-file`). After every
`checkpoint-interval` records (default 1000) the chain gets a `checkpoint` record with the
HMAC-SHA256 of the chain, the position and hash before it, and the interval it was written
with. The key never reaches the database; the `keyId` (the start of the SHA-256 of the key)
tells keys apart.

With a key set, a signature that is missing is as much a break as one that is wrong, because
someone who rewrites a chain cannot forge signatures but can remove them:

- every checkpoint must carry a valid signature of the current key or of a retired key;
- checkpoints must follow each other, from the first record of the chain, at the distance of
  the interval they were written with, and no more records than that may follow the last one:
  removed checkpoints are missed, also after the engine has written new ones over a rewritten
  chain;
- a chain that does not start at record 1 needs a purge record signed with such a key.

**Changing the key, or setting one later.** Give the engine the earlier keys in
`fasti.audit.retired-hmac-keys` (comma separated, e.g. `FASTI_AUDIT_RETIRED_HMAC_KEYS`): what
they signed is still checked, and the trail stays authenticated. If an earlier key is lost, or
checkpoints and purge records were written before any key was set, list their key ids (12 hex
characters, as `GET /audit` and the checkpoints show them) or the word `unsigned` in
`fasti.audit.retired-key-ids`. Those signatures are then accepted without being checked, but
only before the first checkpoint of the current key in a chain, and such a chain verifies as
intact, not as authenticated ("3 of 5 verified" in the web UI, `checkpointsVerified` over
REST). Remove an entry once a purge has removed the records it covers. Without either setting,
verification reports `checkpoint-unknown-key`, `checkpoint-unsigned` or `purge-unattested`.
Changing `checkpoint-interval` from one value to another is fine (each checkpoint records the
interval it was written with). But a stretch of a signed chain that is longer than the interval
and has no checkpoint (checkpoints were off, `0`, for a while; the interval was lowered before
the first checkpoint of a chain; another node uses a larger interval) is reported as
`checkpoint-missing`, because that is also what removed checkpoints look like. At the start of a
chain, list `unsigned` to accept it (the chain is then not authenticated); in the middle of a
chain it stays until a purge removes that part. So set the key and the interval before the first
records, the same on all nodes of a cluster.

Each checkpoint is also written
to the application log (`Audit trail checkpoint …`), which, shipped to a log store, is an anchor
outside the database. The records written after the last checkpoint are protected by the
chain only: keep the last hashes that a verification reports (or the log lines) outside the
database to detect a later truncation or rewrite of them.

## Immutability, retention and purging

- The application only inserts records. **History cleanup does not touch them**: they are not
  in the tables of the history, whatever the history time to live.
- Records are kept until they are purged explicitly. `POST /audit/purge`
  `{"before": "…", "exported": true}` (Java: `AuditService#purge`) removes, per chain, the
  records from its start up to the last one before `before`, and appends a `purge` record that
  names the position and hash of the last removed record, so the rest of the chain still
  verifies. With a key the purge record is signed (HMAC over the chain, the removed position and
  hash, and the id and position of the purge record), and verification accepts a chain without
  its start only with such a signature: a purge record that someone writes into the database to
  hide removed records is found. Without a key a purge record proves nothing; the chain is
  reported with `purgeAttested: false` and never as authenticated. The purge
  runs in a transaction of its own (not inside one that has written audit records). Records
  younger than `fasti.audit.retention-days` (default 365) are never purged. Export first: the
  JSON export carries every hash, so the removed part can still be verified offline against the
  purge record. Purging needs an administrators group or `DELETE` on `SYSTEM`.
- `databaseSchemaUpdate=create-drop` does not drop the audit tables.

**Harden the database.** Run the application with a database user that may insert into and
read `FASTI_AUDIT_EVENT`, but not update it, and delete only while you purge:

```sql
-- PostgreSQL (similar on Oracle, SQL Server, DB2)
REVOKE UPDATE, DELETE, TRUNCATE ON fasti_audit_event FROM fasti_app;
GRANT SELECT, INSERT ON fasti_audit_event TO fasti_app;
GRANT SELECT, INSERT, UPDATE ON fasti_audit_head TO fasti_app;
-- MySQL / MariaDB
REVOKE UPDATE, DELETE ON fasti.FASTI_AUDIT_EVENT FROM 'fasti_app'@'%';
```

For stronger guarantees, export the trail regularly to write-once storage (S3 Object Lock,
Azure immutable blobs, WORM tape) and keep the reported last hashes there, or replicate the
table to a database the application cannot write to.

## In the web UI

- **Cockpit → Audit trail**: the records, newest first, with filters for process instance,
  business key, user (or `system` for the engine), category, action and time. **Show** opens
  the details of a record and its place in its chain (position, hash, previous hash).
  **Export CSV** / **Export JSON** download the selection from the server (oldest first, at most
  100,000 records; the export is recorded); the CSV puts an apostrophe before text a spreadsheet
  would run as a formula, the JSON keeps every value exactly as hashed. **Verify integrity**
  shows "Intact" with the last hash of every chain, or an alert that names the chain, the
  record and the reason of the first break. Below "Intact" it says whether the trail is also
  authenticated, and if not, why (no key, or signatures that could not be checked); each chain
  shows up to which record its signatures reach, and a purged start without checked signature
  is marked on its chain.
- **History of an instance → Audit trail**: the records of that instance, oldest first (shown
  when the audit trail is on).

Without the plugin the page says that the audit trail is off and how to turn it on.

## REST API

All below `/engine-rest` (also in the OpenAPI document, tag "Audit Trail"):

| Method and path | What it does |
|---|---|
| `GET /audit` | `enabled`, for every caller and also while the audit trail is off (`200` with `enabled: false`); for callers who may read the trail also the settings (`nodeId`, `chains`, `types`, `variableValues`, `maskedVariables`, `signed`, `keyId`, `retiredKeyIds`, `checkpointInterval`, `retentionDays`, `historyLevel`); never the key |
| `GET /audit/event` | The records, newest first; filters `processInstanceId`, `businessKey`, `actor` (`system` for the engine), `type` (comma separated), `action`, `tenantId`, `processDefinitionKey`, `entityId`, `chainId`, `from`, `to`, `sortOrder` (`desc`, `asc`), paging `firstResult` (at most 1,000,000), `maxResults` (at most 1000); the page is cut by the database |
| `GET /audit/event/count` | The number of records the filters select. With `maxCount` (1 to 1,000,000) the database stops counting there, so the count of a large result stays cheap; a result equal to `maxCount` means "that many or more" |
| `POST /audit/event/export?format=csv\|json` | The selection (same filters, as query parameters) as a file, streamed from one database cursor; recorded as `audit`/`export`. A `POST` because it writes that record: a `GET` answers `405` and records nothing |
| `POST /audit/verify?chainId=` | Verifies every chain (or one): `intact`, `authenticated`, `records`, `chains` (with `authenticated`, `authenticatedThrough`, `lastHash`, `checkpointsVerified`, `purgedThrough`, `purgeAttested`, `firstBreak`) and `firstBreak`; `409` while another verification runs |
| `POST /audit/purge` | `{"before": "...", "exported": true}`: purges old records, see above |

**Authorization**: reading, exporting and verifying need membership of an administrators group
(`camunda-admin`) or the permission `READ` on the resource `SYSTEM`; purging needs an
administrators group or `DELETE` on `SYSTEM`. The resource `SYSTEM` exists in Camunda 7.24, so
no new resource type is needed. Without authorization checks, as everywhere in the engine,
anyone may. The same holds for a request without an authenticated user, even while
authorization is on: that is the case when REST authentication is off, as in the default
configuration of Fasti BPM Run (the container image has it on). All endpoints except `GET /audit`
answer `400` when the audit trail is off.

**Tenants**: a reader who is not an administrator sees, counts and exports only the records of
the tenants of the authentication and the records without tenant (deployments and operations
without tenant, checkpoints, verifications, exports, purges), as the queries of the engine do
while its tenant check is on. Verifying always covers whole chains; it returns positions and
hashes, not records.

**Errors**: when the database fails, the response names what could not be done ("The audit trail
could not read records"), never the message of the database; that is in the server log. Queries
and exports stop after `fasti.audit.query-timeout-seconds` (default 120).

Java: `AuditTrail.of(processEngine)` returns the `AuditService` (`createQuery()`, `verify()`,
`recordExport(…)`, `purge(before)`); `AuditEventQuery#listPage` pages in the database and
`#forEach` reads a large result from one cursor.

## Turning it on

| Distribution | Setting |
|---|---|
| Fasti BPM Run | Off; uncomment `fasti.audit` in `default.yml` / `production.yml` |
| Spring Boot starter | `fasti.audit.enabled=true` (off by default) |
| Container image | `FASTI_AUDIT_ENABLED=true` (off by default); key in `FASTI_AUDIT_HMAC_KEY` or `FASTI_AUDIT_HMAC_KEY_FILE` |
| Tomcat, WildFly | Uncomment the plugin `dev.fasti.engine.audit.AuditTrailPlugin` in `bpm-platform.xml` / `standalone.xml` |

Settings (`fasti.audit.*`; plugin properties in camel case for Tomcat and WildFly):

| Setting | Default | Meaning |
|---|---|---|
| `node-id` | `<engine>@<host>` | this node in the chain ids |
| `chains` | `4` | chains per node, written in parallel (1 to 64) |
| `types` | everything but `activity` | categories, comma separated, or `all` |
| `variable-values` | `hash` | `hash`, `value` or `none` |
| `masked-variables` | see above | name patterns never recorded |
| `hmac-key` / `hmac-key-file` | none | key that signs checkpoints and purge records (at least 32 bytes) |
| `retired-hmac-keys` | none | earlier keys, comma separated: what they signed is still checked |
| `retired-key-ids` | none | key ids of lost earlier keys, and `unsigned`: accepted unchecked, the chain is then not authenticated |
| `checkpoint-interval` | `1000` | records between two checkpoints; `0` for none |
| `retention-days` | `365` | records younger than this cannot be purged |
| `lock-timeout-seconds` | `60` | how long a command waits for its chain before it fails; `0` for the limit of the database |
| `query-timeout-seconds` | `120` | how long a query or export may run; `0` for no limit |

**Why off by default everywhere**: it adds two tables and one write per command that produces
history, it should run on every node of a cluster (a node without it records nothing), it needs
a key to be managed for the strongest guarantee, and an existing database may be shared with
Camunda 7.24 nodes. An installation turns it on deliberately.

**Tables.** With `databaseSchemaUpdate` `true` or `create` (Run, the starter by default) the
plugin creates its tables at startup. Otherwise (the container image keeps an existing database
unchanged) create them first with the script `<database>_fasti_audit.sql` of the distribution:
in `configuration/sql/create/` of Fasti BPM Run (`/fasti/configuration/sql/create/` in the
container image), in `sql/create/` of the Tomcat and WildFly distributions (also in the engine
jar below `dev/fasti/engine/audit/db/create`). The script of the same name in `sql/drop/` next
to it removes the tables with all records. If the tables are missing and may not be created, the engine refuses to start
with a message that names the script. Scripts exist for H2, PostgreSQL, MySQL/MariaDB (use
`utf8mb4`), Oracle, SQL Server and DB2.

**Cost.** Per command that produces history: one update and one select of the head row, one
batched insert of its records and one update of the head, at commit; for a task update one
indexed read of the historic task (to tell an assignment); computing the hashes is negligible.
When the transaction ends with the command (every request and job of Run and the container
image, every command of a standalone engine), the records are written after everything the
engine writes for the command, so the lock on the head row is held for these statements and the
commit only; with the default of four chains per node, threads rarely wait
([runtime tuning](tuning-runtime.md#audit-trail)).

**Upgrading existing tables.** Audit tables created by 1.0 or 1.1 lack the index
`FASTI_IDX_AUDIT_ORDER (TIME_, CHAIN_ID_, SEQ_)`, which gives the order of a page. Run
`fasti.<database>.upgrade.audit.after_1.1.sql` (in the engine jar below
`dev/fasti/engine/audit/db/upgrade`) once; it creates the index and drops `FASTI_IDX_AUDIT_TIME`,
which the new index replaces. Until then pages are read as before, only no faster.
Records of the same millisecond in different chains are listed in the order of their chain ids,
reversed when the newest come first.

## Storage and compatibility

- **The tables of Camunda 7.24 stay unchanged.** The audit trail has its own optional tables,
  `FASTI_AUDIT_EVENT` (the records; `TIME_` in milliseconds, so the hashes do not depend on time
  zones or timestamp precision) and `FASTI_AUDIT_HEAD` (one row per chain).
- **Camunda 7.24 nodes and Fasti nodes without the plugin** on the same database ignore the
  tables; what they do is not recorded. Turn the plugin on for every node at once.
- **Rollback to Camunda 7.24**: leave the tables in place or drop them with the drop script;
  nothing else was written.
- If the tables become unwritable (dropped, a record inserted from outside at the next
  position), commands that produce history fail rather than run unrecorded.

## Limits

- The trail proves that records were not changed after they were written. It cannot prove that
  something was recorded at all: a node without the plugin, a lower history level or a direct
  change of the engine tables in the database bypasses it.
- Without a signing key, someone with write access to the database can rewrite a chain from
  some record on together with its head, or remove its start and write a purge record for it;
  verification then still says intact, but never authenticated. With a key, only the records
  after the last checkpoint can be rewritten. Keep reported last hashes or the checkpoint log
  lines outside the database.
- Deleting every record and the head of a chain is only noticed against such an outside
  anchor.
- A purge removes all records of a chain before a time in one transaction, one chain after the
  other (each chain in a transaction of its own, so that a purge holds one head at a time; if it
  fails half-way, the chains done so far stay purged, each with its purge record); very large purges
  are best done in steps (several `before` dates).
- Oracle, SQL Server and DB2 scripts are written in the style of the engine scripts but are
  not tested here; H2, PostgreSQL 16 and MariaDB 11.4 are.
