from fastapi import FastAPI
from app.api.main import api_router
from app.core.config import settings
from starlette.exceptions import HTTPException as StarletteHTTPException
from app.schema.response import Response
from fastapi.responses import JSONResponse
from starlette.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging
from meilisearch import Client as MeiliSearchClient
from aio_pika import connect_robust

# device = torch.device("cpu")
logger = logging.getLogger("main")
from arq.connections import RedisSettings
from arq import create_pool


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.redis_pool = await create_pool(
        RedisSettings(
            host=settings.REDIS_HOST,
            port=settings.REDIS_PORT,
            password=settings.REDIS_PASSWORD,
        )
    )
    logger.info("Redis pool for arq created successfully.")

    app.state.meili_client = MeiliSearchClient(
        settings.MEILI_URL, settings.MEILI_MASTER_KEY
    )
    logger.info("MeiliSearch client created successfully.")

    app.state.rabbitmq_connection = await connect_robust(url=settings.RABBITMQ_URL)
    app.state.rabbitmq_channel = await app.state.rabbitmq_connection.channel()
    await app.state.rabbitmq_channel.declare_queue(
        settings.RABBITMQ_QUEUE_CRAWL, durable=True
    )
    logger.info("RabbitMQ channel created successfully.")

    yield

    await app.state.redis_pool.close()
    del app.state.redis_pool
    del app.state.meili_client
    await app.state.rabbitmq_channel.close()
    await app.state.rabbitmq_connection.close()
    del app.state.rabbitmq_channel
    del app.state.rabbitmq_connection


app = FastAPI(title=settings.PROJECT_NAME, lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request, exc):
    return JSONResponse(
        status_code=exc.status_code,
        content=Response(
            status_code=exc.status_code,
            success=False,
            message=str(exc.detail),
            data=None,
        ).model_dump(),
    )


app.include_router(api_router, prefix=settings.API_V1_STR)
