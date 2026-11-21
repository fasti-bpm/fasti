<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "id"
        type = "string"
        desc = "The id of the group or of the user; `null` for the totals." />

    <@lib.property
        name = "open"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Open tasks: offered to the group (claimed or not), or assigned to the user." />

    <@lib.property
        name = "unassigned"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Those of the open tasks nobody has claimed; always 0 for a user." />

    <@lib.property
        name = "overdue"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Those of the open tasks that were due before `overdueBefore`." />

    <@lib.property
        name = "oldest"
        type = "string"
        format = "date-time"
        last = true
        desc = "The creation time of the oldest of the open tasks; `null` without open tasks." />

</@lib.dto>
</#macro>
