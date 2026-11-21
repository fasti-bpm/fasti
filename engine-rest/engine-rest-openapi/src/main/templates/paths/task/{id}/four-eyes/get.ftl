<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getTaskFourEyesStatus"
      tag = "Task"
      summary = "Get Four-Eyes Status"
      desc = "Fasti: whether the authenticated user may complete the task under its four-eyes rule (the
              extension property `fasti:fourEyes` of the user task). The response names the earlier steps the
              task checks and says whether the authenticated user or the assignee completed one of them; such a
              person cannot complete the task, submit its form, or end it with a BPMN error or an escalation
              (`403`, type `FourEyesViolationException`). Needs the permission to read the task." />

  "parameters" : [

    <@lib.parameter
        name = "id"
        location = "path"
        type = "string"
        required = true
        last = true
        desc = "The id of the task." />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "FourEyesStatusDto"
        desc = "Request successful. `guarded` is `false` for a task without rule, and `enabled` is `false` if the
                four-eyes plugin is not registered with the engine."
        examples = ['"example-1": {
                       "summary": "GET /task/aTaskId/four-eyes",
                       "value": {
                         "taskId": "aTaskId",
                         "enabled": true,
                         "guarded": true,
                         "earlierSteps": [
                           { "id": "draftContract", "name": "Draft contract" }
                         ],
                         "userId": "alice",
                         "userExcluded": true,
                         "userSteps": ["draftContract"],
                         "assignee": "alice",
                         "assigneeExcluded": true,
                         "assigneeSteps": ["draftContract"],
                         "blocked": true
                       }
                     }'] />

    <@lib.response
        code = "404"
        dto = "ExceptionDto"
        last = true
        desc = "The task does not exist, or the user may not read it (the two are answered alike, as by
                `GET /task/{id}`). See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

  }
}

</#macro>
