English | [Tiếng Việt](performance-web-ui.vi.md)

# Request volume of the web UI

> **New after 1.1.0.** These numbers compare the web UI of 1.1.0 with the next release. A web UI
> of the next release that talks to a 1.1.0 server works too: the workload board then falls
> back to its per-row task queries (see below).

The pages of the web UI refresh themselves while they are open. With many groups and users, or
with many people at the screen, those refreshes are a large part of what the engine has to
answer. This page lists what each page sends and how that changed.

## How it was measured

`webapps/frontend-next/scripts/request-trace.mjs` opens each page in the Chromium of Playwright
against a running Fasti BPM Run (`--webapps --rest --example`, H2), logged in as `demo`:

```sh
node scripts/request-trace.mjs --base http://localhost:8080/fasti/ --seed 46 --window 120
```

- `--seed 46` creates 46 groups, 46 users and 20 tasks (a third of them overdue), so that the
  workload board shows full pages; they are removed afterwards.
- Each page is watched for 120 s after it has loaded, with the pointer moving every 10 s like a
  user at the screen, then for 90 s in a hidden tab.
- Only requests to the REST API and the web application API count as "API requests"; scripts,
  styles and fonts are listed under "all requests" of the first load only.

Both runs used the same script, data and machine: 1.1.0 (commit `7df3ab864e`) and this branch.

## Results

| Page | API requests at load | API requests per minute | API kB per minute |
|---|---:|---:|---:|
| Cockpit → Workload | 344 → **11** | 509.5 → **2** | 484.3 → **15.9** |
| Cockpit → Dashboard | 46 → **40** | 52 → **18** | 48.1 → **18.4** |
| Tasklist | 13 → 14 | 8 → **4.5** | 52.2 → **46.5** |
| Tasklist: claim / unclaim | 10 / 7 → **8 / 4** | | |
| Process instance | 13 → 13 | 8 → 8 | 15.5 → 15.5 |

A hidden tab sent no requests before and after: the pages already paused while hidden. The one
exception is new: with "Notify me about new tasks" on and browser notifications allowed, the
task list keeps reading its first page while hidden (see Tasklist).

### Workload board

