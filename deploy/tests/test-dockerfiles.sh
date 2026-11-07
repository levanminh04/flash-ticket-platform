#!/usr/bin/env bash
# All Spring services must share one byte-identical, digest-pinned runtime Dockerfile.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

ref="$root/infrastructure/discovery-server/Dockerfile"
grep -q 'eclipse-temurin:21-jre-alpine@sha256:' "$ref" || fail "base image must be pinned by digest"
grep -q 'COPY --chown=10001:10001 target/\*.jar' "$ref" || fail "Dockerfile must copy the prebuilt JAR"
for path in $(jq -r '.services[] | select(.name != "keycloak") | .path' "$root/.github/services.json"); do
  cmp -s "$ref" "$root/$path/Dockerfile" || fail "$path/Dockerfile differs from the template"
done
echo "test-dockerfiles OK"
