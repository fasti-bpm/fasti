<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "intact"
        type = "boolean"
        nullable = false
        desc = "Whether every chain verified: its hashes are consistent from its first record to its head and,
                with a signing key, every checkpoint and every purge record that explains a removed start carries
                a valid signature (or one the configuration lists as retired) and no checkpoint is missing." />

    <@lib.property
        name = "authenticated"
        type = "boolean"
        nullable = false
        desc = "Whether every chain is intact and every signature it needs was checked with a key the engine
                holds. Only this rules out that someone with write access to the database rewrote a chain or
                removed its start. Always `false` without a signing key, and for chains with signatures that are
                accepted unchecked (`fasti.audit.retired-key-ids`) or with a removed start that no signed purge
                record names." />

    <@lib.property
        name = "verifiedAt"
        type = "string"
        format = "date-time"
        desc = "When the verification ran." />

    <@lib.property
        name = "records"
        type = "integer"
        format = "int64"
        nullable = false
        desc = "Records read in all." />

    <@lib.property
        name = "chains"
        type = "array"
        dto = "AuditChainDto"
        desc = "The verification of each chain." />

    <@lib.property
        name = "firstBreak"
        type = "ref"
        dto = "AuditBreakDto"
        last = true
        desc = "The first break of the first broken chain, or `null`." />

</@lib.dto>
</#macro>
