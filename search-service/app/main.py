import asyncio
import logging
from contextlib import asynccontextmanager, suppress

import httpx
from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.api import router
from app.config import Settings
from app.sync.worker import run as run_sync

log = logging.getLogger(__name__)


def create_app(settings: Settings | None = None, transport=None) -> FastAPI:
    config = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.settings = config
        app.state.sync_ready = False
        async def supervise_sync():
            # The API keeps its read key; the consumer receives a separate write key.
            writer = config.model_copy(update={"meili_master_key":
                config.meili_write_key if config.meili_write_key.get_secret_value() else config.meili_master_key})
            for name in ("aio_pika", "aiormq"):
                logging.getLogger(name).setLevel(logging.CRITICAL)
            while True:
                try:
                    await run_sync(writer, on_ready=lambda: setattr(app.state, "sync_ready", True))
                except asyncio.CancelledError:
                    raise
                except Exception:
                    # Never log broker URLs, credentials, or dependency response bodies.
                    log.error("Search synchronization interrupted; reconnecting")
                app.state.sync_ready = False
                await asyncio.sleep(5)

        async with httpx.AsyncClient(
            timeout=config.request_timeout, follow_redirects=False, transport=transport,
            limits=httpx.Limits(max_connections=50, max_keepalive_connections=10),
        ) as client:
            app.state.client = client
            task = asyncio.create_task(supervise_sync()) if config.search_sync_enabled else None
            try:
                yield
            finally:
                if task is not None:
                    task.cancel()
                    with suppress(asyncio.CancelledError):
                        await task
                app.state.sync_ready = False

    app = FastAPI(title="MyTruyen Search Service", version="0.2.0", lifespan=lifespan,
                  servers=[{"url": "/"}])
    app.include_router(router)

    @app.get("/health", tags=["operations"])
    async def health():
        return {"status": "up", "service": "search-service"}

    @app.get("/health/ready", tags=["operations"])
    async def readiness():
        ready = not config.search_sync_enabled or app.state.sync_ready
        return JSONResponse(status_code=200 if ready else 503, content={
            "status": "up" if ready else "down", "service": "search-service",
            "search_sync": "disabled" if not config.search_sync_enabled else (
                "connected" if app.state.sync_ready else "reconnecting")})

    return app


app = create_app()
