# Phase 01 - Service scaffold

## Scope completed

- Created the target service directories and independent build definitions.
- Added Flyway baselines and disabled Hibernate schema mutation for stateful Java services.
- Added Search health API and a durable RabbitMQ consumer skeleton for Ingestion.
- Rewired Gateway routes from the removed Book service to Identity, Catalog, Search and Engagement.
- Replaced the Compose topology with database-per-service infrastructure.
- Kept legacy Auth/User source outside the default runtime topology for a safe later migration.

## Explicitly out of scope

- Domain entities and public CRUD endpoints.
- JWT issuance/refresh implementation in the new Identity service.
- Catalog import from `mytruyen-be`.
- Outbox/event schemas and Search indexing.
- Crawler business logic and Engagement APIs.

## Exit checks

- `identity-service`, `catalog-service` and `engagement-service` produce bootable JARs.
- Gateway tests pass with the new route configuration.
- Search and Ingestion unit smoke tests pass.
- `docker compose config --quiet` succeeds with required environment variables.

The next phase is Identity parity: schema migrations, register/login/refresh/logout, user CRUD, JWT tests and user-data migration tooling.
