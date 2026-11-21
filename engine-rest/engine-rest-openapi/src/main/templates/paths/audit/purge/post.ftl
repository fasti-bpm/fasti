<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "purgeAuditTrail"
      tag = "Audit Trail"
      summary = "Purge Old Audit Records"
      desc = "Fasti: removes, per chain, the records from its start up to the last one before `before`, and appends a
              purge record that names the hash of the last removed record, so the rest of the chain still verifies.
              A chain is purged in batches (`fasti.audit.purge-batch-size`, default 10,000 records), each in a
              transaction of its own with a purge record of its own. A chain that cannot be purged does not stop the
              others: it is listed in `failedChains` of the result. Records younger than the retention period
              (`fasti.audit.retention-days`) are kept. Export the records first and confirm it with `exported`.
              Needs membership of an administrators group or the permission `DELETE` on the resource `SYSTEM`." />

  <@lib.requestBody
      mediaType = "application/json"
      dto = "AuditPurgeDto"
      examples = ['"example-1": {
                       "summary": "POST /audit/purge",
                       "value": {
                         "before": "2025-01-01T00:00:00.000+0700",
                         "exported": true
                       }
                     }'] />

  "responses" : {

    <@lib.response
        code = "200"
        dto = "AuditPurgeResultDto"
        desc = "Request successful; chains whose purge failed are listed in `failedChains`." />

    <@lib.response
        code = "400"
        dto = "ExceptionDto"
                desc = "The audit trail is not enabled, `before` is missing or within the retention period, or `exported` is not `true`. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

    <@lib.response
        code = "403"
        dto = "AuthorizationExceptionDto"
        last = true
        desc = "The user may not purge the audit trail. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

  }
}

</#macro>
