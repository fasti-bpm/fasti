<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "id"
        type = "string"
        desc = "The id of the record." />

    <@lib.property
        name = "chainId"
        type = "string"
        desc = "The chain of the record: `<node id>/<slot>`." />

    <@lib.property
        name = "sequence"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "The position in the chain, from 1 without gaps." />

    <@lib.property
        name = "timestamp"
        type = "string"
        format = "date-time"
        desc = "When it happened, by the clock of the engine." />

    <@lib.property
        name = "time"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "The same time in milliseconds since 1970-01-01T00:00Z, as hashed." />

    <@lib.property
        name = "type"
        type = "string"
        desc = "The category: `process`, `task`, `variable`, `deployment`, `operation`, `incident`, `decision`, `activity` or `audit`." />

    <@lib.property
        name = "action"
        type = "string"
        desc = "What happened." />

    <@lib.property
        name = "actor"
        type = "string"
        desc = "The authenticated user, or `null` for the engine itself." />

    <@lib.property
        name = "engineName"
        type = "string"
        desc = "The process engine that wrote the record." />

    <@lib.property
        name = "tenantId"
        type = "string"
        desc = "The tenant." />

    <@lib.property
        name = "processDefinitionKey"
        type = "string"
        desc = "The key of the process definition." />

    <@lib.property
        name = "processDefinitionId"
        type = "string"
        desc = "The id of the process definition." />

    <@lib.property
        name = "processInstanceId"
        type = "string"
        desc = "The process instance." />

    <@lib.property
        name = "businessKey"
        type = "string"
        desc = "The business key of the process instance." />

    <@lib.property
        name = "entityType"
        type = "string"
        desc = "The kind of object the record is about." />

    <@lib.property
        name = "entityId"
        type = "string"
        desc = "The id of that object." />

    <@lib.property
        name = "entityName"
        type = "string"
        desc = "A readable name of that object (task name, variable name, property)." />

    <@lib.property
        name = "details"
        type = "string"
        desc = "Further facts as JSON text, exactly as hashed." />

    <@lib.property
        name = "previousHash"
        type = "string"
        desc = "SHA-256 (hex) of the previous record of the chain." />

    <@lib.property
        name = "hash"
        type = "string"
        last = true
        desc = "SHA-256 (hex) of the previous hash and the canonical form of this record." />

</@lib.dto>
</#macro>
