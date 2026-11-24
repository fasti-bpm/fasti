English | [Tiếng Việt](benchmark-camunda-7.24.vi.md)

# Fasti BPM compared with Camunda 7.24.0

Measured on 23 and 24 November 2026. Fasti BPM 1.2.0, Fasti BPM 1.2.1 and Camunda Run 7.24.0 ran
on the same machine, against the same databases and with the same CPU and memory limits. This
page reports every difference found, in both directions. The method and the index scripts are
described in [Database performance](performance.md). 1.2.1 fixes the costs that the 1.2.0
measurements found; its numbers are in [Fasti 1.2.1](#fasti-121), the later sections are the
1.2.0 measurements.

## In short

| | Result |
|---|---|
| Engine work (jobs, queries, SQL plans) | **The same.** Identical SQL and plans on 1 million tasks; job throughput within run-to-run noise. |
| Workload board, 20 groups and 20 users, 1 million tasks | **1 request instead of 145, 1.53 s instead of 3.45 s, 17× fewer database pages read.** |
| Optional index scripts (shipped with Fasti) | **Idle job polling 162 ms → 0.1 ms per poll; overdue tasks sorted by due date 61 ms → 0.3 ms in the database.** They are plain indexes, so they help Camunda 7.24 just as much. |
| Startup (1.2.1) | **About the same**: 6.45 s vs 6.35 s to REST ready (1.2.0: 7.66 s). |
| Memory (1.2.1) | **Fasti 362 MB vs Camunda 393 MB** idle, as shipped (1.2.0: 518 MB). |
| Container image (1.2.1) | Fasti about 390 MB vs 338 MB uncompressed (1.2.0: 554 MB, with a duplicated Java runtime). |
| REST with authorization on, 2.2 million authorizations (1.2.1) | **Fasti faster than Camunda 7.24 with authorization on in every call measured**, for example the overdue task list 14 ms vs 138 ms. Camunda Run ships with authorization off, which is faster still where the check has work to do (a task board of 145 requests: 3.7 s off, 8.8 s Fasti 1.2.1, 20.8 s Camunda with authorization). |

## Fasti 1.2.1

Measured on 24 November 2026 in one round with Fasti 1.2.0 and Camunda Run 7.24.0, on the
environment described below. Fasti 1.2.1 is a build of the release commit; the startup and
memory runs used an existing schema, so the times are shorter than in the 1.2.0 section.

**Startup, memory and image.** Startup 5 runs, jobs 3 runs (50,000 jobs each), interleaved.

| | Ready (s) | `Started in` (s) | Idle memory (MB) | Jobs per second | Image (MB) |
|---|---|---|---|---|---|
| Camunda 7.24 | 6.35 | 4.52 | 393 | 732 (693–733) | 338 |
| Fasti 1.2.0 | 7.66 | 5.64 | 518 | – | 554 |
| Fasti 1.2.1 | 6.45 | 4.83 | **362** | 756 (710–814) | about 390 |

**REST with authorization**, on the large PostgreSQL database below (1,000,000 tasks, 2,200,000
authorizations). Camunda Run 7.24.0 also ran with authentication and authorization on, which
Camunda Run ships off. Median of 50 calls in milliseconds, as an administrator.

| Call | Camunda, as shipped (off) | Camunda, authorization on | Fasti 1.2.0 | Fasti 1.2.1 |
|---|---|---|---|---|
| Overdue tasks, first 50 | 12 | 138 | 156 | **14** |
| Tasks of one assignee by due date | 7 | 60 | 72 | **10** |
| Task count of a candidate group | 33 | 120 | 120 | **38** |
| Historic process instances, first 50 | 7 | 71 | 74 | **8** |
| Task count, all | 126 | 1,082 | 1,090 | 929 |
| Task board, 145 requests (s, median of 5) | 3.7 | 20.8 | 21.0 | **8.8** |
| `POST /task/workload` (s, median of 5) | – | – | 2.54 | 2.15 |

What changed:

- Every command read the group ids of all 2.2 million authorizations to drop the user's groups
  that have none; Camunda 7.24 does the same. 1.2.1 asks only for the user's own groups.
- The authorization subquery selects `TYPE_ IN (0, 1)` instead of `TYPE_ < 2`, so PostgreSQL
  seeks the unique indexes instead of reading the table: the overdue task list 77 → 1.6 ms in the
  database. The workload statements keep `TYPE_ < 2`, which was 250 times faster there for a
  user with many grants.
- Basic authentication over REST is remembered for 30 s with the user's groups and tenants:
  3 instead of 6 statements and 1 instead of 4 transactions per request. On this database that
  saves at most 2 ms per call; the two changes above make the difference.

A user with 20,400 grants (200 tasks, 3 groups) is slow in both products: the overdue list takes
about 3.2 s in Fasti 1.2.1 and 3.3 s in Camunda with authorization on, and the count of all tasks
and the task board do not finish within 60 s in either. That is the authorization join of the
engine (every task against every grant of the user) and remains to be improved. The process
instance count for this user went from 174 ms to 24 ms.

## What was compared

- **Fasti BPM 1.2.0**: the image `fastidev/fasti-bpm:1.2.0` (digest
  `sha256:1a6d43e5…25969f8`, Temurin 21.0.13.1).
- **Camunda 7.24.0**: Camunda Run 7.24.0. The official image `camunda/camunda-bpm-platform` can no
  longer be pulled from Docker Hub, so an equivalent image was built from the official
  distribution `camunda-bpm-run-7.24.0.tar.gz` (Camunda's download center, SHA-256
  `b4bc0583…04ed634`) and the final stage of the official Dockerfile, unchanged (branch `7.24`,
  Alpine, OpenJDK 17), with the JDBC drivers that Dockerfile selects. Started with
  `--webapps --rest`.

Both ran as shipped unless noted. To separate defaults from code, Fasti also ran with Camunda
Run's settings: 3 to 10 job executor threads, 3 jobs per acquisition, 10 database connections,
and REST without authentication (`FASTI_INSECURE_DEMO=true`). This configuration is called
"Fasti, same settings" below.

**Environment.** Intel Core i5-13400, 32 GB, Windows 11 with Docker Desktop (WSL2). Application
container `--cpus 4 --memory 2g`; database container `--cpus 4` with data on tmpfs. PostgreSQL
16.16 (`shared_buffers=1GB`), MariaDB 11.4.11. One database and one application container at a
time. Medians with min–max ranges.

## Startup and memory

Time from `docker run` to the first HTTP 200 of `GET /engine-rest/engine`, on an empty database,
3 runs each. Memory is the resident size of the Java process 2 minutes after start.

| | Ready (s) | Idle memory (MB) |
|---|---|---|
| Camunda 7.24, as shipped (heap 25 % of the limit) | 7.29 (7.17–7.76) | 353 (349–360) |
| Camunda 7.24, heap 75 % | 6.83 (6.82–6.91) | 442 (439–442) |
| Fasti 1.2.0, as shipped (heap 75 % of the limit) | 8.30 (8.24–9.10) | 512 (446–560) |
| Fasti 1.2.0, heap 25 % | 8.39 (8.25–8.61) | 447 (424–450) |

Fasti starts about 1 s later and, with the same heap setting, uses about 90 MB more memory. It
ships more (the new web interface, metrics, MCP and single sign-on modules) on a newer stack
(Java 21, Spring Boot 4); which part accounts for the difference was not measured. Idle CPU was
below 1.5 % for both.

## Job throughput

A process with 10 asynchronous service tasks, 5,000 instances started with the job executor off,
then the executor started to drain the 50,000 jobs. History level full. 3 runs per
configuration, interleaved.

| | Jobs per second |
|---|---|
| Camunda 7.24 | 891 (851–1,049) |
| Fasti 1.2.0, as shipped | 951 (830–1,077) |
| Fasti 1.2.0, same settings | 831 (750–944) |

The ranges overlap: there is no difference the noise allows to name. Both products kept the 4
application CPUs and the 4 database CPUs busy, so this is engine work, which is the same code.
Fasti's larger job executor defaults do not help on 4 CPUs with jobs that do nothing.

## Large database (PostgreSQL 16)

1,000,000 running tasks (600,000 assigned to 5,000 users, about 25,000 overdue), 1,600,000
candidate links in 200 groups, 1,000,000 jobs, 2,200,000 authorizations, 1,000,000 historic
instances; 6.6 GB. The schema was created by Camunda 7.24 and every configuration used the same
database. Statement times are the database's own (`auto_explain` with `ANALYZE, BUFFERS`).

### SQL plans

With the same settings, Fasti and Camunda 7.24 send the same statements and get the same plans
and the same number of pages read, for every statement measured.

### The optional index scripts

| Statement | Without the indexes | With the indexes |
|---|---|---|
| Job acquisition when nothing is due (every node, every poll) | 162 ms, 47,631 pages (full scan of `ACT_RU_JOB`) | **0.1 ms, 6 pages** |
| Overdue tasks sorted by due date, first 50 | 61 ms, 31,394 pages | **0.3 ms, 68 pages** |
| Over REST, `GET /task?dueBefore=now&sortBy=dueDate` | 56 ms | **8 ms** |
| Tasks of one assignee by due date | 203 pages | 68 pages |

Without a client, both products poll for jobs once a minute; without the index each poll reads
the whole job table (about 370 MB of pages). The indexes are plain SQL and are not tied to Fasti:
they give Camunda 7.24 the same gain. What Fasti adds is the scripts for six databases, the check
at startup that tells you when they are missing, and the measurements behind each one. None of
them speeds up the task count of a candidate group (23–33 ms for 5,000 tasks in both products).

### Workload board

The numbers of a team workload view: open, unclaimed and overdue tasks and the oldest task, in
total, for 20 groups and 20 users. Camunda 7.24 has no endpoint for this; a client asks per row,
which is 145 requests. Fasti answers with one `POST /task/workload`. Both give the same counts in
all 40 rows.

| | Requests | Time, median of 5 | Database pages read |
|---|---:|---|---:|
| Camunda 7.24, per-row requests | 145 | 3.45 s (3.32–3.96) | 2,795,826 |
| Fasti 1.2.0, same settings, `POST /task/workload` | 1 | **1.53 s** (1.49–1.58) | 167,904 |
| Fasti 1.2.0 as shipped (authorization on), `POST /task/workload` | 1 | 2.50 s (2.45–2.72) | 283,961 |
| Fasti 1.2.0 as shipped, per-row requests | 145 | 20.6 s (20.5–21.0) | 13,708,069 |

The endpoint is not a light request: it counts all 1,000,000 open tasks for the totals. It is
still 2.3× faster than the per-row requests on Camunda 7.24 and reads 17× fewer pages.

### REST latency

One client, 10 warm-up calls, then 50 measured. Median / 95th percentile in milliseconds.

| Call | Camunda 7.24 | Fasti, same settings | Fasti, as shipped |
|---|---|---|---|
| `GET /task?dueBefore=now&maxResults=50` | 11.6 / 13.8 | 12.8 / 17.1 | 153.7 / 177.0 |
| `GET /task?assignee=…&sortBy=dueDate&maxResults=50` | 7.3 / 9.3 | 8.3 / 17.3 | 74.0 / 91.0 |
| `GET /task/count?candidateGroup=…` | 33.7 / 48.4 | 32.2 / 40.8 | 122.8 / 148.2 |

With the same settings, the two are equal. As shipped, Fasti is slower on this database because
of its secure default: REST requires a login and authorization checks are on, while Camunda Run
ships with both off. Every request then reads the user, its groups and tenants, and every task
query joins the authorization table, here 2.2 million rows. Camunda 7.24 with authorization
switched on runs the same checks; that case was not measured. The index scripts do not reduce
this cost.

## MariaDB 11.4

Fasti only, on half the data (500,000 tasks and jobs). These check the choices behind the index
scripts.

| | Without the index | With the index |
|---|---|---|
| Job acquisition, nothing due (500,000 jobs) | 154 ms, 500,000 rows examined | **0.12 ms, 0 rows, 24 pages** |
| Same, with 50,000 failed and 10,000 suspended jobs | 164 ms | **2.3 ms** (a plain `DUEDATE_` index: 117 ms) |
| Absence lookup, chain of three substitutes | 2 full scans of `ACT_ID_INFO`, 200,024 rows, 40.5 ms | 4 seeks, 12 rows, 0.53 ms |
| Same, the whole REST call | 86 ms | 51 ms |

The performance advisor reported the missing indexes on a new MariaDB schema and no longer did
after the script ran. The script can be run twice without error.

## The web interface

The classic Tasklist of Camunda 7.24 and the Fasti web interface are different products, so they
were not timed against each other. The classic Tasklist sends 9 API requests when it opens and
none while it is open. The Fasti task list sends 14 when it opens and about 4.5 per minute,
because it refreshes itself. See [Web interface performance](performance-web-ui.md).

## Known issues and next steps

- Fixed in 1.2.1: the duplicated Java runtime in the image, the read of all authorization group
  ids per command, the slower startup and the higher idle memory.
- Users with tens of thousands of grants on a database of a million tasks: task counts without a
  filter do not finish in reasonable time, in Fasti and in Camunda 7.24 alike. Reducing this needs
  a different authorization join.
- Starting process instances over REST is slower with authorization on (5,000 starts in 18.6 s
  vs 12.1 s with Camunda Run's open REST); every start is checked.

## Not measured

MySQL, Oracle, SQL Server and DB2 with authorization on, more than one node, garbage collection
pauses under bursty load with the memory settings of 1.2.1, and machines other than the one
above. Numbers on other hardware will differ; the ratios between the
configurations are what this page is about.
