<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "queryTaskWorkload"
      tag = "Task"
      summary = "Get Task Workload (POST)"
      desc = "Fasti: the same as the `GET` method with the parameters as JSON body, for lists of ids that are too long
              for a URL. Nothing is written." />

  <@lib.requestBody
      mediaType = "application/json"
      dto = "TaskWorkloadQueryDto"
      examples = ['"example-1": {
                       "summary": "POST /task/workload",
                       "value": {
                         "groupIdIn": ["accounting", "sales"],
                         "userIdIn": ["demo", "john"]
                       }
                     }'] />

  "responses" : {

    <@lib.response
        code = "200"
        dto = "TaskWorkloadDto"
        desc = "Request successful." />

    <@lib.response
        code = "400"
        dto = "ExceptionDto"
        last = true
        desc = "A list has more than 200 ids. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

  }
}
</#macro>
