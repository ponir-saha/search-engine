AI-Powered Product Search Service

1. Project Goal

Build a standalone Spring Boot microservice that provides intelligent product search suggestions using:

1. Prefix Search (Autocomplete)
2. Semantic Search (Embedding/Vector Search)

The service should expose a single REST API endpoint and return a combined response from both search mechanisms.

Example:

Request:

GET /api/search/suggestions?q=apple

Response:

{
"query": "apple",
"suggestions": [
{
"type": "PREFIX",
"text": "Apple Watch"
},
{
"type": "PREFIX",
"text": "Apple Charger"
},
{
"type": "PREFIX",
"text": "Apple AirPods"
},
{
"type": "SEMANTIC",
"text": "iPhone 16 Pro Max"
},
{
"type": "SEMANTIC",
"text": "iPad Mini"
},
{
"type": "SEMANTIC",
"text": "MacBook Air"
}
]
}

⸻

2. Business Use Cases

Use Case 1: Autocomplete

User types:

ip

Suggestions:

iPhone 16 Pro Max
iPhone Charger
iPhone Cable

Purpose:

* Fast user experience
* Reduce typing effort
* Increase conversion rate

⸻

Use Case 2: Semantic Search

User types:

apple

Suggestions:

iPhone 16 Pro Max
iPad Mini
MacBook Air

Purpose:

* Understand user intent
* Improve product discovery
* Support natural language search

⸻

Use Case 3: Typo Tolerance

User types:

iphne

Suggestions:

iPhone 16 Pro Max
iPhone Charger

Purpose:

* Reduce failed searches

⸻

Use Case 4: Category Understanding

User types:

gaming phone

Suggestions:

ROG Phone
Samsung S26 Ultra
iPhone 16 Pro Max

Purpose:

* Semantic matching
* Better recommendation quality

⸻

3. Functional Requirements

Search API

Endpoint:

GET /api/search/suggestions?q={keyword}

Input:

iphone

Output:

{
"query": "iphone",
"suggestions": []
}

⸻

Prefix Search

Retrieve top 3 suggestions using:

* OpenSearch Completion Suggester
* Prefix Matching
* Fuzzy Matching

⸻

Semantic Search

Retrieve top 3 suggestions using:

* Embedding Generation
* Vector Similarity Search
* KNN Search

⸻

Result Aggregation

Merge results:

Top 3 Prefix Results
+
Top 3 Semantic Results

Remove duplicates before returning.

⸻

4. Non-Functional Requirements

Performance

Autocomplete latency:

< 50 ms

Semantic search latency:

< 150 ms

Combined response:

< 200 ms

⸻

Availability

99.9%

⸻

Scalability

Initial target:

100,000 products
500 requests/sec

Future target:

10 million products
5000 requests/sec

⸻

5. Technical Architecture

Client
|
Spring Boot Search Service
|
+----------------------+
|                      |
Prefix Search          Semantic Search
|                      |
OpenSearch             OpenSearch Vector Index

⸻

6. Technology Stack

Backend

* Java 21+
* Spring Boot 3.x
* Spring Web

⸻

Search Engine

OpenSearch

Purpose:

* Prefix Search
* Completion Suggestion
* Fuzzy Search
* Vector Search

⸻

Embedding Provider

Option A:

AWS Bedrock Titan Embeddings

Option B:

OpenAI text-embedding-3-small

Preferred:

AWS Bedrock Titan

Reason:

* AWS native
* Lower operational overhead

⸻

Cache (Optional Future Phase)

Redis

Use Cases:

* Query cache
* Popular suggestions cache
* Embedding cache

⸻

Observability

OpenTelemetry

Micrometer

Grafana

⸻

7. Data Model

Product Document

{
"productId": 1001,
"name": "iPhone 16 Pro Max",
"brand": "Apple",
"category": "Mobile",
"description": "Latest Apple flagship phone",
"embedding": []
}

⸻

8. OpenSearch Index Design

Product Index

Fields:

productId
name
brand
category
description
suggest
embedding

⸻

Suggest Field

Used for:

Prefix Search
Autocomplete

Example:

{
"suggest": [
"iphone",
"iphone pro",
"apple phone"
]
}

⸻

Embedding Field

Used for:

Semantic Search

Example:

{
"embedding": [0.234, 0.345, 0.765]
}

⸻

9. API Processing Flow

User Request

apple

Step 1:

Execute Prefix Search

Result:

Apple Watch
Apple Charger
Apple AirPods

Step 2:

Generate Query Embedding

apple

Step 3:

Execute Vector Search

Result:

iPhone 16 Pro Max
iPad Mini
MacBook Air

Step 4:

Merge Results

Step 5:

Remove Duplicates

Step 6:

Return Response

⸻

10. Search Ranking Strategy

Prefix Search Weight

60%

Semantic Search Weight

40%

