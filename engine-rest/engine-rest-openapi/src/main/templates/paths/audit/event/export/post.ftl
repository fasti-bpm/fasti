<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "exportAuditEvents"
      tag = "Audit Trail"
      summary = "Export Audit Records"
      desc = "Fasti: the selected records as a file, oldest first, at most 100000, streamed from one database
              cursor. The export itself is recorded in the audit trail (who, how many, which filter), which is why
              it is a `POST`; the filter is given as query parameters. A user who is not an administrator gets the
              records of the tenants of the authentication and the records without tenant. The JSON export keeps every value exactly as hashed,
              so it can be verified offline; the CSV export puts an apostrophe before text a spreadsheet would run
              as a formula. The user needs membership of an administrators group (`camunda-admin`) or the permission
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
        desc = "Records of this chain (`<node id>/<slot>` or `<node id>/end-<slot>`)." />

    <@lib.parameter
        name = "from"
        location = "query"
        type = "string"
        desc = "Records at or after this time, for example `2026-11-01T00:00:00.000+0700`." />

    <@lib.parameter
        name = "to"
        location = "query"
        type = "string"
        desc = "Records before this time." />

    <@lib.parameter
        name = "format"
        location = "query"
        type = "string"
        enumValues = ["\"csv\"", "\"json\""]
        last = true
        desc = "File format: `csv` (default) or `json`." />

  ],

  "responses" : {

    <@lib.multiTypeResponse
        code = "200"
        types = [
          {
            "binary": true,
            "mediaType": "text/csv"
          },
          {
            "binary": true,
            "mediaType": "application/json"
          }
        ]
        desc = "Request successful. The file, as an attachment." />

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
