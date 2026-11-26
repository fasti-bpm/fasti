<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getUserTaskAnalytics"
      tag = "Process Analytics"
      summary = "Get User Task Analytics"
      desc = "Fasti: waiting time (from creation to the first assignment) and working time (from then to completion) of the user tasks completed in the range, per task definition and per assignee, with exact percentiles. The first assignment comes from the identity link history (history level `full`). At most `maxTasks` tasks are read, the newest first. Computed by the database over the history (`ACT_HI_*` tables), with the authorization
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
        name = "maxTasks"
        location = "query"
        type = "integer"
        format = "int32"
        desc = "At most this many completed tasks are read, 1 to 200,000; 50,000 by default. `sampled` tells
                whether the range held more." />

    <#include "/lib/commons/analytics-params.ftl" >
    <@lib.parameters
        object = params
        last = true />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "UserTaskAnalyticsDto"
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
