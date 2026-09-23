from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI

from app.api import router
from app.config import Settings


def create_app(settings: Settings | None = None, transport=None) -> FastAPI:
    config = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.settings = config
        async with httpx.AsyncClient(
            timeout=config.request_timeout, follow_redirects=False, transport=transport,
            limits=httpx.Limits(max_connections=50, max_keepalive_connections=10),
        ) as client:
            app.state.client = client
            yield

    app = FastAPI(title="MyTruyen Search Service", version="0.2.0", lifespan=lifespan)
    app.include_router(router)

    @app.get("/health", tags=["operations"])
    async def health():
        return {"status": "up", "service": "search-service"}

    return app


app = create_app()
