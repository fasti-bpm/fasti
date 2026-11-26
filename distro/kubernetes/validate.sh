#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright Fasti BPM contributors
#
# Checks the Helm chart and the plain manifests without a cluster: the versions that follow a
# release, helm lint, helm template with every value set in helm/fasti-bpm/ci, cases that must
# be refused, and kubeconform (strict) of the rendered chart and of manifests/ against several
# Kubernetes versions. Helm and kubeconform run from container images pinned by digest, the
# CRD schemas come from a pinned commit; only Docker is needed.
#
#   distro/kubernetes/validate.sh [output directory]
#   distro/kubernetes/validate.sh --versions     only the version check (no Docker)
#
# Environment: HELM_IMAGE, KUBECONFORM_IMAGE, K8S_VERSIONS (space-separated), CRD_CATALOG_REF.

set -euo pipefail

HELM_IMAGE=${HELM_IMAGE:-alpine/helm:3.22.1@sha256:8191701ad1f82ee3695c87211fcbfe6d7b1bb7df92ac1c8de9ccdb0995a6a2c1}
KUBECONFORM_IMAGE=${KUBECONFORM_IMAGE:-ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e}
K8S_VERSIONS=${K8S_VERSIONS:-1.29.0 1.30.0 1.31.0 1.32.0 1.33.0}
# commit of https://github.com/datreeio/CRDs-catalog with the schemas of the custom resources
# (ServiceMonitor); a branch name would let the schemas change under a pinned kubeconform
CRD_CATALOG_REF=${CRD_CATALOG_REF:-784ec895547a36e548e6810b41f27baade3a0890}

here=$(cd "$(dirname "$0")" && pwd)

# The chart appVersion (default image tag), the image tag of the plain manifests and the default
# VERSION of the Dockerfile must be the version of the POMs: a release that forgets one of them
# would deploy or label the previous version.
check_versions() {
  local pom chart manifest dockerfile bad=0
  pom=$(grep -m1 -o '^  <version>[^<]*' "$here/../../pom.xml" | sed 's/.*>//')
  chart=$(grep -m1 '^appVersion:' "$here/helm/fasti-bpm/Chart.yaml" | sed 's/^appVersion://' | tr -d '"[:space:]')
  manifest=$(grep -m1 -o 'image: fastidev/fasti-bpm:[^@[:space:]]*' "$here/manifests/deployment.yaml" | sed 's/.*://')
  dockerfile=$(grep -m1 -o '^ARG VERSION=[^[:space:]]*' "$here/../docker/Dockerfile" | sed 's/.*=//')
  if [ -z "$pom" ]; then
    echo "FAIL: no version found in the root pom.xml"
    return 1
  fi
  [ "$chart" = "$pom" ] || { echo "FAIL: appVersion '$chart' of helm/fasti-bpm/Chart.yaml is not the POM version $pom"; bad=1; }
  [ "$manifest" = "$pom" ] || { echo "FAIL: image tag '$manifest' of manifests/deployment.yaml is not the POM version $pom"; bad=1; }
  [ "$dockerfile" = "$pom" ] || { echo "FAIL: ARG VERSION '$dockerfile' of distro/docker/Dockerfile is not the POM version $pom"; bad=1; }
  [ "$bad" -eq 0 ] && echo "ok: chart appVersion, manifest image tag and Dockerfile VERSION are $pom"
  return "$bad"
}
if [ "${1:-}" = "--versions" ]; then
  check_versions
  exit
fi

out=${1:-$(mktemp -d)}
mkdir -p "$out/rendered" "$out/cache"
out=$(cd "$out" && pwd)

# Docker Desktop on Windows (Git Bash) needs Windows paths for bind mounts
export MSYS_NO_PATHCONV=1
hostpath() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }
chart_dir=$(hostpath "$here/helm/fasti-bpm")
manifests_dir=$(hostpath "$here/manifests")
out_dir=$(hostpath "$out")

helm() {
  docker run --rm -v "$chart_dir:/chart:ro" -v "$out_dir:/out" -w /out "$HELM_IMAGE" "$@"
}

