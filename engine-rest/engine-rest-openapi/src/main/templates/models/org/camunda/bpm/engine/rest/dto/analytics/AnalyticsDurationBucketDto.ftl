<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "lower"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Durations from this many milliseconds (inclusive)." />

    <@lib.property
        name = "upper"
        type = "integer"
        format = "int64"
        desc = "Up to this many milliseconds (exclusive); `null` for the last bucket." />

    <@lib.property
        name = "count"
        type = "integer"
        format = "int64"
        nullable = false
        last = true
        desc = "How many durations fell into the bucket." />

</@lib.dto>
</#macro>
