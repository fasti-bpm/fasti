<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "activityId"
        type = "string"
        desc = "The id of the activity." />

    <@lib.property
        name = "repeatedInstances"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Scopes that ran the activity more than once in the range." />

    <@lib.property
        name = "repeatedExecutions"
        type = "integer"
        format = "int64"
        nullable = false
        last = true
        desc = "The runs beyond the first in those scopes." />

</@lib.dto>
</#macro>
