# Runtime tuning

Tiếng Việt: [tuning-runtime.vi.md](tuning-runtime.vi.md)

Settings of the engine, the job executor, the connection pool and the Fasti additions that
decide how much one node gets done, with what they cost. Everything here is per node and safe
next to Camunda 7.24 nodes on the same database: no table of Camunda 7.24 is changed and jobs are
locked as before. Indexes and database settings are not covered here.

The numbers come from one shared 16-core workstation with the databases in local containers;
they show ratios, not what your hardware will reach. Measure your own workload before and after
a change.

## Job executor and connection pool

The job executor works in cycles: an acquisition selects up to `max-jobs-per-acquisition` due
jobs and locks them, puts them into a queue of `queue-capacity`, and up to `max-pool-size`
threads execute them. Every executing job holds one database connection, like every request
that reads or writes the engine.

| Setting | Up to 1.1 (Run, image) | After 1.1 (Run, image) | Engine / starter default |
|---|---|---|---|
| `camunda.bpm.job-execution.core-pool-size` | 3 | 10 (follows `max-pool-size`) | 3 |
| `camunda.bpm.job-execution.max-pool-size` | 10 | 10 | 10 |
| `camunda.bpm.job-execution.queue-capacity` | 3 | 10 | 3 |
| `camunda.bpm.job-execution.max-jobs-per-acquisition` | 3 | 10 | 3 |
| `camunda.bpm.job-execution.lock-time-in-millis` | 300000 (5 min) | 300000 | 300000 |
| `spring.datasource.hikari.maximum-pool-size` | 10 (HikariCP default) | 20 | 10 |
| `spring.datasource.hikari.minimum-idle` | 10 (= maximum) | 10 | = maximum |

The new values are set in `configuration/default.yml` and `production.yml` of Fasti BPM Run and in
the configuration of the container image; applications that embed the starter keep the defaults
of the last column and set what they need. **A Run installation that keeps its configuration
files from 1.1** keeps the former values: copy the `camunda.bpm.job-execution` block and the
`hikari` lines of `spring.datasource` from the new files into the kept ones.

Why these values:

- **Three jobs per acquisition** were the limit of the old defaults: each cycle is one select and
  one batch of lock updates, so the node could not start more than three jobs per round trip
  pair, however many threads were idle.
- **The queue holds one acquisition.** A smaller queue rejects part of every acquisition (the
  jobs stay locked until they are retried); a much larger queue lets one node lock jobs it cannot
  run soon, which other nodes then cannot take.
- **Core threads equal to the maximum.** The thread pool only grows beyond `core-pool-size` when
  the queue is full, so with a smaller core most threads were never started. The shipped files
  set `core-pool-size: ${camunda.bpm.job-execution.max-pool-size:10}`, so lowering only the
  maximum (for example `CAMUNDA_BPM_JOB_EXECUTION_MAX_POOL_SIZE=4` for a small database) lowers
  both. A `core-pool-size` larger than `max-pool-size` (a thread pool cannot have more core
  threads than its maximum) is reduced to the maximum by the starter, with a warning, instead of
  keeping the node from starting.
- **Pool = job threads + headroom.** With ten job threads and a pool of ten, ten long jobs
  could hold every connection while requests and the readiness check (`db`) waited. Size the
  pool as `max-pool-size` + the requests that use the database at the same time + 2 (acquisition
  and housekeeping); 20 leaves room for about eight concurrent requests. An idle node keeps
  `minimum-idle` connections open (10, as many as up to 1.1) and opens more, up to 20, only under
  load; HikariCP closes the extra ones after `idle-timeout` (10 minutes).

Async jobs per second of one Run node on PostgreSQL 16 (2,000 instances of a process with one
asynchronous service task, the jobs released at once; three runs of each configuration,
alternating, three measured rounds each; another workload loaded the machine meanwhile):

| Configuration | Median | Range |
|---|---|---|
| Up to 1.1 (3 per acquisition, queue 3, core 3, pool 10) | 267 jobs/s | 190 to 286 |
| After 1.1 (10, 10, 10, pool 20) | 285 jobs/s | 225 to 299 |

