# Source layout

Java domain services use packages by responsibility. Keep only the Spring Boot application entry point in the root package so component, entity and repository scanning includes all child packages.

```text
online.mytruyen.<service>/
  *Application.java
  api/          HTTP controllers and endpoint mappings
  dto/          Request/response contracts; no JPA entities in HTTP responses
  domain/       JPA entities and domain state
  repository/   Spring Data repositories and database queries
  service/      Use cases, transaction boundaries and orchestration
  security/     JWT, principals, filters and security configuration
  exception/    Application exceptions and HTTP exception handlers
  mapper/       Entity-to-DTO mapping (Catalog)
  support/      Shared implementation helpers such as validated PATCH (Catalog)
```

Identity's `IdentityStore` remains in `service` because it coordinates repositories, DTO mapping and outbox writes. `BootstrapAdmin` also lives there as a startup use case. JWT issuance/verification is exposed through public methods; token lifetime remains private with a read-only accessor.

Catalog follows the same layout. Controllers remain in `api`; DTOs, mapping, PATCH validation and error handling are separated from controllers. Transaction boundaries and HTTP contracts are unchanged.

Gateway already groups JWT components in `security`. Engagement, Search and Ingestion remain small skeletons: create the corresponding packages when adding functionality, rather than empty directories now. Tests remain under `src/test/java`, independent of production sources; service-wide integration tests stay in the root test package.

Resources stay under `src/main/resources`: application configuration and Flyway migrations are not Java packages. This refactor changes no database schema, table names, URLs or deployment entry points.

After moving packages, use `gradlew.bat clean test bootJar` in each affected service. A clean build is important so old compiled classes cannot mask missing imports or duplicate Spring components.