Future ranking factors:

* Product popularity
* Click-through rate
* Purchase frequency
* Inventory status

⸻

11. Future Enhancements

Phase 2

* Redis cache
* Search analytics
* Trending searches

Phase 3

* Personalized search
* User behavior ranking

Phase 4

* AI reranking using LLM
* Product recommendation engine

⸻

12. MVP Scope

Included:

* Single Spring Boot application
* OpenSearch integration
* Prefix search
* Semantic search
* Single API endpoint
* Top 3 prefix results
* Top 3 semantic results
* Combined response

Excluded:

* Kafka
* Microservice communication
* User personalization
* Recommendation engine
* AI reranking
* Multi-region deployment

The goal of the MVP is to validate search quality and user experience before introducing additional infrastructure complexity.

13. Implementation Decisions (MVP)

The following implementation choices have been made for the MVP based on the project goals and the preferences provided:

- Embedding provider: OpenAI `text-embedding-3-small` (configurable abstraction to allow future swapping).
- API style: Reactive single endpoint. A single reactive GET endpoint `GET /api/search/suggestions?q={keyword}` will concurrently execute prefix (autocomplete) and semantic (vector) searches, merge top-3 results from each, remove duplicates, and return a combined reactive response.
- Local development: Provide Docker Compose for running a single-node OpenSearch instance. The application will support connecting to a remote OpenSearch cluster as well.
- Prefix search strategy: Primary use of OpenSearch Completion Suggester for low-latency autocomplete. `search_as_you_type` (or an edge-ngram subfield) will be present as a complement for better ranking and fuzzy fallbacks. Fuzzy matching will be used as a fallback to give typo tolerance.
- OpenSearch version target: OpenSearch 2.x (index mappings and vector support assume 2.x-compatible APIs). Confirm if your environment uses a different minor version.
- Embedding caching: The implementation will include a small in-memory caching layer for query embedding results (configurable) to reduce OpenAI calls in high-traffic scenarios (further caching via Redis can be added in Phase 2).
- Reactive HTTP: The service will use a reactive HTTP client (`WebClient`) to call both OpenAI and OpenSearch so the full request pipeline stays non-blocking.
- Security & secrets: OpenAI key and OpenSearch credentials are expected to be supplied via environment variables (e.g. `OPENAI_API_KEY`, `OPENSEARCH_URL`, `OPENSEARCH_USERNAME`, `OPENSEARCH_PASSWORD`).

Suggested config keys (in `application.yaml`):

- `openai.api-key` or environment `OPENAI_API_KEY`
- `opensearch.url` or environment `OPENSEARCH_URL`
- `opensearch.username` / `opensearch.password` or environment equivalents
- `opensearch.index.products` (index name, default `products`)

Index mapping guidance (MVP):

- `name` field: store as `keyword`, `search_as_you_type`, and a `completion` subfield for the `suggest` usage.
- `suggest` field: type `completion` for the Completion Suggester.
- `embedding` field: use the dense_vector / k-NN compatible mapping for your OpenSearch build (e.g., `dense_vector` or the `knn_vector` field depending on the distribution/plugins).

Operational notes:

- The reactive endpoint enables concurrent execution of prefix and semantic searches to meet combined latency targets; for strict production SLAs add caching, connection pooling, and precomputed embeddings for products.
- The repository will include a `Dockerfile` and `docker-compose.yml` for local testing; the README contains commands to bring up OpenSearch and run the service.

See `README.md` for technical details, environment variables, Docker instructions, and example requests.

14. Product Management API (CRUD + Pagination)

To support catalog management and local testing the service exposes a simple product management API. The canonical product data is stored in Postgres (source-of-truth) and is synchronized to OpenSearch / vector stores either on-write (indexer) or via CDC (Debezium -> Kafka -> indexer) depending on deployment configuration.

Endpoints (HTTP / REST)

- Create product
  - POST /api/products
  - Body: Product DTO (name, sku, brand, category, description, price, status)
  - Response: 201 Created with created Product DTO (including productId, createdAt, updatedAt)

- Update product
  - PUT /api/products/{id}
  - Body: Product DTO (fields to update)
  - Response: 200 OK with updated Product DTO

- Delete product
  - DELETE /api/products/{id}
  - Response: 204 No Content

- Get product by id
  - GET /api/products/{id}
  - Response: 200 OK with Product DTO

- List products (pagination)
  - GET /api/products?page={page}&size={size}&sort={field,asc|desc}&q={optional-search-filter}
  - Default: page=0, size=20
  - Response: 200 OK with paginated list (items, page, size, total)

Behavior and sync notes