On that loaded machine the gain was small (+7 %, and steadier): the database was the limit, not
the acquisition. The new values matter most when jobs wait on something other than the database
(HTTP calls, scripts): with the old values mostly three threads ran, as the pool grows beyond its
core only when the queue is full.

Going further:

- More job threads only help while the database and the CPUs of the node have room. Raise
  `max-pool-size`, `core-pool-size`, `queue-capacity` and `max-jobs-per-acquisition` together, and
  `maximum-pool-size` by as many.
- **Connections:** the database must accept every connection all its clients may open at the
  same time:

  ```
  sum of maximum-pool-size of every node (Fasti and Camunda 7.24 nodes)
    + other clients (administration tools, backup, monitoring, migrations)
    + reserve of the database (PostgreSQL: superuser_reserved_connections, 3 by default)
    < connection limit of the database (PostgreSQL, MySQL/MariaDB: max_connections)
  ```

  Count the nodes at their highest: with autoscaling the maximum number of replicas, plus the
  extra pods of a rolling update (`maxSurge`). Example on PostgreSQL with `max_connections=100`:
  four Fasti nodes × 20 + one Camunda 7.24 node × 10 + 5 for tools + 3 reserved = 98, just
  inside. A pool of 20 instead of 10 doubles what one node may open under load (idle it keeps
  `minimum-idle`); check the limit before upgrading many replicas. The image takes
  `DB_CONN_MAXACTIVE` / `DB_CONN_MINIDLE` or `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` /
  `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE`.
- **Exclusive jobs and the lock time.** The jobs of one acquisition are locked for
  `camunda.bpm.job-execution.lock-time-in-millis` (5 minutes, as on Camunda 7.24 nodes). The
  exclusive jobs of one process instance among them (asynchronous continuations are exclusive by
  default) run one after the other in one thread, and the engine does not check the lock again
  before each of them. With 10 jobs per acquisition such a batch can be up to 10 jobs long
  instead of 3, so the budget per job shrinks from about 100 to about 30 seconds: when the lock
  runs out while the batch still runs, another node (Fasti or Camunda 7.24) acquires the rest
  and executes it a second time; the optimistic locking conflict rolls one execution back, but
  side effects outside the database (HTTP calls, mails) happen twice. The same budget covers the
  time a batch waits in the queue. Keep `max-jobs-per-acquisition` × the slowest exclusive job
  (plus the queue wait) below the lock time, or raise the lock time (for example 600000), or
  lower `max-jobs-per-acquisition`. Jobs that take tens of seconds are better external tasks.
  The shipped value stays at 5 minutes: a longer lock also makes the jobs that a node had locked
  but not run when it crashed or was killed wait that long before another node takes them.
- **Several nodes:** every node acquires from the same table. Larger acquisitions make it more
  likely that two nodes select the same jobs; the loser of the lock skips the job (an optimistic
  locking conflict, harmless). Keep `max-jobs-per-acquisition` at 16 or below and set
  `camunda.bpm.job-execution.backoff-time-in-millis` (for example 50, with `max-backoff` 5000) so
  that a node that lost jobs waits a moment before it acquires again.
- `spring.datasource.hikari.connection-timeout` (default 30 s) decides how long a request waits
  for a connection when the pool is empty. A lower value (5 to 10 s) makes the readiness probe
  report an exhausted pool sooner.
- Nodes that only serve the web UI or the REST API: `camunda.bpm.job-execution.enabled: false`
  (`FASTI_JOB_EXECUTOR_ENABLED=false`).

## Audit trail

With `fasti.audit.enabled` every command that produces history appends its records to one of
the chains of the node and locks the head row of that chain until its transaction ends
([audit-trail.md](audit-trail.md)). Transactions that map to the same chain wait for each other
while the head is locked.

**The lock is short after 1.1.** Up to 1.1 the records were written before the statements of the
engine, so the head was locked while the engine sent all its inserts, updates and deletes. After
1.1 a command whose transaction ends with it writes its records last: the head is locked for the
audit statements (lock, read, insert, update) and the commit only. That is every request and
every job of Fasti BPM Run and the container image, and every command of a standalone engine
(the Tomcat distribution).

