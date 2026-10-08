# MyTruyen Microservices

## Target services

| Directory | Runtime | Responsibility | Current phase |
|---|---|---|---|
| `api-gateway` | Java 17 / Spring Cloud Gateway | Public entry point, routing and edge JWT policy | Existing, routed to the new service names |
| `identity-service` | Java 17 / Spring Boot | Users, roles, credentials, authentication and refresh sessions | Implemented; local integration tests, PostgreSQL deployment pending |
| `book-service` | Java 17 / Spring Boot | Books, authors, taxonomy, chapters and chapter content | CRUD + chapter publication/statistics/outbox; event delivery next |
| `search-service` | Python 3.12 / FastAPI | Search API and integrated RabbitMQ indexing consumer | Search, offline rebuild and automatic synchronization in one process |
| `story-crawler` | Go 1.26 | Existing crawler and Catalog import migration | Imported from the standalone worker repo; cutover pending |
| `engagement-service` | Java 17 / Spring Boot | Comments, reviews, ratings and bookmarks | Walking skeleton; implementation is deferred |

Auth and User have been consolidated into [Identity](identity-service/README.md). Their former source is retained in Git history. Identity handles registration, login, refresh rotation, logout, account administration and self-service profiles without credential HTTP calls. See its README for API compatibility changes and offline user import instructions.

## Infrastructure

Source organization and package responsibilities: [Source layout](docs/architecture/source-layout.md).

- One PostgreSQL database per stateful domain service.
- RabbitMQ for integration events and ingestion commands.
- Meilisearch for the search projection.
- Redis is reserved for cache/rate limiting; it is not a second crawl queue.
- Flyway owns Java service schema changes. Identity uses Spring Data JPA/Hibernate; Java domain services use `ddl-auto: validate`.

## Run locally

VPS with managed Azure PostgreSQL: [Standalone Azure deployment](docs/deployment-azure.md).

Deployment, secrets, initial index, backup/rollback: [Deployment runbook](docs/deployment.md). Test scope and outbox/DLQ operation: [Verification](docs/migration/verification.md). Ordinary CI runs unit tests only; Docker integration is a manual opt-in.

Generate local JWT keys, then create the environment file:

```powershell
.\scripts\generate-jwt-keys.ps1
Copy-Item .env.example .env
```

Copy the generated key values from `.env.jwt.local` into `.env`, set non-default passwords, then run:

```powershell
docker compose up --build
```

The public Gateway listens on `http://localhost:8000`. RabbitMQ management is available on `http://localhost:15672` for local development.

## API documentation

Set `SWAGGER_ENABLED=true` in the service environment to enable Java OpenAPI and Swagger UI (disabled by default outside the local Compose configuration).

- Gateway: http://localhost:8000/docs — select Identity, Book, Search or Engagement. API requests from this page go through Gateway.
- Identity: http://localhost:8001/docs
- Book: http://localhost:8002/docs
- Search: http://localhost:8003/docs (FastAPI)
- Engagement: http://localhost:8004/docs — the service is currently a skeleton with no domain endpoints and is not part of the Azure deployment.

Use Identity's login endpoint to obtain an `access_token`, then paste it into **Authorize** for protected Identity/Catalog operations. Each selected service must be running for its specification to load. Java specifications are available at `/v3/api-docs`; Search uses `/openapi.json`. Gateway proxies them at `/api-docs/identity`, `/api-docs/book`, `/api-docs/search` and `/api-docs/engagement` only when Swagger is enabled.

## Build and test

Docker Hub publishing after tests: [CI setup, image tags and rollback](docs/dockerhub-ci.md).

Java services:

```powershell
.\identity-service\gradlew.bat -p identity-service build
.\book-service\gradlew.bat -p book-service build
.\engagement-service\gradlew.bat -p engagement-service build
.\api-gateway\gradlew.bat -p api-gateway test
```

Go worker (run inside `story-crawler/`; tests do not crawl or require Docker):

```powershell
Push-Location story-crawler
go test ./...
go vet ./...
go build ./...
Pop-Location
```

Worker contract and rollout gates: [Worker import](docs/migration/worker-import.md).

Python services:

```powershell
Push-Location search-service; uv run pytest; Pop-Location
```

## Migration phases

1. Walking skeleton and deployable topology (current).
2. Identity implementation complete; rehearse PostgreSQL migration and account-data cutover before deployment.
3. Implement Catalog schema/API and migrate book/chapter data.
4. Search read/rebuild and automatic outbox projection implemented; real dependency/staging verification remains a deployment gate.
5. Complete the Go crawler cutover in `story-crawler/`; this is the only crawl/import worker.
6. Implement Engagement only when its product APIs are scheduled.
7. Canary cutover and archive the monolith/legacy services.

Each phase must keep the repository buildable and requires its own tests before the next phase starts.
