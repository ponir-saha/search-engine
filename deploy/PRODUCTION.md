# Production Runbook

This service is production-ready only when it runs against managed or separately operated infrastructure. Docker Compose and `run-app.sh` are for local development.

## Runtime Profile

Use the `prod` Spring profile:

```bash
SPRING_PROFILES_ACTIVE=prod
```

The production profile disables startup product bootstrapping. Product data should arrive through the source database and CDC pipeline, with `/api/products/sync` kept as a repair operation.

## Required Configuration

Set these values from your platform secret/config system:

```bash
SPRING_PROFILES_ACTIVE=prod
JDBC_URL=jdbc:postgresql://<postgres-host>:5432/products_db
R2DBC_URL=r2dbc:postgresql://<postgres-host>:5432/products_db
POSTGRES_USER=<secret>
POSTGRES_PASSWORD=<secret>
KAFKA_BOOTSTRAP_SERVERS=<broker-1>:9092,<broker-2>:9092
APP_KAFKA_TOPIC=dbserver1.public.products
APP_KAFKA_GROUP_ID=search-engine-group
APP_KAFKA_DLQ_TOPIC=dbserver1.public.products.dlq
OPENSEARCH_URL=https://<opensearch-host>
VECTORDB_URL=https://<weaviate-host>
OPENAI_API_KEY=<secret>
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://<otel-collector>:4318/v1/traces
```

## Database

Schema changes are managed by Flyway migrations in `src/main/resources/db/migration`.

Do not rely on application-side `CREATE TABLE` behavior as the production schema control mechanism. Keep migrations reviewed and applied by the service at startup or by your deployment process.

## CDC And Indexing

The Kafka consumer indexes CDC events into OpenSearch and Weaviate. Production behavior:

- Indexing failures are retried with bounded backoff.
- Failed messages are published to `APP_KAFKA_DLQ_TOPIC`.
- Kafka producer idempotence is enabled for DLQ publishing.
- Consumer group id, topic, retry count, retry interval, and concurrency are configurable.

Monitor the DLQ. Any message in the DLQ means a product search document or vector may be stale.

## Health And Observability

Expose these endpoints internally:

```text
/actuator/health
/actuator/prometheus
```

In production, health details are hidden and tracing samples at 10% by default. Tune `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` per environment.

Important metrics to alert on:

- HTTP 5xx rate
- API latency p95/p99
- Kafka consumer lag
- DLQ publish rate
- OpenSearch indexing errors
- Weaviate indexing errors
- OpenAI embedding failures and latency

## Container

Build:

```bash
docker build -t search-engine:latest .
```

Run:

```bash
docker run --rm -p 8082:8082 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JDBC_URL=jdbc:postgresql://postgres:5432/products_db \
  -e R2DBC_URL=r2dbc:postgresql://postgres:5432/products_db \
  -e POSTGRES_USER=pguser \
  -e POSTGRES_PASSWORD=pgpass \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e OPENSEARCH_URL=http://opensearch:9200 \
  -e VECTORDB_URL=http://weaviate:8080 \
  -e OPENAI_API_KEY=replace-me \
  search-engine:latest
```

The container runs as a non-root user and uses JVM container memory limits.

## Security Notes

Before public exposure, place this API behind an API gateway or ingress that provides:

- TLS termination
- Authentication
- Rate limiting
- Request size limits
- Admin-only access for product CRUD and sync/reindex endpoints

Do not expose Kafka Connect, OpenSearch, Weaviate, Postgres, Grafana, Loki, Prometheus, or Tempo directly to the public internet.
