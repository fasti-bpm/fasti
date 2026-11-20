<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "userId"
        type = "string"
        desc = "The id of the absent user. Ignored in requests." />

    <@lib.property
        name = "enabled"
        type = "boolean"
        nullable = false
        desc = "Whether absences can be set on the process engine (Fasti property `fasti.absence.enabled`).
                Ignored in requests." />

    <@lib.property
        name = "unavailableReason"
        type = "string"
        desc = "Why absences cannot be set, if they cannot. Ignored in requests." />

    <@lib.property
        name = "status"
        type = "string"
        enumValues = ['"none"', '"upcoming"', '"active"', '"ended"']
        desc = "Where the absence stands now. Ignored in requests." />

    <@lib.property
        name = "from"
        type = "string"
        format = "date-time"
        desc = "Start of the absence (inclusive). In requests optional: now if left out.
                Default format `yyyy-MM-dd'T'HH:mm:ss.SSSZ`." />

    <@lib.property
        name = "until"
        type = "string"
        format = "date-time"
        desc = "End of the absence (exclusive); after `from` and in the future. Required in requests." />

    <@lib.property
        name = "substituteId"
        type = "string"
        desc = "The user who takes over the tasks; an existing user other than the absent one. Required in requests." />

    <@lib.property
        name = "note"
        type = "string"
        desc = "An optional note for the substitute, at most 255 characters." />

    <@lib.property
        name = "effectiveSubstituteId"
        type = "string"
        last = true
        desc = "While the absence is active: who gets the tasks now, which is the substitute or, if that user is
                absent too, their substitute. Ignored in requests." />

</@lib.dto>
</#macro>
