<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "groupIdIn"
        type = "array"
        itemType = "string"
        desc = "Candidate group ids, at most 200." />

    <@lib.property
        name = "userIdIn"
        type = "array"
        itemType = "string"
        desc = "Assignee ids, at most 200." />

    <@lib.property
        name = "overdueBefore"
        type = "string"
        format = "date-time"
        desc = "Tasks due before this time count as overdue. The current time by default." />

    <@lib.property
        name = "tenantIdIn"
        type = "array"
        itemType = "string"
        last = true
        desc = "Only tasks of these tenants." />

</@lib.dto>
</#macro>
