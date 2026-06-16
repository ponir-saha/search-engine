#!/usr/bin/env bash
set -euo pipefail

APP_PORT="${APP_PORT:-8082}"
CONNECTOR_NAME="products-connector"
CONNECTOR_FILE="deploy/debezium/products-connector.json"
LOCAL_HTTP_HOSTS="${LOCAL_HTTP_HOSTS:-127.0.0.1 localhost 0.0.0.0}"
KAFKA_CONNECT_BASE_URL="${KAFKA_CONNECT_BASE_URL:-}"
READY_HTTP_BASE=""

if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env"
  set +a
fi

wait_for_local_http() {
  local name="$1"
  local port="$2"
  local path="$3"
  local attempts="${4:-60}"

  printf 'Waiting for %s' "$name"
  for _ in $(seq 1 "$attempts"); do
    for host in ${LOCAL_HTTP_HOSTS}; do
      local url="http://${host}:${port}${path}"
      if curl -fsS "$url" >/dev/null 2>&1; then
        READY_HTTP_BASE="http://${host}:${port}"
        printf ' ready (%s)\n' "${READY_HTTP_BASE}"
        return 0
      fi
    done
    printf '.'
    sleep 2
  done

  printf '\n%s did not become ready on port %s%s\n' "$name" "$port" "$path" >&2
  return 1
}

wait_for_postgres() {
  local attempts="${1:-60}"

  printf 'Waiting for Postgres'
  for _ in $(seq 1 "$attempts"); do
    if docker compose exec -T postgres pg_isready -U "${POSTGRES_USER:-pguser}" -d "${POSTGRES_DB:-products_db}" >/dev/null 2>&1; then
      printf ' ready\n'
      return 0
    fi
    printf '.'
    sleep 2
  done

  printf '\nPostgres did not become ready\n' >&2
  return 1
}

wait_for_kafka() {
  local attempts="${1:-60}"

  printf 'Waiting for Kafka'
  for _ in $(seq 1 "$attempts"); do
    if docker compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1; then
      printf ' ready\n'
      return 0
    fi
    printf '.'
    sleep 2
  done

  printf '\nKafka did not become ready\n' >&2
  return 1
}

register_connector() {
  if [[ -z "${KAFKA_CONNECT_BASE_URL}" ]]; then
    KAFKA_CONNECT_BASE_URL="http://127.0.0.1:8083"
  fi

  if curl -fsS "${KAFKA_CONNECT_BASE_URL}/connectors/${CONNECTOR_NAME}/status" >/dev/null 2>&1; then
    echo "Debezium connector ${CONNECTOR_NAME} already exists."
    curl -fsS "${KAFKA_CONNECT_BASE_URL}/connectors/${CONNECTOR_NAME}/status"
    echo
    return 0
  fi

  echo "Registering Debezium connector ${CONNECTOR_NAME}..."
  local response_file
  response_file="$(mktemp)"
  local http_status
  http_status="$(curl -sS -o "${response_file}" -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    --data @"${CONNECTOR_FILE}" \
    "${KAFKA_CONNECT_BASE_URL}/connectors")"

  if [[ "${http_status}" != "200" && "${http_status}" != "201" && "${http_status}" != "409" ]]; then
    echo "Debezium connector registration failed with HTTP ${http_status}:" >&2
    cat "${response_file}" >&2
    echo >&2
    rm -f "${response_file}"
    return 1
  fi

  cat "${response_file}"
  echo
  rm -f "${response_file}"
}

if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  echo "OPENAI_API_KEY is configured for this run."
else
  echo "OPENAI_API_KEY is not configured. Semantic vector sync will be disabled." >&2
fi

mkdir -p logs

export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:-http://127.0.0.1:4317}"
export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc}"

echo "Starting local services..."
docker compose up -d postgres opensearch zookeeper kafka connect weaviate prometheus loki promtail tempo otel-collector grafana

wait_for_postgres
if ! wait_for_kafka 30; then
  echo "Restarting ZooKeeper, Kafka, and Kafka Connect to clear stale local broker state..."
  docker compose restart zookeeper kafka connect
  wait_for_kafka
fi
wait_for_local_http "OpenSearch" 9200 "/_cluster/health"
wait_for_local_http "Kafka Connect" 8083 "/connectors"
KAFKA_CONNECT_BASE_URL="${KAFKA_CONNECT_BASE_URL:-${READY_HTTP_BASE}}"
wait_for_local_http "Weaviate" 8085 "/v1/meta"
wait_for_local_http "Prometheus" 9090 "/-/ready"
wait_for_local_http "Grafana" 3000 "/api/health"
wait_for_local_http "Loki" 3100 "/ready"
wait_for_local_http "Tempo" 3200 "/ready"

register_connector

echo "Starting Spring Boot on port ${APP_PORT}..."
if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"${APP_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port ${APP_PORT} is already in use. Stop the old app first:" >&2
  lsof -nP -iTCP:"${APP_PORT}" -sTCP:LISTEN >&2
  exit 1
fi

exec ./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=${APP_PORT}"
