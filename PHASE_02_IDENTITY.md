# Phase 02 - Identity consolidation

Auth and User functionality now lives inside Identity. Former service source is removed from the working tree and recoverable through Git history; no database volumes or production data were removed.

Persistence has been converted from JDBC to Spring Data JPA/Hibernate without changing Flyway V1/V2 or the HTTP contract. Entity relationships, pessimistic refresh locking, user optimistic versions and transactional outbox writes are covered by integration tests; schema validation is enabled and OSIV disabled.

Implemented: Flyway V2 schema, normalized account/credential/role tables, register/login, RSA JWT verification and issuance, refresh rotation and reuse revocation, logout/logout-all, owner/admin access control, profile/password changes, soft deletion, administrator provisioning and last-admin protection. Account writes record PII-free events in a transactional outbox; delivery belongs to the later messaging phase.

Tests cover full HTTP flows using Flyway on H2 in PostgreSQL mode, JWT failure cases, concurrent refresh, session invalidation, duplicate registration and authorization. Gateway permits public token refresh/logout while keeping logout-all authenticated.

Remaining deployment gates: real PostgreSQL migration rehearsal, offline import validation and Docker smoke test. Docker Desktop was unavailable during implementation. No user data was imported. Revocation is immediate within Identity; other services using offline JWT validation retain their access-token expiry window.

The import script supports the Python monolith only. The former Java user-service schema needs a separate mapping if it has live accounts. API differences and configuration are documented in identity-service/README.md.
