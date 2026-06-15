# Search Engine

A Spring Boot WebFlux product search service with product CRUD, OpenSearch lexical search, Weaviate vector search, OpenAI embeddings, and Debezium/Kafka CDC indexing.

## Current Features

- Product CRUD API backed by Postgres/R2DBC
- Static product search page at `/index.html`
- Static product CRUD page at `/products.html`
- Random product cards on the home page
- Search products by keyword with pagination
- Autocomplete suggestions, limited to 5 results
- Lexical search through OpenSearch
- Semantic vector search through Weaviate
- OpenAI `text-embedding-3-small` embeddings
- Startup bootstrap from `src/main/resources/data/products.tsv`
- Automatic reset and loading of 3000 product records on app startup
- Debezium Postgres CDC connector
- Kafka consumer that indexes product changes into OpenSearch and Weaviate
- One-command local runner through `./run-app.sh`

## Use Cases

- Search ecommerce products by name or description
- Find products by user intent, for example searching `mobile` to find phone products
- Find related device categories, for example searching `laptop` to find MacBook, Dell XPS, HP Spectre, and similar products
- Show live autocomplete suggestions while users type
- Manage products from a browser CRUD page
- Keep search stores updated from product database changes
- Start the app and have Postgres, OpenSearch, and Weaviate become ready without manual seed/reindex calls

## Tech Stack

- Java 21
- Spring Boot 4.1
- Spring WebFlux
- Spring Kafka
- R2DBC Postgres
- OpenSearch
- Weaviate
- OpenAI embeddings API
- Kafka, Zookeeper, Debezium Connect
- Spring Actuator, Micrometer, OpenTelemetry
- Prometheus, Grafana, Loki, Promtail, Tempo, OpenTelemetry Collector
- Docker Compose

## Services And Ports

| Service | Port | Purpose |
| --- | ---: | --- |
| Spring Boot app | `8082` | REST API and static pages |
| Postgres | `5432` | Product source database |
| OpenSearch | `9200` | Lexical product search |
| Kafka host listener | `9093` | App Kafka consumer connection |
| Kafka Connect | `8083` | Debezium connector API |
| Weaviate | `8085` | Vector database |
| Prometheus | `9090` | Metrics storage and query |
| Grafana | `3000` | Dashboards, logs, traces |
| Loki | `3100` | Log storage |
| Tempo | `3200` | Trace storage |
| OpenTelemetry Collector | `4317`, `4318` | OTLP trace receiver |

## API Endpoints

### Product CRUD

- `GET /api/products?page=0&size=10`
- `GET /api/products/{id}`
- `POST /api/products`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`

Product JSON:

```json
{
  "id": 1,
  "name": "Apple iPhone 15",
  "description": "Smartphone product",
  "price": 999.99
}
```

### Search And Suggestions

- `GET /api/products/search?q=mobile&page=0&size=10`
- `GET /api/products/suggestions?q=mobile&size=5`

### Bootstrap And Sync

- `POST /api/products/sync`
- `POST /api/products/reindex`
- `GET /api/products/semantic-status`

On startup, the app reads `src/main/resources/data/products.tsv`, clears previous product/search/vector data, inserts 3000 products into Postgres, and waits for CDC indexing to populate OpenSearch and Weaviate.

`/api/products/sync` and `/api/products/reindex` are still available as repair endpoints, but normal local startup does not require calling them manually.

## Run Locally After Clone

### Prerequisites

Install these first:

- Java 21
- Docker Desktop
- Git

The Maven wrapper is included, so a separate Maven install is not required.

### 1. Clone The Repository

```bash
git clone https://github.com/ponir-saha/search-engine.git
cd search-engine
```

### 2. Configure Environment

Create a local `.env` file:

```bash
cp .env.example .env
```

Edit `.env` and set your OpenAI key:

```bash
OPENAI_API_KEY=your-openai-api-key
```

`.env` is ignored by Git. Do not commit real API keys.

### 3. Start Everything

```bash
./run-app.sh
```

The script starts Docker services, waits for OpenSearch/Kafka Connect/Weaviate/observability tools, registers the Debezium connector if needed, and starts Spring Boot on port `8082`.

During Spring Boot startup, the application automatically:

1. Loads 3000 products from `src/main/resources/data/products.tsv`.
2. Truncates the existing Postgres `products` table.
3. Clears the OpenSearch `products` index.
4. Clears the Weaviate `Product` class.
5. Inserts the fresh product dataset into Postgres.
6. Waits until CDC/Kafka indexing catches OpenSearch and Weaviate up to the expected product count.

If port `8082` is already in use, stop the old Java process shown by the script and run it again.

### 4. Open The UI

- Search page: `http://localhost:8082/index.html`
- Product CRUD page: `http://localhost:8082/products.html`

### 5. Verify Semantic Search

