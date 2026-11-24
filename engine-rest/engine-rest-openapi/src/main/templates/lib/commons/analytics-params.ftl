<#assign params = {
  "processDefinitionIdIn": {
    "type": "string",
    "desc": "Only these versions of the process, comma separated: at most 200 ids of the process. All versions by
             default. An id of another process is refused."
  },
  "tenantIdIn": {
    "type": "string",
    "desc": "Only the history of these tenants, comma separated: at most 200 ids."
  },
  "finishedAfter": {
    "type": "string",
    "format": "date-time",
    "desc": "The range covers what finished at or after this time. 30 days before `finishedBefore` by default."
  },
  "finishedBefore": {
    "type": "string",
    "format": "date-time",
    "desc": "The range covers what finished before this time. Now by default."
  },
  "targetDuration": {
    "type": "integer",
    "format": "int64",
    "desc": "A target duration in milliseconds: every duration statistics also counts how many stayed within it
             (`withinTarget`)."
  }
}>
