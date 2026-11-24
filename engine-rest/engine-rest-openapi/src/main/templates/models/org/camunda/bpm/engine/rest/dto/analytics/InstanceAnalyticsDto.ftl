<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "completed"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Instances completed in the range." />

    <@lib.property
        name = "terminated"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Instances terminated in the range, internally or externally." />

    <@lib.property
        name = "running"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Instances running now." />

    <@lib.property
        name = "durations"
        type = "ref"
        dto = "AnalyticsDurationStatisticsDto"
        last = true
        desc = "Durations of the instances completed in the range." />

</@lib.dto>
</#macro>
