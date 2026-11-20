<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "setUserAbsence"
      tag = "User"
      summary = "Set Absence"
      desc = "Fasti: sets (replaces) the absence of a user. While it is active, user tasks created for or assigned
              to the user go to the substitute. A user can set their own absence; the absence of another user
              needs the permission `UPDATE` on that user." />

  "parameters" : [

    <@lib.parameter
        name = "id"
        location = "path"
        type = "string"
        required = true
        last = true
        desc = "The id of the absent user." />

  ],

  <@lib.requestBody
      mediaType = "application/json"
      dto = "AbsenceDto"
      requestDesc = "Only `from`, `until`, `substituteId` and `note` are used."
      examples = ['"example-1": {
                       "summary": "PUT /user/alice/absence",
                       "value": {
                         "from": "2026-12-01T00:00:00.000+0700",
                         "until": "2026-12-15T00:00:00.000+0700",
                         "substituteId": "bob",
                         "note": "Handover notes are in the team wiki"
                       }
                     }'] />

  "responses" : {

    <@lib.response
        code = "200"
        dto = "AbsenceDto"
        desc = "Request successful. The stored absence." />

    <@lib.response
        code = "400"
        dto = "ExceptionDto"
        desc = "Absences are not enabled, or a value is invalid: no end, an end before the start or in the past,
                a substitute that does not exist, is the user, or is absent at the same time with substitutes
                that lead back to the user, or a note longer than 255 characters. See the
                [Introduction](${docsUrl}/reference/rest/overview/#error-handling)
                for the error response format." />

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
