<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "tasks"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Completed tasks read." />

    <@lib.property
        name = "sampled"
        type = "boolean"
        nullable = false
        desc = "`true` if the range held more than `maxTasks` completed tasks: the newest were read." />

    <@lib.property
        name = "byTask"
        type = "array"
        dto = "UserTaskTimesDto"
        desc = "One entry per task definition, most frequent first (at most 200)." />

    <@lib.property
        name = "byAssignee"
        type = "array"
        dto = "UserTaskTimesDto"
        last = true
        desc = "One entry per assignee, most frequent first (at most 200)." />

</@lib.dto>
</#macro>
