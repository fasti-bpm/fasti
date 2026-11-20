<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "chainId"
        type = "string"
        desc = "The chain." />

    <@lib.property
        name = "intact"
        type = "boolean"
        nullable = false
        desc = "Whether the chain verified." />

    <@lib.property
        name = "records"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Records read." />

    <@lib.property
        name = "firstSequence"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "The position of the first record." />

    <@lib.property
        name = "lastSequence"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "The position of the last record." />

    <@lib.property
        name = "lastHash"
        type = "string"
        desc = "The hash of the last record; keep it outside the database to detect later truncation." />

    <@lib.property
        name = "checkpoints"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Checkpoints in the chain." />

    <@lib.property
        name = "checkpointsVerified"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Checkpoints whose HMAC was checked with the configured key." />

    <@lib.property
        name = "purgedThrough"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Position up to which records were purged; 0 if none." />

    <@lib.property
        name = "firstBreak"
        type = "ref"
        dto = "AuditBreakDto"
        last = true
        desc = "The first break." />

</@lib.dto>
</#macro>
