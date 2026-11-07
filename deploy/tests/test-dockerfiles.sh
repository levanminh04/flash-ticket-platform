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

cleanup_dummy() {
  for row in $(jq -r '.services[] | select(.name != "keycloak") | @base64' "$root/.github/services.json"); do
    _svc=$(echo "$row" | base64 --decode | jq -r .name)
    _p=$(echo "$row" | base64 --decode | jq -r .path)
    rm -f "$root/$_p/target/${_svc}-dummy.jar"
    rmdir "$root/$_p/target" 2>/dev/null || true
  done
}
trap cleanup_dummy EXIT

# Verify build context does not block target/*.jar
for row in $(jq -r '.services[] | select(.name != "keycloak") | @base64' "$root/.github/services.json"); do
  svc=$(echo "$row" | base64 --decode | jq -r .name)
  path=$(echo "$row" | base64 --decode | jq -r .path)
  ign="$root/$path/.dockerignore"
  if [[ -f "$ign" ]]; then
    grep -q '!target/' "$ign" || fail "$path/.dockerignore must not ignore target/"
    grep -qE '!target/(\*|[a-z0-9_-]+-\*)\.jar' "$ign" || fail "$path/.dockerignore must allow target JAR"
  fi
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    mkdir -p "$root/$path/target"
    touch "$root/$path/target/${svc}-dummy.jar"
    docker build --quiet -f "$root/$path/Dockerfile" "$root/$path" >/dev/null || fail "$path: docker build failed (context may block target/*.jar)"
    rm -f "$root/$path/target/${svc}-dummy.jar"
    rmdir "$root/$path/target" 2>/dev/null || true
  fi
done

echo "test-dockerfiles OK"
