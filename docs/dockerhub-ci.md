# Docker Hub publishing

The `Migration verification` workflow tests Java, Python and Go first. Only a
successful push to `main`, or a manual run on `main` with `publish_images=true`,
publishes images. PRs and other branches cannot log in or publish. Manual Docker
integration remains separately opt-in; publishing does not imply it passed.
This workflow does not deploy to a VPS or start crawlers.

## One-time configuration

In GitHub repository Settings > Secrets and variables > Actions:

- Variable `DOCKERHUB_USERNAME`: your Docker Hub account name (lowercase).
- Secret `DOCKERHUB_TOKEN`: Docker Hub access token with Read/Write permission,
  not your account password. Never put it in `.env`, build arguments or Git.

Create/check these Docker Hub repositories under that account, selecting the
intended public/private visibility before the first run:

- `mytruyen-identity-service`
- `mytruyen-catalog-service`
- `mytruyen-engagement-service`
- `mytruyen-gateway`
- `mytruyen-search-service`
- `mytruyen-worker`

Search API and search-indexer share `mytruyen-search-service`; the indexer keeps
its existing `python -m app.sync.worker` command. Python ingestion is experimental
and excluded. PostgreSQL, RabbitMQ, Redis and Meilisearch use upstream images.

## Tags and deployment

Every image uses `sha-<full Git commit SHA>`. There is deliberately no moving
`latest` tag: overlapping builds cannot replace a newer release with an older
one. A rerun of the same commit can still replace its SHA tag (base images may
change); use the image digest in the Actions job summary for immutable pinning.
Images currently target `linux/amd64`, not ARM Azure Bps VMs.

Example image reference (replace account and full SHA):

```text
docker.io/ACCOUNT/mytruyen-catalog-service:sha-FULL_COMMIT_SHA
```

Publish uses a six-service matrix, three builds at a time, per-service BuildKit
cache, SBOM and minimal provenance. Wait for ALL six publishing jobs to succeed
before deploying a revision: publication across repositories is not atomic.
If one fails, rerun the failed jobs; do not deploy a mixed partial release.

The current Compose file still builds locally. To deploy published images, set
each application service's `image` to the matching SHA/digest in your deployment
Compose configuration and use `docker compose pull` followed by
`docker compose up -d --no-build`. Both search services must select the same
image. Keep the worker opt-in profile and existing environment/secrets/volumes.
On private repositories, authenticate the VPS using a separate read-only token.
Rollback uses the previous verified digests and the database migration runbook;
rolling back images does not roll back database migrations.

Protect `main`, require verification checks before merge, and restrict workflow
changes to trusted maintainers. Missing credentials fail the publish jobs with
a configuration error; do not paste tokens into logs to diagnose it.

References: [Docker GitHub Actions](https://docs.docker.com/build/ci/github-actions/)
and [build-push-action](https://github.com/docker/build-push-action).
