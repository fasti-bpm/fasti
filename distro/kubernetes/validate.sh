#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright Fasti BPM contributors
#
# Checks the Helm chart and the plain manifests without a cluster: helm lint, helm template with
# every value set in helm/fasti-bpm/ci, cases that must be refused, and kubeconform (strict) of
# the rendered chart and of manifests/ against several Kubernetes versions. Helm and
# kubeconform run from container images pinned by digest; only Docker is needed.
#
#   distro/kubernetes/validate.sh [output directory]
#
# Environment: HELM_IMAGE, KUBECONFORM_IMAGE, K8S_VERSIONS (space-separated).

set -euo pipefail

HELM_IMAGE=${HELM_IMAGE:-alpine/helm:3.22.1@sha256:8191701ad1f82ee3695c87211fcbfe6d7b1bb7df92ac1c8de9ccdb0995a6a2c1}
KUBECONFORM_IMAGE=${KUBECONFORM_IMAGE:-ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e}
K8S_VERSIONS=${K8S_VERSIONS:-1.29.0 1.30.0 1.31.0 1.32.0 1.33.0}

here=$(cd "$(dirname "$0")" && pwd)
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
echo "== $(helm version --short)"
for values in "$here"/helm/fasti-bpm/ci/*-values.yaml; do
  name=$(basename "$values" -values.yaml)
  echo "== helm lint --strict ($name)"
  helm lint --strict /chart -f "/chart/ci/$name-values.yaml" || fail=1
  echo "== helm template ($name)"
  helm template fasti /chart --namespace fasti -f "/chart/ci/$name-values.yaml" \
    > "$out/rendered/$name.yaml" || fail=1
done

# combinations the chart must refuse
refuse() {
  local why=$1 message; shift
  if message=$(helm template fasti /chart "$@" 2>&1 > /dev/null); then
    echo "FAIL: rendered although it must be refused: $why"
    fail=1
  else
    echo "ok: refused: $why: $(printf '%s' "$message" | grep -m1 -i 'error' || true)"
  fi
}
db=(--set database.url=jdbc:postgresql://db:5432/fasti --set database.existingSecret=fasti-db)
helm template fasti /chart "${db[@]}" > /dev/null || { echo "FAIL: the base case of the refusal checks does not render"; fail=1; }
refuse "no database" --set database.existingSecret=fasti-db
refuse "no database secret" --set database.url=jdbc:postgresql://db:5432/fasti
refuse "admin without secret" "${db[@]}" --set admin.user=admin
refuse "OIDC without issuer" "${db[@]}" --set oidc.enabled=true
refuse "insecure demo in production" "${db[@]}" --set 'extraEnv[0].name=FASTI_INSECURE_DEMO' --set-string 'extraEnv[0].value=true'
refuse "unknown value (schema)" "${db[@]}" --set replicas=2
refuse "malformed digest (schema)" "${db[@]}" --set image.digest=latest
refuse "neither webapps nor rest" "${db[@]}" --set components.webapps=false --set components.rest=false

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
    -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' \
    /out/rendered "${manifests[@]}" || fail=1
done

if [ "$fail" -ne 0 ]; then
  echo "validation FAILED (rendered files in $out/rendered)"
  exit 1
fi
echo "validation passed (rendered files in $out/rendered)"
