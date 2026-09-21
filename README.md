# MyTruyen Microservices

This repository is being migrated from `mytruyen-be` with a strangler-style, service-by-service rollout. The detailed roadmap is in [MIGRATION_PLAN.md](MIGRATION_PLAN.md).

## Target services

| Directory | Runtime | Responsibility | Current phase |
|---|---|---|---|
| `mytruyen-gateway` | Java 17 / Spring Cloud Gateway | Public entry point, routing and edge JWT policy | Existing, routed to the new service names |
| `identity-service` | Java 17 / Spring Boot | Users, roles, credentials, authentication and refresh sessions | Walking skeleton |
| `catalog-service` | Java 17 / Spring Boot | Books, authors, taxonomy, chapters and chapter content | Walking skeleton |
| `search-service` | Python 3.12 / FastAPI | Search API and search projections | Walking skeleton |
| `ingestion-worker` | Python 3.12 | Crawl/import commands and data normalization | Walking skeleton |
| `engagement-service` | Java 17 / Spring Boot | Comments, reviews, ratings and bookmarks | Walking skeleton; implementation is deferred |

`auth-service` and `user-service` are legacy transition sources. They are intentionally not included in the default Compose topology. Their behavior will be moved into `identity-service`, tested for parity, and only then removed.

## Infrastructure

- One PostgreSQL database per stateful domain service.
- RabbitMQ for integration events and ingestion commands.
- Meilisearch for the search projection.
- Redis is reserved for cache/rate limiting; it is not a second crawl queue.
- Flyway owns Java service schema changes. Hibernate runs with `ddl-auto: validate`.

## Run locally

Generate local JWT keys, then create the environment file:

```powershell
.\scripts\generate-jwt-keys.ps1
Copy-Item .env.example .env
```

Copy the generated key values from `.env.jwt.local` into `.env`, set non-default passwords, then run:

```powershell
docker compose up --build
```

The public Gateway listens on `http://localhost:8080`. RabbitMQ management is available on `http://localhost:15672` for local development.

## Build and test

Java services:

```powershell
.\identity-service\gradlew.bat -p identity-service build
.\catalog-service\gradlew.bat -p catalog-service build
.\engagement-service\gradlew.bat -p engagement-service build
.\mytruyen-gateway\gradlew.bat -p mytruyen-gateway test
```

Python services:

```powershell
Push-Location search-service; uv run pytest; Pop-Location
Push-Location ingestion-worker; uv run pytest; Pop-Location
```

## Migration phases

1. Walking skeleton and deployable topology (current).
2. Merge Auth/User behavior and data into Identity.
3. Implement Catalog schema/API and migrate book/chapter data.
4. Build event outbox and Search projection.
5. Move crawler into Ingestion Worker.
6. Implement Engagement only when its product APIs are scheduled.
7. Canary cutover and archive the monolith/legacy services.

Each phase must keep the repository buildable and requires its own tests before the next phase starts.
