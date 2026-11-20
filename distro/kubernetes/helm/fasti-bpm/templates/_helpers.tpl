{{/*
SPDX-License-Identifier: Apache-2.0
Copyright Fasti BPM contributors
*/}}

{{- define "fasti.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "fasti.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "fasti.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "fasti.selectorLabels" -}}
app.kubernetes.io/name: {{ include "fasti.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "fasti.labels" -}}
helm.sh/chart: {{ include "fasti.chart" . }}
{{ include "fasti.selectorLabels" . }}
app.kubernetes.io/version: {{ include "fasti.imageTag" . | trunc 63 | trimSuffix "-" | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: fasti-bpm
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{- define "fasti.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "fasti.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "fasti.imageTag" -}}
{{- default .Chart.AppVersion .Values.image.tag -}}
{{- end -}}

{{/* repository:tag, repository:tag@digest */}}
{{- define "fasti.image" -}}
{{- $ref := printf "%s:%s" .Values.image.repository (include "fasti.imageTag" .) -}}
{{- if .Values.image.digest -}}
{{- if not (regexMatch "^sha256:[a-f0-9]{64}$" .Values.image.digest) -}}
{{- fail "image.digest must look like sha256:<64 hex characters>" -}}
{{- end -}}
{{- $ref = printf "%s@%s" $ref .Values.image.digest -}}
{{- end -}}
{{- $ref -}}
{{- end -}}

{{/* whether a ConfigMap with configuration files is rendered */}}
{{- define "fasti.hasConfig" -}}
{{- if or .Values.config .Values.oidc.enabled -}}true{{- end -}}
{{- end -}}

{{/* checks of combinations the schema cannot express */}}
{{- define "fasti.validate" -}}
{{- if and (not .Values.database.url) (not .Values.database.urlKey) -}}
{{- fail "database.url (or database.urlKey) is required: the chart only supports an external database" -}}
{{- end -}}
{{- if not .Values.database.existingSecret -}}
{{- fail "database.existingSecret is required: name an existing Secret with the database username and password" -}}
{{- end -}}
{{- if and .Values.admin.user (not .Values.admin.existingSecret) -}}
{{- fail "admin.user needs admin.existingSecret (a Secret with the administrator password)" -}}
{{- end -}}
{{- if and (not .Values.components.webapps) (not .Values.components.rest) -}}
{{- fail "components: enable webapps, rest or both" -}}
{{- end -}}
{{- if .Values.oidc.enabled -}}
{{- if not .Values.components.webapps -}}
{{- fail "oidc.enabled needs components.webapps" -}}
{{- end -}}
{{- if or (not .Values.oidc.issuerUri) (not .Values.oidc.clientId) (not .Values.oidc.existingSecret) -}}
{{- fail "oidc.enabled needs oidc.issuerUri, oidc.clientId and oidc.existingSecret" -}}
{{- end -}}
{{- if and .Values.oidc.rest.bearerEnabled (not .Values.oidc.rest.audiences) -}}
{{- fail "oidc.rest.bearerEnabled needs oidc.rest.audiences" -}}
{{- end -}}
{{- end -}}
{{- if and .Values.pdb.enabled .Values.pdb.maxUnavailable .Values.pdb.minAvailable -}}
{{- fail "pdb: set maxUnavailable or minAvailable, not both" -}}
{{- end -}}
{{- if and .Values.metrics.serviceMonitor.enabled (ne .Values.metrics.serviceMonitor.port "http") (not .Values.metrics.extraPort.enabled) -}}
{{- fail "metrics.serviceMonitor.port names a port other than http: enable metrics.extraPort" -}}
{{- end -}}
{{- range .Values.extraEnv -}}
{{- if has .name (list "FASTI_INSECURE_DEMO" "DEBUG" "FASTI_DEBUG") -}}
{{- if and $.Values.production (eq (toString .value | lower) "true") -}}
{{- fail (printf "%s=true cannot be combined with production: true" .name) -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/* arguments of the entrypoint */}}
{{- define "fasti.args" -}}
{{- $args := list -}}
{{- if .Values.components.webapps -}}{{- $args = append $args "--webapps" -}}{{- end -}}
{{- if .Values.components.rest -}}{{- $args = append $args "--rest" -}}{{- end -}}
{{- if .Values.components.example -}}{{- $args = append $args "--example" -}}{{- end -}}
{{- if .Values.oidc.enabled -}}{{- $args = append $args "--oauth2" -}}{{- end -}}
{{- if .Values.production -}}{{- $args = append $args "--production" -}}{{- end -}}
{{- range .Values.extraArgs -}}{{- $args = append $args (toString .) -}}{{- end -}}
{{- toYaml $args -}}
{{- end -}}

{{/* OpenID Connect configuration file */}}
{{- define "fasti.oidcConfig" -}}
{{- $o := .Values.oidc -}}
{{- $id := $o.registrationId -}}
{{- $registration := dict "client-id" $o.clientId "client-secret" "${FASTI_OIDC_CLIENT_SECRET}" "scope" $o.scope -}}
{{- if $o.clientName -}}{{- $_ := set $registration "client-name" $o.clientName -}}{{- end -}}
{{- $provider := dict "issuer-uri" $o.issuerUri "user-name-attribute" $o.userNameAttribute -}}
{{- $oauth2 := dict "identity-provider" (dict "enabled" $o.identityProvider) "sso-logout" (dict "enabled" $o.ssoLogout.enabled "post-logout-redirect-uri" $o.ssoLogout.postLogoutRedirectUri) -}}
{{- if $o.groupsClaim -}}{{- $_ := set (index $oauth2 "identity-provider") "group-name-attribute" $o.groupsClaim -}}{{- end -}}
{{- $groups := dict "admin" $o.adminGroups "include-unmapped" $o.includeUnmappedGroups -}}
{{- if $o.groupMapping -}}{{- $_ := set $groups "mapping" $o.groupMapping -}}{{- end -}}
{{- $fasti := dict "registration-id" $id "groups" $groups -}}
{{- if $o.clientName -}}{{- $_ := set $fasti "login-label" $o.clientName -}}{{- end -}}
{{- if $o.tenantsClaim -}}{{- $_ := set $fasti "tenants" (dict "claim" $o.tenantsClaim) -}}{{- end -}}
{{- if $o.rest.bearerEnabled -}}
{{- $rest := dict "bearer-enabled" true "issuer-uri" (default $o.issuerUri $o.rest.issuerUri) "audiences" $o.rest.audiences "user-id-claim" $o.rest.userIdClaim -}}
{{- if $o.rest.groupsClaim -}}{{- $_ := set $rest "groups-claim" $o.rest.groupsClaim -}}{{- end -}}
{{- $_ := set $fasti "rest" $rest -}}
{{- end -}}
{{- $doc := dict "spring" (dict "security" (dict "oauth2" (dict "client" (dict "registration" (dict $id $registration) "provider" (dict $id $provider))))) "camunda" (dict "bpm" (dict "oauth2" $oauth2)) "fasti" (dict "security" (dict "oidc" $fasti)) -}}
{{- toYaml $doc -}}
{{- end -}}

{{/* configuration files named in FASTI_CONFIG */}}
{{- define "fasti.configFiles" -}}
{{- $files := list -}}
{{- if .Values.oidc.enabled -}}{{- $files = append $files "/fasti/configuration/helm/oidc.yml" -}}{{- end -}}
{{- if .Values.config -}}{{- $files = append $files "/fasti/configuration/helm/fasti-config.yml" -}}{{- end -}}
{{- join "," $files -}}
{{- end -}}
