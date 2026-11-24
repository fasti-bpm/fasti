<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getAnalyticsTrend"
      tag = "Process Analytics"
      summary = "Get Duration Trend"
      desc = "Fasti: one entry per period between consecutive `boundaries`: the instances started and the duration statistics of the instances completed in the period. The range parameters `finishedAfter` and `finishedBefore` do not apply. Computed by the database over the history (`ACT_HI_*` tables), with the authorization
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
        name = "boundaries"
        location = "query"
        type = "string"
        required = true
        desc = "The period boundaries, comma separated dates in ascending order: 2 to 63 dates, so at most 62
                periods. Computing them in the time zone of the user makes days, weeks and months local." />

    <#include "/lib/commons/analytics-params.ftl" >
    <@lib.parameters
        object = params
        last = true />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "PeriodAnalyticsDto"
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
        code = "501"
        dto = "ExceptionDto"
        last = true
        desc = "The MyBatis mappings of the engine lack the analytics statements: an application replaced the
                default `mappings.xml` with a copy of its own without `dev/fasti/engine/analytics/ProcessAnalytics.xml`." />

  }
}
</#macro>
