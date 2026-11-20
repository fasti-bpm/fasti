<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "taskId"
        type = "string"
        desc = "The id of the task." />

    <@lib.property
        name = "enabled"
        type = "boolean"
        desc = "Whether the four-eyes plugin is registered with the engine." />

    <@lib.property
        name = "guarded"
        type = "boolean"
        desc = "Whether the task has a four-eyes rule." />

    <@lib.property
        name = "earlierSteps"
        type = "array"
        dto = "FourEyesStepDto"
        desc = "The earlier steps the task checks, in the order of the model." />

    <@lib.property
        name = "userId"
        type = "string"
        desc = "The authenticated user the status is for, or `null`." />

    <@lib.property
        name = "userExcluded"
        type = "boolean"
        desc = "Whether the authenticated user completed one of the earlier steps." />

    <@lib.property
        name = "userSteps"
        type = "array"
        itemType = "string"
        desc = "The ids of the earlier steps the authenticated user completed." />

    <@lib.property
        name = "assignee"
        type = "string"
        desc = "The assignee of the task, or `null`." />

    <@lib.property
        name = "assigneeExcluded"
        type = "boolean"
        desc = "Whether the assignee completed one of the earlier steps." />

    <@lib.property
        name = "assigneeSteps"
        type = "array"
        itemType = "string"
        desc = "The ids of the earlier steps the assignee completed." />

    <@lib.property
        name = "blocked"
        type = "boolean"
        last = true
        desc = "Whether the engine would refuse to let the authenticated user complete the task now: the user or
                the assignee completed an earlier step, or neither exists while an earlier step was completed." />

</@lib.dto>
</#macro>
