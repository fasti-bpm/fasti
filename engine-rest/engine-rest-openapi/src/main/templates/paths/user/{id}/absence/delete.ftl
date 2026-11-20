<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "clearUserAbsence"
      tag = "User"
      summary = "Clear Absence"
      desc = "Fasti: removes the absence of a user, if any. Tasks that went to the substitute stay with the
              substitute. A user can clear their own absence; the absence of another user needs the permission
              `UPDATE` on that user." />

  "parameters" : [

    <@lib.parameter
        name = "id"
        location = "path"
        type = "string"
        required = true
        last = true
        desc = "The id of the user." />

  ],

  "responses" : {

    <@lib.response
        code = "204"
        desc = "Request successful." />

    <@lib.response
        code = "403"
        dto = "AuthorizationExceptionDto"
        last = true
        desc = "The user may not update the user. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

  }
}

</#macro>
