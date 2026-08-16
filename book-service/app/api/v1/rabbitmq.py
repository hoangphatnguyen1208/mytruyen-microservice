from fastapi import APIRouter, Body
from app.api.deps import CurrentAdmin, RabbitMQChannelDep
from app.schema.rabbitmq import RabbitMQB
from app.schema.response import Response
from app.core.config import settings
from aio_pika import Message, DeliveryMode
import json

router = APIRouter(prefix="/rabbitmq", tags=["rabbitmq"])


@router.post("/genres", response_model=Response[None])
async def send_genres_to_rabbitmq(
    current_admin: CurrentAdmin, rabbitmq_channel: RabbitMQChannelDep
) -> Response[None]:
    await rabbitmq_channel.default_exchange.publish(
        Message(
            body=json.dumps({"type": "crawl_genres"}).encode(),
            delivery_mode=DeliveryMode.PERSISTENT,
        ),
        routing_key=settings.RABBITMQ_QUEUE_CRAWL,
    )
    return Response(
        status_code=200,
        success=True,
        message="Crawl genres message sent to RabbitMQ",
        data=None,
    )


@router.post("/tags", response_model=Response[None])
async def send_tags_to_rabbitmq(
    current_admin: CurrentAdmin, rabbitmq_channel: RabbitMQChannelDep
) -> Response[None]:
    await rabbitmq_channel.default_exchange.publish(
        Message(
            body=json.dumps({"type": "crawl_tags"}).encode(),
            delivery_mode=DeliveryMode.PERSISTENT,
        ),
        routing_key=settings.RABBITMQ_QUEUE_CRAWL,
    )
    return Response(
        status_code=200,
        success=True,
        message="Crawl tags message sent to RabbitMQ",
        data=None,
    )


@router.post("/book-statuses", response_model=Response[None])
async def send_book_statuses_to_rabbitmq(
    current_admin: CurrentAdmin, rabbitmq_channel: RabbitMQChannelDep
) -> Response[None]:
    await rabbitmq_channel.default_exchange.publish(
        Message(
            body=json.dumps({"type": "crawl_book_statuses"}).encode(),
            delivery_mode=DeliveryMode.PERSISTENT,
        ),
        routing_key=settings.RABBITMQ_QUEUE_CRAWL,
    )
    return Response(
        status_code=200,
        success=True,
        message="Crawl book statuses message sent to RabbitMQ",
        data=None,
    )


@router.post("/all-books", response_model=Response[None])
async def send_all_books_to_rabbitmq(
    current_admin: CurrentAdmin, rabbitmq_channel: RabbitMQChannelDep
) -> Response[None]:
    await rabbitmq_channel.default_exchange.publish(
        Message(
            body=json.dumps({"type": "crawl_all_books"}).encode(),
            delivery_mode=DeliveryMode.PERSISTENT,
        ),
        routing_key=settings.RABBITMQ_QUEUE_CRAWL,
    )
    return Response(
        status_code=200,
        success=True,
        message="Crawl all books message sent to RabbitMQ",
        data=None,
    )


@router.post("/book", response_model=Response[None])
async def send_book_to_rabbitmq(
    current_admin: CurrentAdmin, rabbitmq_channel: RabbitMQChannelDep, book: RabbitMQB
) -> Response[None]:
    await rabbitmq_channel.default_exchange.publish(
        Message(
            body=json.dumps({"type": "crawl_book", "book_id": book.book_id}).encode(),
            delivery_mode=DeliveryMode.PERSISTENT,
        ),
        routing_key=settings.RABBITMQ_QUEUE_CRAWL,
    )
    return Response(
        status_code=200,
        success=True,
        message=f"Crawl book {book.book_id} message sent to RabbitMQ",
        data=None,
    )


@router.post("/chapters", response_model=Response[None])
async def send_chapters_to_rabbitmq(
    current_admin: CurrentAdmin, rabbitmq_channel: RabbitMQChannelDep, book: RabbitMQB
) -> Response[None]:
    await rabbitmq_channel.default_exchange.publish(
        Message(
            body=json.dumps(
                {"type": "crawl_chapters", "book_id": book.book_id}
            ).encode(),
            delivery_mode=DeliveryMode.PERSISTENT,
        ),
        routing_key=settings.RABBITMQ_QUEUE_CRAWL,
    )
    return Response(
        status_code=200,
        success=True,
        message=f"Crawl chapters for book {book.book_id} message sent to RabbitMQ",
        data=None,
    )
