<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "chainId"
        type = "string"
        desc = "The chain." />

    <@lib.property
        name = "sequence"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "The position where the break was found." />

    <@lib.property
        name = "recordId"
        type = "string"
        desc = "The record at that position, or `null` if it is missing." />

    <@lib.property
        name = "reason"
        type = "string"
        desc = "Why: `hash-mismatch`, `previous-hash-mismatch`, `sequence-gap`, `missing-start`, `head-mismatch`, `head-missing` or `checkpoint-mismatch`." />

    <@lib.property
        name = "message"
        type = "string"
        last = true
        desc = "A readable explanation." />

</@lib.dto>
</#macro>
