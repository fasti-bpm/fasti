<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "totals"
        type = "ref"
        dto = "TaskLoadDto"
        desc = "Every open task the user may read." />

    <@lib.property
        name = "orphaned"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Unclaimed tasks that no group is offered." />

    <@lib.property
        name = "groups"
        type = "array"
        dto = "TaskLoadDto"
        desc = "One entry per group asked for, in that order." />

    <@lib.property
        name = "users"
        type = "array"
        dto = "TaskLoadDto"
        last = true
        desc = "One entry per user asked for, in that order." />

</@lib.dto>
</#macro>
