<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getUserAbsence"
      tag = "User"
      summary = "Get Absence"
      desc = "Fasti: the absence of a user and their substitute, also an upcoming or ended one. The response
              also says whether absences can be set on the engine. A user can see their own absence; the absence
              of another user needs the permission `READ` on that user." />

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
        code = "200"
        dto = "AbsenceDto"
        desc = "Request successful. The `status` is `none` if no absence is set."
        examples = ['"example-1": {
                       "summary": "GET /user/alice/absence",
                       "value": {
                         "userId": "alice",
                         "enabled": true,
                         "unavailableReason": null,
                         "status": "active",
                         "from": "2026-12-01T00:00:00.000+0700",
                         "until": "2026-12-15T00:00:00.000+0700",
                         "substituteId": "bob",
                         "note": "Handover notes are in the team wiki",
                         "effectiveSubstituteId": "bob"
                       }
                     }'] />

    <@lib.response
        code = "403"
        dto = "AuthorizationExceptionDto"
        last = true
        desc = "The user may not read the user. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

  }
}

</#macro>
