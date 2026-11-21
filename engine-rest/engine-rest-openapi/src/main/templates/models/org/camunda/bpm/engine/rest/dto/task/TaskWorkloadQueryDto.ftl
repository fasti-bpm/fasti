<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "groupIdIn"
        type = "array"
        itemType = "string"
        desc = "Candidate group ids: at most 200 distinct ids, none empty, `null` or longer than 255 characters." />

    <@lib.property
        name = "userIdIn"
        type = "array"
        itemType = "string"
        desc = "Assignee ids: at most 200 distinct ids, none empty, `null` or longer than 255 characters." />

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
        desc = "Only tasks of these tenants: at most 200 ids, none empty, `null` or longer than 255 characters. An
                empty list means no restriction." />

</@lib.dto>
</#macro>