fail=0
check_versions || fail=1
echo "== $(helm version --short)"
for values in "$here"/helm/fasti-bpm/ci/*-values.yaml; do
  name=$(basename "$values" -values.yaml)
  echo "== helm lint --strict ($name)"
  helm lint --strict /chart -f "/chart/ci/$name-values.yaml" || fail=1
  echo "== helm template ($name)"
  helm template fasti /chart --namespace fasti -f "/chart/ci/$name-values.yaml" \
    > "$out/rendered/$name.yaml" || fail=1
done

# combinations the chart must refuse, each with (a part of) the message it must be refused with:
# any other failure (Docker, the image pull, a broken template) is not a refusal
refuse() {
  local why=$1 expected=$2 message; shift 2
  if message=$(helm template fasti /chart "$@" 2>&1 > /dev/null); then
    echo "FAIL: rendered although it must be refused: $why"
    fail=1
  elif printf '%s' "$message" | grep -Fqi -- "$expected"; then
    echo "ok: refused: $why: $(printf '%s' "$message" | grep -m1 -Fi -- "$expected")"
  else
    echo "FAIL: failed, but not with the expected message '$expected': $why"
    printf '%s\n' "$message" | sed 's/^/    /'
    fail=1
  fi
}
db=(--set database.url=jdbc:postgresql://db:5432/fasti --set database.existingSecret=fasti-db)
helm template fasti /chart "${db[@]}" > /dev/null || { echo "FAIL: the base case of the refusal checks does not render"; fail=1; }
refuse "no database" "database.url (or database.urlKey) is required" --set database.existingSecret=fasti-db
refuse "no database secret" "database.existingSecret is required" --set database.url=jdbc:postgresql://db:5432/fasti
refuse "admin without secret" "admin.user needs admin.existingSecret" "${db[@]}" --set admin.user=admin
refuse "OIDC without issuer" "oidc.enabled needs oidc.issuerUri" "${db[@]}" --set oidc.enabled=true
refuse "insecure demo in production" "FASTI_INSECURE_DEMO=true cannot be combined with production" "${db[@]}" --set 'extraEnv[0].name=FASTI_INSECURE_DEMO' --set-string 'extraEnv[0].value=true'
refuse "neither webapps nor rest" "components: enable webapps, rest or both" "${db[@]}" --set components.webapps=false --set components.rest=false
refuse "management port in extraEnv" "set management.port instead" "${db[@]}" --set 'extraEnv[0].name=FASTI_MANAGEMENT_PORT' --set-string 'extraEnv[0].value=9464'
refuse "management port in extraArgs" "set management.port instead" "${db[@]}" --set 'extraArgs[0]=--management.server.port=9464'
refuse "management port equal to the service port" "management.port and service.port must differ" "${db[@]}" --set management.port=9464 --set service.port=9464
refuse "ServiceMonitor port the Service does not have" "is not a port of the Service" "${db[@]}" --set metrics.serviceMonitor.enabled=true --set metrics.serviceMonitor.port=management
refuse "ServiceMonitor port other than the extra port" "is not a port of the Service" "${db[@]}" --set metrics.serviceMonitor.enabled=true --set metrics.serviceMonitor.port=other --set metrics.extraPort.enabled=true
# refused by values.schema.json: the message of Helm names the value
refuse "unknown value (schema)" "replicas" "${db[@]}" --set replicas=2
refuse "malformed digest (schema)" "digest" "${db[@]}" --set image.digest=latest
refuse "management port 8080 (schema)" "management" "${db[@]}" --set management.port=8080
refuse "extra port named like the HTTP port (schema)" "extraPort" "${db[@]}" --set metrics.extraPort.enabled=true --set metrics.extraPort.name=http
refuse "PDB value that is neither a number nor a percentage (schema)" "maxUnavailable" "${db[@]}" --set-string pdb.maxUnavailable=half
refuse "pool size 0 as a string (schema)" "maxActive" "${db[@]}" --set-string database.pool.maxActive=0

# rendered output of ci/edge-values.yaml
expect() {
  local what=$1 pattern=$2
  if grep -Eq -- "$pattern" "$out/rendered/edge.yaml"; then echo "ok: $what"; else echo "FAIL: $what"; fail=1; fi
}
expect_not() {
  local what=$1 pattern=$2
  if grep -Eq -- "$pattern" "$out/rendered/edge.yaml"; then echo "FAIL: $what"; fail=1; else echo "ok: $what"; fi
}
expect "Recreate strategy" '^    type: Recreate$'
expect_not "no rollingUpdate with Recreate" 'rollingUpdate:'
expect "PDB maxUnavailable 0 kept" '^  maxUnavailable: 0$'
expect "pool minIdle 0 kept" 'name: FASTI_DB_CONN_MINIDLE'
expect "helm test pod admitted by the NetworkPolicy" '^        - podSelector:$'
expect "management container port" '^              containerPort: 9464$'
expect "FASTI_MANAGEMENT_PORT set" 'name: FASTI_MANAGEMENT_PORT'
expect "probes on the management port" '^              port: management$'
expect_not "no probe left on the HTTP port" '^              port: http$'
expect "management port of the Service" '^      targetPort: management$'
expect "management port admitted by the NetworkPolicy" '^        - port: management$'
expect "helm test calls the management port" ':9464/actuator/health/readiness'
expect "ServiceMonitor on the management port" '^    - port: "management"$'
if grep -A1 'name: FASTI_MAX_RAM_PERCENTAGE' "$out/rendered/edge.yaml" | grep -q 'value: "70"'; then
  echo "ok: heap share passed when set"
else
  echo "FAIL: heap share passed when set"; fail=1
fi
# unset (the default), the image chooses 75, or 60 below a 1.5 GiB limit
if grep -q 'name: FASTI_MAX_RAM_PERCENTAGE' "$out/rendered/minimal.yaml"; then
  echo "FAIL: no FASTI_MAX_RAM_PERCENTAGE by default"; fail=1
else
  echo "ok: no FASTI_MAX_RAM_PERCENTAGE by default"
fi

manifests=()
for file in "$here"/manifests/*.yaml; do
  case "$(basename "$file")" in kustomization.yaml) continue ;; esac
  manifests+=("/manifests/$(basename "$file")")
done
for version in $K8S_VERSIONS; do
  echo "== kubeconform -strict, Kubernetes $version"
  docker run --rm -v "$out_dir:/out" -v "$manifests_dir:/manifests:ro" "$KUBECONFORM_IMAGE" \
    -strict -summary -kubernetes-version "$version" -cache /out/cache \
    -schema-location default \
    -schema-location "https://raw.githubusercontent.com/datreeio/CRDs-catalog/${CRD_CATALOG_REF}/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json" \
    /out/rendered "${manifests[@]}" || fail=1
done

if [ "$fail" -ne 0 ]; then
  echo "validation FAILED (rendered files in $out/rendered)"
  exit 1
fi
echo "validation passed (rendered files in $out/rendered)"
