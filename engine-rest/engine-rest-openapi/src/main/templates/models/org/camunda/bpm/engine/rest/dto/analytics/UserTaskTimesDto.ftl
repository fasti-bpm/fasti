<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "key"
        type = "string"
        desc = "The task definition key, or the assignee; `null` for tasks completed without an assignee." />

    <@lib.property
        name = "name"
        type = "string"
        desc = "The name of the task definition; `null` for an assignee." />

    <@lib.property
        name = "count"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Completed tasks read." />

    <@lib.property
        name = "unknownWaiting"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Tasks without a recorded assignment; their time counts in neither waiting nor working." />

    <@lib.property
        name = "waiting"
        type = "ref"
        dto = "AnalyticsDurationStatisticsDto"
        desc = "From creation to the first assignment (exact percentiles)." />

    <@lib.property
        name = "working"
        type = "ref"
        dto = "AnalyticsDurationStatisticsDto"
        last = true
        desc = "From the first assignment to completion (exact percentiles)." />

</@lib.dto>
</#macro>