Before, the board asked three or four task queries per group and per person (`/task/count`
and `/task?maxResults=1`), about 340 requests per refresh with full pages, every 30 s and on
every return to the tab. The engine now has `GET`/`POST /task/workload` (see the
[REST guide](api/README.md#workload-per-group-and-per-person-fasti)), which counts open, unclaimed and overdue tasks and the oldest one per
candidate group and per assignee with a few grouped statements. The statements wrap the task
query of the engine, so its authorization check (revoked permissions included), tenant check and
CMMN handling apply unchanged: the counts never include a task that `GET /task` would not
return to the same user. At most 200 groups and 200 users per request.

The board sends one `POST /task/workload` per refresh; the pages of groups and users are read
every five minutes. A server without the endpoint (404 or 405), or one whose MyBatis mappings
lack its statements (501, see the REST guide), is asked per row as before: four groups and four
people at a time, each row with three or four requests. Whether the endpoint exists is kept for
five minutes only, so a page left open during a rolling update or a rollback falls back when
the endpoint goes away and uses it again once it is back.

### Dashboard

- The open tasks, their assignment and the overdue tasks come from the same totals request
  instead of five task counts.
- The two `statistics-count` requests, whose results were not used, are gone.
- Batches with failures are counted by `/batch/statistics/count` instead of listing every batch.
- Definitions, deployments and the trends over days of history are read every five minutes
  instead of every 30 s. The 8 history counts of the trends remain at load.
- "Nothing needs attention" is shown only once the overdue tasks are counted, and a tile whose
  numbers cannot be read says so instead of waiting forever.

### Tasklist

- The filters are read without counts every five minutes. Their task counts (one task query
  per filter on the server) are read every two minutes while the filter pane is open, once
  after a burst of task actions instead of after each one, and not at all behind a closed pane
  while the filter menu is not in use. The filter in use takes its count from the task list.
- The tasks of the filter used last are requested beside the filters; the two business-key
  lookups (processes and cases) run together; a business key is read once and kept for five
  minutes (until the session ends) instead of with every refresh. That an instance's key cannot
  be read (a task permission does not cover the process instance) is kept the same way.
- An urgency view (overdue, due soon) no longer changes its query every minute; "now" is taken
  when the list is read. A filter with refresh off still moves such a view along once a minute,
  as before.
- With "Notify me about new tasks" on, the list is read at least once a minute even while nobody
  is at the screen, so a new task is reported within a minute. With browser notifications
  allowed it is also read while the tab is hidden, which is when the system notification is
  shown; before, a hidden tab never refreshed and that notification never appeared.

### Process instance

The activity-instance tree keeps the nodes of the parts that did not change, so a refresh
re-renders only what changed. The tree is read less often when it is large (over 100 or 500
activity instances) or has not changed for two or ten minutes; the 120 s window above is too
short to show that.

### Idle and away

Pages that refresh every 30 s do so every 60 s after two minutes without input and every five
minutes after ten minutes. The first input reads at once what has become old. Before, the rate
did not depend on input. Measured with `--window 60 --idle 150` (watched for 240 s after
150 s without input): the dashboard sends 9 instead of 18 API requests per minute (9.2 instead
of 18.4 kB), the task list 24.9 instead of 45.9 kB per minute.

Back from that, only the shown lists that refresh by themselves are read at once; a list whose
refresh the user paused stays as it is. While the keyboard focus is in a frame of the page (an
embedded or external task form), the user counts as present, as input there does not reach the
page. A wall display that nobody touches but that should stay current turns the pacing off:
`"idlePacing": false` in `config.json` for the installation, or `?idlePacing=false` in the
address before the `#` (e.g. `/fasti/app/next/default/?idlePacing=false#/cockpit`) for one
screen.

### Start-up

The first render waits at most three seconds for `config.json`; without an answer the page
starts with the defaults. The file is still read and applied when it answers later: its default
language replaces English unless the user chose one, and a request that changes something waits
for it (up to 20 s), so it is never sent with the wrong CSRF cookie name. A failed connection is
tried again three times. A language the user chose before is loaded beside the settings instead
of after them.

## Bundle

Sizes of `vite build`, minified (gzip in brackets):

| Chunk | 1.1.0 | Now |
|---|---:|---:|
| `index` (entry) | 396.5 kB (123.0) | 91.7 kB (27.0) |
| `react` (React, React DOM, router, scheduler) | in `index` | 315.1 kB (99.3) |
| `ui` (shared UI, English messages) | 274.2 kB (81.6) | 257.7 kB (76.3) |
| `icons` (icons several pages use) | 54 files under 1.5 kB | 27.4 kB (9.4) |
| JavaScript files | 204 | 164 |

The JavaScript loaded before the first page is about the same size: React and the router
moved to a chunk of their own, which changes only with a dependency update. Fasti Run, the
Docker image and the Spring Boot starter serve the hashed files below `/app/next/assets/` with
`Cache-Control: public, max-age=31536000, immutable`, so browsers keep a chunk whose content did
not change across releases; on Tomcat and WildFly the servlet container decides. The case
timeline of the task detail is loaded when its tab is opened.

A page left open across an update of the server names files of the old build that are gone. A
part loaded later that fails for this reason (the case tab or a diagram of the open task) says
in its place that a newer version is available and offers to reload; the rest of the page, with
a task form being filled in, stays.

The entry chunk grew from 293 kB (16 Nov) to 396 kB (21 Nov) with the features added up to
1.1.0, not with a dependency update. Its source maps show React DOM (207 kB) and React Router (96 kB)
as the largest parts. The largest part the application owns among what loads first is the
English message catalogue (about 128 kB minified in `ui`), which is the fallback of every
language; loading the non-core catalogues per area is a possible next step.
