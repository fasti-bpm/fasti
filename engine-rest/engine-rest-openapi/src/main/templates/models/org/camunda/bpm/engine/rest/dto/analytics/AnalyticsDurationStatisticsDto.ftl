<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "count"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Number of durations." />

    <@lib.property
        name = "total"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "All durations added up, in milliseconds: the time spent there." />

    <@lib.property
        name = "minimum"
        type = "integer"
        format = "int64"
        desc = "Milliseconds; `null` without durations." />

    <@lib.property
        name = "maximum"
        type = "integer"
        format = "int64"
        desc = "Milliseconds; `null` without durations." />

    <@lib.property
        name = "average"
        type = "integer"
        format = "int64"
        desc = "Milliseconds; `null` without durations." />

    <@lib.property
        name = "p50"
        type = "integer"
        format = "int64"
        desc = "The median, in milliseconds." />

    <@lib.property
        name = "p75"
        type = "integer"
        format = "int64"
        desc = "Milliseconds." />

    <@lib.property
        name = "p90"
        type = "integer"
        format = "int64"
        desc = "Nine in ten took at most this long, in milliseconds." />

    <@lib.property
        name = "p95"
        type = "integer"
        format = "int64"
        desc = "Milliseconds." />

    <@lib.property
        name = "p99"
        type = "integer"
        format = "int64"
        desc = "Milliseconds." />

    <@lib.property
        name = "approximate"
        type = "boolean"
        nullable = false
        desc = "`true` if the percentiles come from the histogram (within about 12 %); count, total, minimum, maximum and average are exact." />

    <@lib.property
        name = "withinTarget"
        type = "integer"
        format = "int64"
        desc = "How many took at most `targetDuration`; `null` without a target." />

    <@lib.property
        name = "buckets"
        type = "array"
        dto = "AnalyticsDurationBucketDto"
        last = true
        desc = "The non-empty buckets of the histogram, twenty per power of ten, shortest first." />

</@lib.dto>
</#macro>
