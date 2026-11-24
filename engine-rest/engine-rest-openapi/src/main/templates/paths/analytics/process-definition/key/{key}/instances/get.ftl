<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getInstanceAnalytics"
      tag = "Process Analytics"
      summary = "Get Process Instance Analytics"
      desc = "Fasti: the process instances completed and terminated in the range, those running now, and the duration statistics of the completed ones. Computed by the database over the history (`ACT_HI_*` tables), with the authorization
              and tenant checks of the history; requires `READ_HISTORY` on the process definition key. Nothing is
              written." />

  "parameters" : [

    <@lib.parameter
        name = "key"
        location = "path"
        type = "string"
        required = true
        desc = "The key of the process definition." />

    <#include "/lib/commons/analytics-params.ftl" >
    <@lib.parameters
        object = params
        last = true />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "InstanceAnalyticsDto"
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
