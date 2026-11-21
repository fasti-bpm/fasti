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
        desc = "How many tasks are still assigned to the user: suspended ones, more than `maxTasks`, or
                ones the caller may not assign." />

    <@lib.property
        name = "notAllowed"
        type = "integer"
        format = "int32"
        last = true
        desc = "How many of the tasks this call looked at stayed with the user because the caller may not
                assign them (`TASK_ASSIGN` or `UPDATE` on the task or its process definition)." />

</@lib.dto>
</#macro>
