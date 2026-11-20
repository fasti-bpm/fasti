<#macro endpoint_macro docsUrl="">
{

  <@lib.endpointInfo
      id = "handOverUserTasks"
      tag = "User"
      summary = "Hand Over Tasks"
      desc = "Fasti: hands the open (active) tasks assigned to the user over to the substitute of their current or
              upcoming absence, or to the substitute of that substitute if they are absent too. Each task gets a
              comment and the local variables `fasti_absentAssignee` and `fasti_absenceSubstitute`. The caller
              needs to be the user or have the permission `UPDATE` on the user; the permissions on the single
              tasks are not checked." />

  "parameters" : [

    <@lib.parameter
        name = "id"
        location = "path"
        type = "string"
        required = true
        desc = "The id of the absent user." />

    <@lib.parameter
        name = "maxTasks"
        location = "query"
        type = "integer"
        format = "int32"
        last = true
        desc = "At most this many tasks are handed over (1 to 1000, default 500); call again for the rest." />

  ],

  "responses" : {

    <@lib.response
        code = "200"
        dto = "AbsenceHandoverDto"
        desc = "Request successful."
        examples = ['"example-1": {
                       "summary": "POST /user/alice/absence/handover",
                       "value": {
                         "substituteId": "bob",
                         "handedOver": 12,
                         "remaining": 1
                       }
                     }'] />

    <@lib.response
        code = "400"
        dto = "ExceptionDto"
        desc = "Absences are not enabled, the user has no current or upcoming absence, no present substitute
                can be found, or `maxTasks` is out of range. See the
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
