<#macro dto_macro docsUrl="">
<@lib.dto>

    <@lib.property
        name = "id"
        type = "string"
        desc = "The id of the earlier user task in the model." />

    <@lib.property
        name = "name"
        type = "string"
        last = true
        desc = "The name of the user task in the model, if it has one." />

</@lib.dto>
</#macro>
