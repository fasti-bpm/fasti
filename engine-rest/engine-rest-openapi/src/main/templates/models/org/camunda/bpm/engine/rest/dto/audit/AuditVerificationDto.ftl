<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "intact"
        type = "boolean"
        nullable = false
        desc = "Whether every chain verified." />

    <@lib.property
        name = "verifiedAt"
        type = "string"
        format = "date-time"
        desc = "When the verification ran." />

    <@lib.property
        name = "records"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Records read in all." />

    <@lib.property
        name = "chains"
        type = "array"
        dto = "AuditChainDto"
        desc = "The verification of each chain." />

    <@lib.property
        name = "firstBreak"
        type = "ref"
        dto = "AuditBreakDto"
        last = true
        desc = "The first break of the first broken chain, or `null`." />

</@lib.dto>
</#macro>
