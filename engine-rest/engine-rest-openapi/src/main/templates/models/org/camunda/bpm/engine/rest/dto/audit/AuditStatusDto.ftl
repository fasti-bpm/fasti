<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "enabled"
        type = "boolean"
        nullable = false
        desc = "Whether the audit trail runs on the engine." />

    <@lib.property
        name = "nodeId"
        type = "string"
        desc = "This node in the chain ids." />

    <@lib.property
        name = "chains"
        type = "integer"
        format = "int32"
        nullable = false
        desc = "Chains this node writes to in parallel." />

    <@lib.property
        name = "types"
        type = "array"
        desc = "The categories that are recorded." />

    <@lib.property
        name = "variableValues"
        type = "string"
        desc = "How variable values are recorded: `hash`, `value` or `none`." />

    <@lib.property
        name = "maskedVariables"
        type = "array"
        desc = "Name patterns of variables whose values are never recorded." />

    <@lib.property
        name = "signed"
        type = "boolean"
        nullable = false
        desc = "Whether checkpoints are signed with an HMAC key." />

    <@lib.property
        name = "keyId"
        type = "string"
        desc = "The first characters of the SHA-256 of the key, to tell keys apart." />

    <@lib.property
        name = "checkpointInterval"
        type = "integer"
        format = "int32"
        nullable = false
        desc = "Records between two checkpoints of a chain; 0 for none." />

    <@lib.property
        name = "retentionDays"
        type = "integer"
        format = "int32"
        nullable = false
        desc = "Records younger than this many days cannot be purged." />

    <@lib.property
        name = "historyLevel"
        type = "string"
        last = true
        desc = "The history level of the engine, which decides which events it produces." />

</@lib.dto>
</#macro>
