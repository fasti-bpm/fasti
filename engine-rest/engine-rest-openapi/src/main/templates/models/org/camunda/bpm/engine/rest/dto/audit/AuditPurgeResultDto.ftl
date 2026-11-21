<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "before"
        type = "string"
        format = "date-time"
        desc = "The time given." />

    <@lib.property
        name = "removed"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Records removed in all." />

    <@lib.property
        name = "purgedThrough"
        type = "object"
        additionalProperties = true
        itemType = "integer"
        desc = "Per chain that lost records: the position of the last removed record." />

    <@lib.property
        name = "failedChains"
        type = "array"
        itemType = "string"
        last = true
        desc = "Chains whose purge failed (for example because another transaction held the chain for longer than
                `fasti.audit.lock-timeout-seconds`); the cause is in the server log. The other chains were purged, and
                what was purged of a failed chain before the failure is in `purgedThrough`. Empty when every chain
                was purged; purge again to complete the failed ones." />

</@lib.dto>
</#macro>