```bash
curl "http://localhost:8082/api/products/semantic-status"
curl "http://localhost:8082/api/products/search?q=mobile&page=0&size=10"
curl "http://localhost:8082/api/products/search?q=laptop&page=0&size=10"
curl "http://localhost:8082/api/products/suggestions?q=mobile&size=5"
```

Check Weaviate product count:

```bash
curl -X POST "http://localhost:8085/v1/graphql" \
  -H "Content-Type: application/json" \
  -d '{"query":"{ Aggregate { Product { meta { count } } } }"}'
```

## Configuration

Important environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `OPENAI_API_KEY` | empty | OpenAI embeddings API key |
| `R2DBC_URL` | `r2dbc:postgresql://localhost:5432/products_db` | Postgres R2DBC URL |
| `POSTGRES_USER` | `pguser` | Postgres user |
| `POSTGRES_PASSWORD` | `pgpass` | Postgres password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9093` | Kafka bootstrap server for the app |
| `OPENSEARCH_URL` | `http://localhost:9200` | OpenSearch URL |
| `OPENSEARCH_INDEX_PRODUCTS` | `products` | Product index name |
| `APP_KAFKA_TOPIC` | `dbserver1.public.products` | Debezium product topic |
| `APP_BOOTSTRAP_ENABLED` | `true` | Run startup product dataset bootstrap |
| `APP_BOOTSTRAP_RESET` | `true` | Clear old Postgres/OpenSearch/Weaviate product data before inserting |
| `APP_BOOTSTRAP_DATASET` | `classpath:data/products.tsv` | Product dataset location |
| `APP_BOOTSTRAP_WAIT_FOR_INDEXES` | `true` | Wait for CDC indexing counts before startup finishes |
| `APP_BOOTSTRAP_WAIT_TIMEOUT` | `PT15M` | Max time to wait for OpenSearch/Weaviate counts |
| `VECTORDB_URL` | `http://localhost:8085` | Weaviate URL |
| `VECTORDB_TYPE` | `weaviate` | Vector database type |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | OpenTelemetry trace export endpoint |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `1.0` | Trace sample rate for local development |
| `LOG_FILE` | `logs/search-engine.log` | App log file tailed by Promtail |

## Observability

The local Docker Compose stack includes open-source observability tools:

- Spring Actuator exposes health, metrics, and Prometheus endpoints.
- Micrometer publishes JVM, HTTP, Reactor Netty, Kafka, and application metrics.
- Prometheus scrapes `http://host.docker.internal:8082/actuator/prometheus`.
- OpenTelemetry exports traces from the app to the collector.
- The OpenTelemetry Collector forwards traces to Tempo.
- Spring writes app logs to `logs/search-engine.log`.
- Promtail ships that log file to Loki.
- Grafana is provisioned with Prometheus, Loki, and Tempo datasources.

Open the tools:

| Tool | URL | Notes |
| --- | --- | --- |
| Grafana | `http://localhost:3000` | Login `admin` / `admin` |
| Prometheus | `http://localhost:9090` | Metrics queries and scrape status |
| Loki | `http://localhost:3100/ready` | Log backend readiness |
| Tempo | `http://localhost:3200/ready` | Trace backend readiness |
| Actuator Health | `http://localhost:8082/actuator/health` | App health |
| Actuator Metrics | `http://localhost:8082/actuator/prometheus` | Prometheus scrape endpoint |

Useful Grafana queries:

Prometheus:

```promql
http_server_requests_seconds_count{application="search-engine"}
jvm_memory_used_bytes{application="search-engine"}
```

Loki:

```logql
{job="search-engine"}
```

Generate traffic to see metrics, logs, and traces:

```bash
curl "http://localhost:8082/api/products?page=0&size=5"
curl "http://localhost:8082/api/products/search?q=mobile&page=0&size=10"
curl "http://localhost:8082/api/products/semantic-status"
```

## CDC Flow

1. Product changes are written to Postgres.
2. Debezium Connect streams changes from `public.products`.
3. Events are published to Kafka topic `dbserver1.public.products`.
4. The Spring Kafka consumer receives product events.
5. The app indexes product data into OpenSearch.
6. The app generates OpenAI embeddings and upserts vectors into Weaviate.

## Manual Useful Commands

Check app status:

```bash
curl "http://localhost:8082/api/products/semantic-status"
```

Register or inspect Debezium connector:

```bash
curl "http://localhost:8083/connectors"
curl "http://localhost:8083/connectors/products-connector/status"
```

Check OpenSearch count:

```bash
curl "http://localhost:9200/products/_count"
```

Check Weaviate count:

```bash
curl -X POST "http://localhost:8085/v1/graphql" \
  -H "Content-Type: application/json" \
  -d '{"query":"{ Aggregate { Product { meta { count } } } }"}'
```

Run tests:

```bash
./mvnw test
```