**Transactions that span several commands** (Spring `@Transactional` around engine calls) are
treated differently. Such a transaction holds its chain from the end of its first command that
records something until the outer commit, and goes on writing engine rows meanwhile. If a command
that writes last shared that chain, it would hold its engine rows while it waited for the head,
and could wait for a transaction that next wants one of those rows: the two would wait for each
other until the database picked a victim or the lock timeout ended one of them. So a command
that joins a transaction writes its records first, as up to 1.1, on chains of its own
(`<node>/ext-<slot>`, created on first use), which commands that write last never wait for. The
starter tells the two apart by asking Spring whether a transaction is active before the engine
begins one. An engine that cannot tell (JTA, as in the WildFly distribution, or Spring without
the starter) writes every record first, on the usual chains, as up to 1.1.

Writing transactions per second of one engine with the audit trail on
(`AuditLockWindowBenchmark` in the engine tests: 16 threads, 4 chains; each thread starts an
instance with five variables and completes its user task). The code of 1.1 and the new code ran
at the same time, each against a database server of its own on the same container network, so
that the load of the machine hit both alike; medians of three rounds. "+1 ms" adds a wait of one
millisecond before every statement and commit, as a database on another host costs:

| Database | Records first (1.1) | Records last | Change |
|---|---|---|---|
| PostgreSQL 16 | 228 | 409 | +79 % |
| PostgreSQL 16, +1 ms | 93 | 240 | +158 % (two rounds) |
| MariaDB 11.4 | 94 | 167 | +79 % |
| MariaDB 11.4, +1 ms | 51 | 85 | +69 % |
| H2 (file, in the JVM) | 735 | 655 | −11 % |
| H2, +1 ms | 150 | 397 | +166 % |

The gain depends on the load of the machine: an earlier series of three rounds without the added
wait gave PostgreSQL 208 → 355 (+71 %) and MariaDB 81 → 116 (+43 %). Count on the lower end.

H2 runs inside the JVM and has no round trip: when more transactions send their engine statements
at the same time, it spends the time on its own internal locks instead. It is a development
database.

What still decides the throughput with the audit trail on:

- `fasti.audit.chains` (default 4, 1 to 64): the number of transactions of one node that can
  append at the same time. A thread always uses the same chain (its id modulo the number of
  chains), so raise it towards the number of threads that write at the same time (job threads
  plus busy request threads) when `pg_stat_activity` or the equivalent shows sessions waiting for
  `FASTI_AUDIT_HEAD`. More chains cost nothing while writing; verification reads each chain.
- **Long transactions.** An application transaction that spans several engine commands (Spring
  `@Transactional` around them) locks its `ext-` chain at the end of the first command that
  records something and holds it until the outer commit; other such transactions of the same slot
  wait meanwhile. As up to 1.1, two such transactions of one slot can still wait for each other
  when one of them wrote an engine row in an earlier command that recorded nothing (the other one
  then wants that row while it holds the chain); the database ends that by rolling one of them
  back. Keep such transactions short, or give the node more chains.
- The cost per command is four statements (five when a task is updated) in the transaction of
  the command.

**Pages of the audit trail.** A page is ordered by time, chain and position, all in one
direction (records of the same millisecond in different chains appear in reverse chain order
when the newest are listed first). The index `FASTI_IDX_AUDIT_ORDER (TIME_, CHAIN_ID_, SEQ_)`
gives that order, so the database reads one page instead of sorting every record of the
filter. New audit tables have it. **Existing audit tables** (created by 1.0 or 1.1) need the
upgrade script once: `fasti.<database>.upgrade.audit.after_1.1.sql` in the engine jar below
`dev/fasti/engine/audit/db/upgrade` (it creates the index and drops `FASTI_IDX_AUDIT_TIME`,
which the new index replaces). Without it everything works as before, only no faster. Creating
the index reads the table once; on a large table run it outside peak hours.

