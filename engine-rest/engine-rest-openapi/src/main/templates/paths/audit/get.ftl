<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getAuditStatus"
      tag = "Audit Trail"
      summary = "Get Audit Trail Settings"
      desc = "Fasti: whether the audit trail runs on the engine and how it is set up. Holds no secret: the HMAC key
              is only named by `keyId`." />

  "responses" : {

    <@lib.response
        code = "200"
        dto = "AuditStatusDto"
        last = true
        desc = "Request successful. `enabled` is `false` without the plugin; the other fields are then empty." />

  }
}

</#macro>
