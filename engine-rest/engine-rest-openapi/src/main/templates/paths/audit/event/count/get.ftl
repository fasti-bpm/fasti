<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getAuditEventCount"
      tag = "Audit Trail"
      summary = "Get Audit Record Count"
      desc = "Fasti: counts the records of the audit trail that the filters select. The user needs membership of an administrators group (`camunda-admin`) or the permission
              `READ` on the resource `SYSTEM`." />

  "parameters" : [

    <@lib.parameter
        name = "processInstanceId"
        location = "query"
        type = "string"
        desc = "Records of this process instance." />

    <@lib.parameter
        name = "businessKey"
        location = "query"
        type = "string"
        desc = "Records of process instances with this business key." />

    <@lib.parameter
        name = "actor"
        location = "query"
        type = "string"
        desc = "Records of this authenticated user; `system` for records of the engine itself (job executor, timers, startup)." />

    <@lib.parameter
        name = "type"
        location = "query"
        type = "string"
        desc = "Categories, comma separated: `process`, `task`, `variable`, `deployment`, `operation`, `incident`, `decision`, `activity`, `audit`." />

    <@lib.parameter
        name = "action"
        location = "query"
        type = "string"
        desc = "What happened, for example `start`, `assign`, `complete` or an operation type such as `Claim`." />

    <@lib.parameter
        name = "tenantId"
        location = "query"
        type = "string"
        desc = "Records of this tenant." />

    <@lib.parameter
        name = "processDefinitionKey"
        location = "query"
        type = "string"
        desc = "Records of process instances of this definition key." />

    <@lib.parameter
        name = "entityId"
        location = "query"
        type = "string"
        desc = "Records about this object, for example a task id or a deployment id." />

    <@lib.parameter
        name = "chainId"
        location = "query"
        type = "string"
        desc = "Records of this chain (`<node id>/<slot>`)." />

    <@lib.parameter
        name = "from"
        location = "query"
        type = "string"
        desc = "Records at or after this time, for example `2026-11-01T00:00:00.000+0700`." />

    <@lib.parameter
        name = "to"
        location = "query"
        type = "string"
        last = true
        desc = "Records before this time." />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "CountResultDto"
        desc = "Request successful." />

    <@lib.response
        code = "400"
        dto = "ExceptionDto"
        desc = "The audit trail is not enabled on the engine, or a parameter is invalid. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

    <@lib.response
        code = "403"
        dto = "AuthorizationExceptionDto"
        last = true
        desc = "The user may not read the audit trail. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

  }
}

</#macro>
