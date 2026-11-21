<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "queryTaskWorkload"
      tag = "Task"
      summary = "Get Task Workload (POST)"
      desc = "Fasti: the same as the `GET` method with the parameters as JSON body, for lists of ids that are too long
              for a URL. The same limits apply. Nothing is written." />

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
        desc = "A list has more than 200 distinct ids, an empty or `null` id or one longer than 255 characters,
                `overdueBefore` is no date, or the body is no JSON object of this form. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

    <@lib.response
        code = "501"
        dto = "ExceptionDto"
        last = true
        desc = "The MyBatis mappings of the engine lack the workload statements; see the `GET` method." />

  }
}
</#macro>
