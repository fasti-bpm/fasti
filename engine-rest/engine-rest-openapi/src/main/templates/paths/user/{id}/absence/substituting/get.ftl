<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "getUserSubstitutions"
      tag = "User"
      summary = "Get Substituted Absences"
      desc = "Fasti: the absences that are active now and whose tasks go to this user, directly or because
              the substitute named is absent too. A user can see this for themselves; for another user it needs
              the permission `READ` on that user." />

  "parameters" : [

    <@lib.parameter
        name = "id"
        location = "path"
        type = "string"
        required = true
        last = true
        desc = "The id of the substitute." />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "AbsenceDto"
        array = true
        desc = "Request successful."
        examples = ['"example-1": {
                       "summary": "GET /user/bob/absence/substituting",
                       "value": [{
                         "userId": "alice",
                         "enabled": true,
                         "unavailableReason": null,
                         "status": "active",
                         "from": "2026-12-01T00:00:00.000+0700",
                         "until": "2026-12-15T00:00:00.000+0700",
                         "substituteId": "bob",
                         "note": "Handover notes are in the team wiki",
                         "effectiveSubstituteId": "bob"
                       }]
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
