<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getActivityAnalytics"
      tag = "Process Analytics"
      summary = "Get Activity Analytics"
      desc = "Fasti: one entry per activity that ran in the range or runs now: runs finished, canceled and running, the duration statistics of the finished runs (percentiles within about 12 %, from a histogram), the time beyond the median, the running runs slower than the P90, rework (scopes that ran the activity more than once, multi-instance iterations excluded) and incidents. The basis of a heatmap and of a bottleneck ranking. Computed by the database over the history (`ACT_HI_*` tables), with the authorization
              and tenant checks of the history; requires `READ_HISTORY` on the process definition key. Nothing is
              written." />

  "parameters" : [

    <@lib.parameter
        name = "key"
        location = "path"
        type = "string"
        required = true
        desc = "The key of the process definition." />

    <@lib.parameter
        name = "rework"
        location = "query"
        type = "boolean"
        desc = "`false` leaves rework out (`repeatedInstances` and `repeatedExecutions` are `null`), which makes the
                call cheaper on a long history; read it with `GET .../rework`. `true` by default." />

    <#include "/lib/commons/analytics-params.ftl" >
    <@lib.parameters
        object = params
        last = true />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "ActivityAnalyticsDto"
        array = true
        desc = "Request successful." />

    <@lib.response
        code = "400"
        dto = "ExceptionDto"
        desc = "A parameter is invalid: a date that is not a date, a negative number, `finishedAfter` not before
                `finishedBefore`, more than 200 ids, a version of another process, or `maxResults`, `maxTasks` or
                `running` out of range. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling) for the error response format." />

    <@lib.response
        code = "403"
        dto = "AuthorizationExceptionDto"
        desc = "The user lacks `READ_HISTORY` on the process definition key." />

    <@lib.response
        code = "404"
        dto = "ExceptionDto"
        desc = "No process with this key exists and no history of one." />

    <@lib.response
        code = "503"
        dto = "ExceptionDto"
        desc = "This node already reads as many analytics as it allows (`fasti.analytics.max-concurrent`) and no
                turn came within `fasti.analytics.max-wait`, or a statement ran longer than
                `fasti.analytics.statement-timeout` (the message says which). Ask again later, or for a shorter range." />

    <@lib.response
        code = "501"
        dto = "ExceptionDto"
        last = true
        desc = "The MyBatis mappings of the engine lack the analytics statements: an application replaced the
                default `mappings.xml` with a copy of its own without `dev/fasti/engine/analytics/ProcessAnalytics.xml`." />

  }
}
</#macro>
