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
        desc = "One entry per distinct group asked for, in the order of first occurrence, each with the id as it
                was asked for." />

    <@lib.property
        name = "users"
        type = "array"
        dto = "TaskLoadDto"
        last = true
        desc = "One entry per distinct user asked for, in the order of first occurrence, each with the id as it
                was asked for." />

</@lib.dto>
</#macro>
