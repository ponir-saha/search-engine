#!/usr/bin/env bash
set -euo pipefail

APP_PORT="${APP_PORT:-8082}"
CONNECTOR_NAME="products-connector"
CONNECTOR_FILE="deploy/debezium/products-connector.json"

if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env"
  set +a
fi

wait_for_http() {
  local name="$1"
  local url="$2"
  local attempts="${3:-60}"

  printf 'Waiting for %s' "$name"
  for _ in $(seq 1 "$attempts"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      printf ' ready\n'
      return 0
    fi
    printf '.'
    sleep 2
  done

  printf '\n%s did not become ready at %s\n' "$name" "$url" >&2
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

if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  echo "OPENAI_API_KEY is configured for this run."
else
  echo "OPENAI_API_KEY is not configured. Semantic vector sync will be disabled." >&2
fi

mkdir -p logs

echo "Starting local services..."
docker compose up -d postgres opensearch zookeeper kafka connect weaviate prometheus loki promtail tempo otel-collector grafana

wait_for_postgres
wait_for_http "OpenSearch" "http://localhost:9200/_cluster/health"
wait_for_http "Kafka Connect" "http://localhost:8083/connectors"
wait_for_http "Weaviate" "http://localhost:8085/v1/meta"
wait_for_http "Prometheus" "http://localhost:9090/-/ready"
wait_for_http "Grafana" "http://localhost:3000/api/health"
wait_for_http "Loki" "http://localhost:3100/ready"
wait_for_http "Tempo" "http://localhost:3200/ready"

if curl -fsS "http://localhost:8083/connectors/${CONNECTOR_NAME}" >/dev/null 2>&1; then
  echo "Debezium connector ${CONNECTOR_NAME} already exists."
else
  echo "Registering Debezium connector ${CONNECTOR_NAME}..."
  curl -fsS -X POST \
    -H "Content-Type: application/json" \
    --data @"${CONNECTOR_FILE}" \
    "http://localhost:8083/connectors" >/dev/null
fi

echo "Starting Spring Boot on port ${APP_PORT}..."
if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"${APP_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port ${APP_PORT} is already in use. Stop the old app first:" >&2
  lsof -nP -iTCP:"${APP_PORT}" -sTCP:LISTEN >&2
  exit 1
fi

exec ./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=${APP_PORT}"
