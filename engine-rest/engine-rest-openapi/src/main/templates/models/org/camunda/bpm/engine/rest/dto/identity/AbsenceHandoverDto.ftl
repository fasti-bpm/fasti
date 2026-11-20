<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "substituteId"
        type = "string"
        desc = "The user who got the tasks." />

    <@lib.property
        name = "handedOver"
        type = "integer"
        format = "int32"
        desc = "How many tasks were handed over." />

    <@lib.property
        name = "remaining"
        type = "integer"
        format = "int64"
        last = true
        desc = "How many tasks are still assigned to the user: suspended ones, or more than `maxTasks`." />

</@lib.dto>
</#macro>
