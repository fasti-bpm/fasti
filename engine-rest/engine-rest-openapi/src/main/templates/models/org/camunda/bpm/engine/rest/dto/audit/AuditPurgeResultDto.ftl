<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "before"
        type = "string"
        format = "date-time"
        desc = "The time given." />

    <@lib.property
        name = "removed"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Records removed in all." />

    <@lib.property
        name = "purgedThrough"
        type = "object"
        additionalProperties = true
        itemType = "integer"
        last = true
        desc = "Per chain that lost records: the position of the last removed record." />

</@lib.dto>
</#macro>