Time the database spends on one statement, 2 million audit records in 4 chains (500 actors),
before (indexes of 1.1, mixed order) and after the upgrade script (PostgreSQL `EXPLAIN ANALYZE`,
MariaDB `ANALYZE FORMAT=JSON` with its default 128 MB buffer pool; one run of each statement
after `ANALYZE` of the table, so take the values as orders of magnitude):

| Statement | PostgreSQL 16 before | after | MariaDB 11.4 before | after |
|---|---|---|---|---|
| First page, newest first (25 records) | 0.13 ms | 0.05 ms | 25.3 s (sorts all 2 million) | 9 ms |
| Page at offset 10,000 | 6.5 ms | 4.1 ms | – | – |
| First page filtered by type | 0.07 ms | 0.06 ms | – | – |
| First page filtered by one actor (0.2 % of the records) | 2.8 ms | 2.9 ms | 1.3 s | 3.2 s |
| Count of all records | 202 ms | 176 ms | 2.3 s | 2.9 s |
| Count with `maxCount=10000` | – | 1.8 ms | – | 17 ms |
| Creating the index (upgrade script) | | 5.9 s | | 26.7 s |

PostgreSQL 16 already sorted the old order incrementally along `FASTI_IDX_AUDIT_TIME`, so pages
were cheap there before; MariaDB and MySQL (and other databases without incremental sort) sorted
every record the filter selected for each page. **Trade-off:** a filter that matches a small share
of the records without an index of its own that gives the order (one actor of 500 above) may now
be read along `FASTI_IDX_AUDIT_ORDER` until a page is full, which on MariaDB read about three
times as many rows as the actor index plus a sort did (12,500 instead of 4,000); with a buffer
pool that holds the table both take milliseconds. Filters by process instance and business key
select few rows and keep using their indexes.

**Counting.** `GET /audit/event/count` counts every record the filter selects, which reads the
whole table when there is no filter. With `maxCount=<n>` (1 to 1,000,000) the database stops at
`n` records; a result equal to `n` means "`n` or more". A client that only needs to know whether
there is a next page can also ask `GET /audit/event` for one record more than it shows and skip
the count. In Java: `AuditEventQuery.count(long limit)`.

## Engine gauges

The gauges of the engine state (`fasti.engine.process.instances.active`, `tasks.open`,
`incidents.open`, `jobs.executable`, `jobs.failed`, `external.tasks.open`) are six count queries.
After 1.1 a scrape never runs them: it returns the values read last, and when they are older than
`fasti.metrics.engine.gauges.refresh-interval` (default 30 s) it starts one round of the queries
on a background thread, whose result the next scrape gets. A value is therefore up to one
refresh interval plus one scrape interval old; before the first round has finished the gauges
report `NaN`. A slow count (a large `ACT_RU_*` table without a matching index) no longer delays
or times out the scrape. The first round runs when the engine starts, also on a node that is
never scraped; after it, a node runs the counts only while it is scraped. When the application
stops, the gauges stop before the engine is closed and keep their last values.

- A round that has been running for more than three intervals (a count that waits for a lock, or
  a connection that died without an error) makes every gauge report `NaN` until it ends, instead
  of the last values: treat `NaN` in an alert like a failed scrape. Such a round holds a
  connection of the pool meanwhile; `camunda.bpm.generic-properties.properties.jdbc-statement-timeout`
  (seconds) ends it.
- On large databases raise the interval (`60s` to `5m`): every node runs the six counts once per
  interval while it is scraped.
- `fasti.metrics.engine.gauges.enabled: false` on all nodes but one avoids N identical rounds
  in a cluster (the values describe the database, not the node).

## History: removal time strategy

With the default strategy `end`, the engine writes the removal time of all history of a root
process instance when that instance ends: one `update ACT_HI_… set REMOVAL_TIME_ = ? where
ROOT_PROC_INST_ID_ = ?` per history table, 16 statements in the transaction that ends the
instance, most of which match no row (17 with `enableHistoricInstancePermissions`, which also
updates `ACT_RU_AUTHORIZATION`).

