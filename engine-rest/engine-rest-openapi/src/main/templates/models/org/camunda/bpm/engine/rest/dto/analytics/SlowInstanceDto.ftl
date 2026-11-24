<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "activityInstanceId"
        type = "string"
        desc = "The id of the run." />

    <@lib.property
        name = "processInstanceId"
        type = "string"
        desc = "The process instance." />

    <@lib.property
        name = "processDefinitionId"
        type = "string"
        desc = "The version of the process." />

    <@lib.property
        name = "businessKey"
        type = "string"
        desc = "The business key of the process instance." />

    <@lib.property
        name = "startTime"
        type = "string"
        format = "date-time"
        desc = "Start of the run." />

    <@lib.property
        name = "endTime"
        type = "string"
        format = "date-time"
        desc = "End of the run; `null` while running." />

    <@lib.property
        name = "duration"
        type = "integer"
        format = "int64"
        last = true
        desc = "Milliseconds; for a running one the time since it started." />

</@lib.dto>
</#macro>
