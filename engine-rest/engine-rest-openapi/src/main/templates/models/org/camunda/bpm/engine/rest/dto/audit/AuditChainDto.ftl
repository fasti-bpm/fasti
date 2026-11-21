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
        name = "authenticated"
        type = "boolean"
        nullable = false
        desc = "Whether the chain is intact and every checkpoint, and the purge record of a removed start, was
                checked with a key the engine holds. `false` without a signing key." />

    <@lib.property
        name = "authenticatedThrough"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "The position of the last record that a checked signature covers: the record before the last
                checkpoint of an authenticated chain. `0` if the chain is not authenticated or has no checkpoint
                yet. The records after it are protected by the hash chain and the head only." />

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
        desc = "Checkpoints whose HMAC was checked with the configured key or a retired key the engine holds." />

    <@lib.property
        name = "purgedThrough"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Position up to which records were purged; 0 if none." />

    <@lib.property
        name = "purgeAttested"
        type = "boolean"
        nullable = false
        desc = "Whether the purge record that names the removed start carries a signature that was checked.
                `false` without a signing key: the start may then have been removed by someone with write access
                to the database. Without a purge it has no meaning." />

    <@lib.property
        name = "firstBreak"
        type = "ref"
        dto = "AuditBreakDto"
        last = true
        desc = "The first break." />

</@lib.dto>
</#macro>
