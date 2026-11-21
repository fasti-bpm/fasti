<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getTaskWorkload"
      tag = "Task"
      summary = "Get Task Workload"
      desc = "Fasti: counts the open tasks in total and for each candidate group and assignee asked for: open,
              unclaimed, overdue and the creation time of the oldest one. The numbers are computed with a few grouped
              statements instead of one task query per group and user, and cover exactly the tasks a task query of the
              user would return (the same authorization and tenant checks apply). Ids are compared as the database
              compares them: with a case-insensitive collation (the default of SQL Server, MySQL and MariaDB) `sales`
              also counts the tasks of `Sales`, as a task query does. Nothing is written." />

  "parameters" : [

    <@lib.parameter
        name = "groupIdIn"
        location = "query"
        type = "string"
        desc = "Candidate group ids, comma separated: at most 200 distinct ids of at most 255 characters each. A
                task offered to several of the groups counts for each of them. A repeated id is answered once; an
                empty id within the list (`a,,b`) is refused; the parameter without a value means no groups." />

    <@lib.parameter
        name = "userIdIn"
        location = "query"
        type = "string"
        desc = "Assignee ids, comma separated; the rules of `groupIdIn` apply." />

    <@lib.parameter
        name = "overdueBefore"
        location = "query"
        type = "string"
        format = "date-time"
        desc = "Tasks due before this time count as overdue. The current time by default." />

    <@lib.parameter
        name = "tenantIdIn"
        location = "query"
        type = "string"
        last = true
        desc = "Only tasks of these tenants, comma separated: at most 200 ids of at most 255 characters each. An
                empty id within the list is refused." />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "TaskWorkloadDto"
        desc = "Request successful."
        examples = ['"example-1": {
                       "summary": "GET /task/workload?groupIdIn=accounting&userIdIn=demo",
                       "value": {
                         "totals": {"id": null, "open": 12, "unassigned": 5, "overdue": 2, "oldest": "2026-11-02T08:15:00.000+0700"},
                         "orphaned": 1,
                         "groups": [{"id": "accounting", "open": 7, "unassigned": 4, "overdue": 1, "oldest": "2026-11-02T08:15:00.000+0700"}],
                         "users": [{"id": "demo", "open": 3, "unassigned": 0, "overdue": 1, "oldest": "2026-11-10T09:00:00.000+0700"}]
                       }
                     }'] />

    <@lib.response
        code = "400"
        dto = "ExceptionDto"
        desc = "A list has more than 200 distinct ids, an empty id or one longer than 255 characters, or
                `overdueBefore` is no date. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

    <@lib.response
        code = "501"
        dto = "ExceptionDto"
        last = true
        desc = "The MyBatis mappings of the engine lack the workload statements: an application replaced the
                default `mappings.xml` with a copy of its own without `dev/fasti/engine/workload/TaskWorkload.xml`.
                Clients can count with one task query per group and user instead." />

  }
}
</#macro>
