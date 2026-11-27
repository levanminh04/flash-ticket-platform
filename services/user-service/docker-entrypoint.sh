#!/bin/sh
set -eu
SERVICE_NAME="${SERVICE_NAME:?set by image}"
SERVICE_VERSION="${SERVICE_VERSION:-local-dev}"
INSTANCE_ID="$(cat /proc/sys/kernel/random/uuid)"
ENVIRONMENT="${ENVIRONMENT:-local}"
HOST_ID="${HOST_ID:-local-test}"
export SERVICE_NAME SERVICE_VERSION INSTANCE_ID ENVIRONMENT HOST_ID
case ",${OTEL_RESOURCE_ATTRIBUTES:-}," in
  *,service.name=*|*,service.version=*|*,service.instance.id=*|*,host.id=*|*,deployment.environment=*)
    echo "Use SERVICE_VERSION/ENVIRONMENT/HOST_ID for resource identity" >&2; exit 64 ;;
esac
OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:+$OTEL_RESOURCE_ATTRIBUTES,}service.name=$SERVICE_NAME,service.version=$SERVICE_VERSION,service.instance.id=$INSTANCE_ID,deployment.environment=$ENVIRONMENT,host.id=$HOST_ID"
export OTEL_RESOURCE_ATTRIBUTES
case "${JAVA_TOOL_OPTIONS:-}" in *-javaagent:*) echo "Duplicate Java agent configuration" >&2; exit 64 ;; esac
case "${OTEL_AGENT_ENABLED:-false}" in
  true)
    export OTEL_LOGS_EXPORTER=none
    exec java -javaagent:/otel/opentelemetry-javaagent.jar -jar /app/app.jar "$@" ;;
  false) exec java -jar /app/app.jar "$@" ;;
  *) echo "OTEL_AGENT_ENABLED must be true or false" >&2; exit 64 ;;
esac
