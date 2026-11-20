<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "before"
        type = "string"
        format = "date-time"
        desc = "Records before this time are removed, per chain from its start." />

    <@lib.property
        name = "exported"
        type = "boolean"
        nullable = false
        last = true
        desc = "Confirms that the records were exported first; required." />

</@lib.dto>
</#macro>
