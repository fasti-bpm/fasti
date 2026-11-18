<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "createProcessInstanceComment"
      tag = "Process Instance Comment"
      summary = "Create"
      desc = "Creates a comment for a running process instance as a whole, not for one of its tasks.
              The user needs the permission to update the process instance." />

  "parameters" : [

    <@lib.parameter
        name = "id"
        location = "path"
        type = "string"
        required = true
        last = true
        desc = "The id of the process instance to add the comment to." />

  ],

  <@lib.requestBody
      mediaType = "application/json"
      dto = "CommentDto"
      requestDesc = "**Note:** Only the `message` property is used. Every other property passed to this endpoint is ignored."
      examples = ['"example-1": {
                       "summary": "POST /process-instance/aProcessInstanceId/comment/create",
                       "value": {
                         "message": "The customer called back"
                       }
                     }'] />

  "responses" : {

    <@lib.response
        code = "200"
        dto = "CommentDto"
        desc = "Request successful."
        examples = ['"example-1": {
                       "summary": "Status 200 Response",
                       "value": {
                         "id": "aCommentId",
                         "userId": "userId",
                         "taskId": null,
                         "processInstanceId": "aProcessInstanceId",
                         "time": "2013-01-02T21:37:03.887+0200",
                         "message": "The customer called back",
                         "removalTime": null,
                         "rootProcessInstanceId": "aProcessInstanceId"
                       }
                     }'] />

    <@lib.response
        code = "400"
        dto = "ExceptionDto"
        desc = "No comment message was submitted. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

    <@lib.response
        code = "403"
        dto = "AuthorizationExceptionDto"
        desc = "The history of the engine is disabled or the user may not update the process instance. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

    <@lib.response
        code = "404"
        dto = "ExceptionDto"
        last = true
        desc = "No running process instance with the id exists that the user may read. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

  }
}

</#macro>