- If the deployment uses on-write indexing, write operations (create/update/delete) should trigger indexing operations (compute embedding and upsert/delete document in OpenSearch) either synchronously or asynchronously via an indexing worker.
- If the deployment uses CDC (Debezium + Kafka), product writes only persist to Postgres and Debezium will stream changes to Kafka; an indexer consumer will then apply changes to OpenSearch/vector DB. In this case expect eventual consistency between Postgres and search stores (typically milliseconds to seconds depending on pipeline latency).
- All write operations should return the canonical product representation from Postgres. The client can rely on productId as the unique identifier used by OpenSearch documents.

Example Product DTO (JSON)

{
  "productId": 1001,
  "sku": "SKU-12345",
  "name": "iPhone 16 Pro Max",
  "brand": "Apple",
  "category": "Mobile",
  "description": "Latest Apple flagship phone",
  "price": 1299.99,
  "status": "active",
  "createdAt": "2026-06-15T12:00:00Z",
  "updatedAt": "2026-06-15T12:00:00Z"
}

Example: create product (curl)

curl -X POST "http://localhost:8080/api/products" -H "Content-Type: application/json" -d '{"sku":"SKU-001","name":"Test Phone","brand":"Acme","category":"Mobile","description":"Test device","price":199.99}'


Data synchronization: CDC (Debezium) + Kafka (recommended for production)

For near-real-time synchronization between the canonical product DB (Postgres) and the search stores (OpenSearch and optional vector DB) the recommended production approach is to use change-data-capture (CDC) with Debezium streaming into Kafka, and a resilient indexer (consumer) that applies events to the search indexes.

Flow (high-level):

1. Debezium captures Postgres change events and publishes them to Kafka topics (e.g. `db.products.products`).
2. An indexer/consumer subscribes to the Kafka topic, transforms events into denormalized documents, computes embeddings when required (or reads precomputed embeddings), and upserts documents into OpenSearch and/or a vector DB.
3. Consumers must be idempotent (use productId as document id) and handle out-of-order delivery using `updated_at` or a version field.

Benefits:

- Near-real-time sync with low operational latency.
- Decoupling between product writes and indexing (resilience and scalability).
- Ability to replay topics for reindexing or recovery.

Operational notes for CDC:

- Use Debezium Postgres connector to capture inserts/updates/deletes; run Debezium in a separate container or cluster and connect it to your Kafka broker.
- Use compacted Kafka topics for product streams and configure retention based on replay needs.
- The indexer should support batching, bulk indexing, error handling with DLQ (dead-letter queue), and exponential backoff.
- Maintain idempotency by using productId as the OpenSearch document ID and include `updated_at` to apply last-writer-wins semantics.

Suggested environment variables / config keys:

- `KAFKA_BOOTSTRAP_SERVERS` — bootstrap servers for Kafka
- `DEBEZIUM_CONNECT_URL` — URL for Debezium Connect (if deployed)
- `INDEXER_CONSUMER_GROUP` — Kafka consumer group for the indexer
- `OPENSEARCH_INDEX_PRODUCTS` — target products index name
- `EMBEDDING_ON_WRITE` — boolean to indicate whether the indexer should compute and store embeddings on write

When to prefer CDC over batch/on-write:

- CDC is preferred when product updates must be reflected in search indexes with minimal delay and when the system needs to scale to many updates or multiple services producing product changes.
- Batch is acceptable for initial seed, low update rates, or when operational simplicity is prioritized for the MVP.

See `README.md` for sample Docker Compose snippets to run Kafka + Debezium for local development and links to Debezium configuration examples.

Vector DB (Weaviate)

For semantic vector storage in production we recommend either storing embeddings in OpenSearch (MVP) or using a dedicated vector DB such as Weaviate for scale and feature completeness. The development `docker-compose.yml` includes a Weaviate instance that can be configured to use OpenAI as the vectorizer module (set `OPENAI_API_KEY`).

Weaviate notes:

- REST endpoint: http://localhost:8080
- Health/readiness endpoint: `/v1/.well-known/ready`
- When using Weaviate in production, consider its backup strategy, memory and disk sizing, and vector index persistence options.


Debezium connector - example (Postgres)

Register the Debezium Postgres connector with Debezium Connect using a JSON payload like the following (adjust values to your environment):

```json
{
  "name": "products-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgres.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "pguser",
    "database.password": "pgpass",
    "database.dbname": "products_db",
    "database.server.name": "dbserver1",
    "plugin.name": "pgoutput",
    "slot.name": "debezium_slot",
    "publication.autocreate.mode": "filtered",
    "table.include.list": "public.products"
  }
}
```

Indexer responsibilities (summary):

- Consume change events (insert/update/delete) from the Kafka topic.
- Map events to denormalized documents suitable for OpenSearch and vector DB.
- Compute embeddings if `EMBEDDING_ON_WRITE` is enabled (or read precomputed vectors if present).
- Bulk upsert/delete documents in OpenSearch and upsert vectors in the vector DB.
- Implement idempotency, ordering (use `updated_at`), retry with backoff, and DLQ for poison messages.


