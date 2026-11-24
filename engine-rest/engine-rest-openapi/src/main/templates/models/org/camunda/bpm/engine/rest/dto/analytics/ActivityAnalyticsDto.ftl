<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "activityId"
        type = "string"
        desc = "The id of the activity; a multi-instance body ends with `#multiInstanceBody`." />

    <@lib.property
        name = "activityType"
        type = "string"
        desc = "The type, for example `userTask`." />

    <@lib.property
        name = "activityName"
        type = "string"
        desc = "The name, if the model gives one." />

    <@lib.property
        name = "finished"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Runs finished in the range, not canceled." />

    <@lib.property
        name = "canceled"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Runs canceled in the range." />

    <@lib.property
        name = "running"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Runs running now." />

    <@lib.property
        name = "runningOverP90"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Runs running now for longer than the P90 of the finished ones." />

    <@lib.property
        name = "oldestRunningStart"
        type = "string"
        format = "date-time"
        desc = "Start of the oldest run running now." />

    <@lib.property
        name = "repeatedInstances"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Scopes (process or subprocess instances) that ran the activity more than once in the range." />

    <@lib.property
        name = "repeatedExecutions"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "The runs beyond the first in those scopes." />

    <@lib.property
        name = "openIncidents"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Incidents open now." />

    <@lib.property
        name = "incidents"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Incidents created in the range." />

    <@lib.property
        name = "timeAboveMedian"
        type = "integer"
        format = "int64"
        desc = "Milliseconds the finished runs took beyond the median, together." />

    <@lib.property
        name = "durations"
        type = "ref"
        dto = "AnalyticsDurationStatisticsDto"
        last = true
        desc = "Durations of the runs finished in the range." />

</@lib.dto>
</#macro>
