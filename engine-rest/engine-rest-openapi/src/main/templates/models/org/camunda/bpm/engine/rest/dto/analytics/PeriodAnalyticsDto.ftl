<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "start"
        type = "string"
        format = "date-time"
        desc = "Start of the period (inclusive)." />

    <@lib.property
        name = "end"
        type = "string"
        format = "date-time"
        desc = "End of the period (exclusive)." />

    <@lib.property
        name = "started"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Instances started in the period." />

    <@lib.property
        name = "durations"
        type = "ref"
        dto = "AnalyticsDurationStatisticsDto"
        last = true
        desc = "Durations of the instances completed in the period; `count` is the number completed." />

</@lib.dto>
</#macro>
