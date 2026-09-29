# VPS + Azure PostgreSQL Flexible Server

Use `docker-compose.azure.yml` **alone**, not as an overlay on the local or
production Compose files. It pulls Docker Hub images; it never builds source.
Seven default containers, eight with Go worker; three JVMs. No local PostgreSQL,
Redis or Engagement. Existing Gateway Engagement routes remain unavailable and
can return 5xx; do not expose those features in the frontend.

## Prerequisites

- Linux amd64 VPS, Docker Compose v2, host HTTPS reverse proxy.
- Successful publishing of every required image for the same full commit SHA.
- Azure network access from the VPS (restrict public firewall to its outbound IP,
  or configure private routing). Do not enable access from all addresses.
- Separate databases `mytruyen_identity`/`mytruyen_catalog`, owned by login roles
  `identity_app`/`catalog_app`, with separate passwords and database access grants.
  Application roles need schema creation rights for Flyway. Do not use Azure admin.
- A readable PEM root-CA bundle from current Azure TLS documentation at
  `AZURE_PG_CA_FILE`. Both JDBC URLs enforce `verify-full`; missing bind files fail
  instead of silently becoming directories. Maintain the bundle during CA rotation.
  https://learn.microsoft.com/en-us/azure/postgresql/security/security-tls-how-to-connect
- Back up and rehearse data migration if an existing database is being moved.
  Flyway migrates schema, not old application data. Never overwrite an existing DB.

## Configuration and initial startup

```bash
cp .env.azure.example .env.azure
chmod 600 .env.azure
```

Fill all secrets and published image tag. Generate JWT using the existing
`scripts/generate-jwt-keys.ps1` on Windows and transfer securely; preserve keys
across deployments. Configure Rabbit credentials consistently: `APP_RABBITMQ_URL`
uses URL-encoded credentials and hostname `rabbitmq`, not localhost.

For a NEW deployment only, bootstrap Meili with search/write key values temporarily
equal to the master key, keeping API traffic closed. Start only Meili, create
restricted keys through its internal `/keys` API, then replace the two values
before starting Search. Search key is scoped to searching `books`; writer key
needs index creation/read, settings, document add/delete and task status for
`books`. See the main deployment runbook. There is no implicit master-key fallback.

In the deployment shell define this helper to consistently select the file/env:

```bash
dc() { docker compose --env-file .env.azure -p mytruyen-azure -f docker-compose.azure.yml "$@"; }
dc config --quiet
dc pull
dc up -d meilisearch rabbitmq
# Provision restricted Meili keys now; update .env.azure before continuing.
dc up -d identity-service catalog-service
dc logs --tail=100 identity-service catalog-service
dc up -d search-service mytruyen-gateway
```

Use a stable project name; a different name creates different Rabbit/Meili volumes.
This file does not reuse an old stack's volumes automatically. When migrating an
existing stack, plan the volume handoff or rebuild explicitly; never delete old
volumes. Do not print full `dc config` because it expands secrets.

For a new Identity database set bootstrap enabled/email/password before startup.
After verifying admin login, disable bootstrap and remove its credentials from
the env file, then `dc up -d --force-recreate identity-service`.

Create the Search index with writers paused. Supply a maintenance key interactively:

```bash
read -rsp 'Meili maintenance key: ' MEILI_MASTER_KEY
echo
export MEILI_MASTER_KEY
dc run --rm --no-deps -e MEILI_MASTER_KEY search-indexer \
  python -m app.rebuild --catalog-writes-paused --allow-empty
unset MEILI_MASTER_KEY
dc up -d search-indexer
```

`--allow-empty` is ONLY for a confirmed empty database. For existing data omit it,
stop all index writers and pause Catalog writes for the rebuild. Environment
variables override env-file values: do not keep unrelated secrets exported.

Validate login, protected/public APIs and search via Gateway, then enable host
HTTPS proxy to `127.0.0.1:8080`. Only Gateway and Rabbit management bind loopback;
no database/broker AMQP/Meili ports are published. Restrict SSH and expose only
needed web ports. Do not run destructive smoke scripts against production.

## Worker and resources

Provision an IMPORTER account (role 3), source credentials, exact queue, verified
status map and any existing-book bindings as in `migration/worker-legacy-compat.md`.
Set compatibility enabled and recreate Catalog. Only when authorized to crawl:

```bash
dc up -d --force-recreate catalog-service
dc --profile worker pull worker
dc --profile worker up -d worker
dc logs --tail=100 worker
```

Starting worker also starts legacy scheduled polling. It is not passive.
Concurrency defaults to one in this deployment.

Memory limits total 3328 MiB without worker, 3584 MiB with worker. These are caps,
not measured requirements or a promise of fitting a 4 GB VPS: OS/proxy/Docker
need headroom, and services can OOM under load. Start without worker, measure
`docker stats --no-stream`, test indexing peaks, then tune or increase capacity.
Java heaps leave room for native memory. Meili indexing budget is 256 MiB;
Hikari starts with 1 idle/max 5 connections per DB (not a benchmarked optimum).

## Updates and recovery

Back up Azure databases and confirm restore/PITR procedure before migrations.
Record image digests and previous release tag. Change IMAGE_TAG only to a fully
published/tested release, run `dc pull`, then `dc up -d` during a controlled rollout.
Stop worker first when doing database cutover. Container start is not readiness;
check logs, APIs, outbox and queues. For rollback restore previous images only
if schema compatible; database recovery is a separate reviewed operation.
Never run `down --volumes` on production. This configuration was statically
checked only; no Docker runtime or live Azure connectivity is claimed.
