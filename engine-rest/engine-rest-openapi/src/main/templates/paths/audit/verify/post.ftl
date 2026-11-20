<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "verifyAuditTrail"
      tag = "Audit Trail"
      summary = "Verify Audit Trail"
      desc = "Fasti: recomputes the hash chains and reports, per chain, whether it is intact and where it first
              breaks (a changed, removed or inserted record, a rewritten chain before a signed checkpoint, or a
              head that does not match). The verification is recorded in the audit trail. The user needs membership of an administrators group (`camunda-admin`) or the permission
              `READ` on the resource `SYSTEM`." />

  "parameters" : [

    <@lib.parameter
        name = "chainId"
        location = "query"
        type = "string"
        last = true
        desc = "Verify only this chain." />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "AuditVerificationDto"
        desc = "Request successful. `intact` is `false` if a chain breaks." />

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