`historyRemovalTimeStrategy: start` writes the removal time with every history row when the row
is created (start time of the root instance + time to live) and sends none of these updates.

```yaml
camunda.bpm:
  generic-properties.properties:
    history-removal-time-strategy: start
```

| | `end` (default) | `start` |
|---|---|---|
| Statements when a root instance ends | +16 updates (one per history table; 17 with historic instance permissions) | none |
| Removal time | end of the root instance + time to live | start of the root instance + time to live |
| History of an instance that runs longer than its time to live | kept until the instance has ended and the time to live has passed | **removed by history cleanup while the instance is still running** |
| Time to live changed after the start | applies when the instance ends | does not apply to instances already started (use the batch "Set removal time") |

Choose `start` only when every instance ends well within its history time to live (short,
high-volume processes) or when losing the history of older running instances is acceptable. The
default is not changed. All nodes on one database, Camunda 7.24 nodes included, should use the
same strategy; otherwise the removal time of a row depends on the node that wrote it. Switching
affects new root instances; existing rows keep what they have.

## Scripts (JavaScript)

JavaScript runs on GraalJS. The engine creates a script engine and a context for every
evaluation and closes it afterwards; the settings `enableScriptEngineCaching` and
`enableScriptCompilation` do not apply to it (a GraalJS script engine is not thread-safe and is
never cached, and JavaScript source is not compiled to a `CompiledScript`). What is shared is
the code cache of the polyglot engine behind the script engines: a script that was evaluated
before is not parsed again while that engine lives. It is referenced weakly, so that process
application class loaders can be unloaded; after a garbage collection with no script running it
is created again and the next evaluations parse their source again. On a JVM without the Graal
compiler, scripts run in the interpreter.

For models that evaluate many scripts per second, prefer expressions (JUEL), Groovy (compiled
and cached per script) or Java delegates on the hot path, and keep JavaScript for the rest.

## Smaller changes after 1.1

- **Parameter binding.** MyBatis binds every statement parameter through a statement wrapper
  that hides the parameter metadata of the driver. The wrapper is now a plain class instead of a
  dynamic proxy, so binding a parameter is a direct call instead of a reflective one.

  `ParameterBindingBenchmark` (engine tests) binds the 24 parameters of an insert on H2: the
  plain wrapper took 13 to 18 % less time than the proxy in three runs, 0.2 to 0.4 µs per
  statement. Small per statement, but it applies to every parameter of every statement and every
  row of a JDBC batch. While MyBatis logs the statements of a mapper at `DEBUG` (for example
  `org.camunda.bpm.engine.impl.persistence.entity`), its logging wrapper looks like a stored
  procedure call, and those statements are bound through the proxy as before.

- **Commands that record nothing** (queries) no longer copy the bulk operations of the command
  to look for deployments when the audit trail is on.
- **Absences.** Without an index on `ACT_ID_INFO (USER_ID_, …)` (the Camunda 7.24 schema has
  none) every absence lookup reads the table. There the first user a command asks for is read
  alone and the second makes the command read all absences with one statement, which answers
  every later lookup: a chain of three substitutes sends two statements instead of four. With the
  optional `FASTI_IDX_ID_INFO_USER` ([performance](performance.md)) each lookup is an index seek
  and users are read one by one. The engine checks for the index at start.

## Other settings worth knowing

| Setting | Default | Note |
|---|---|---|
| `jdbcBatchProcessing` | `true` | keep on: the statements of a command go to the database in batches |
| History level | `full` in Run and the starter | `full` writes about twice as many history rows as `audit`; four-eyes rules and the user operation log need `full` |
| `historyCleanupBatchWindowStartTime` / `…EndTime` | unset | without a window history cleanup only runs when started by hand; set one, and a time to live on every definition |
| `camunda.bpm.generic-properties.properties.jdbc-statement-timeout` | unset | seconds after which a statement is cancelled, so that one pathological query (or a gauge round that hangs) cannot hold a connection |
| `fasti.absence.enabled` | on in Run, off in the image | adds one read of the user's absence per assignment to another user |
