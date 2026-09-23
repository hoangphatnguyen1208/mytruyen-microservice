"""Run as one logical index writer: python -m app.sync.worker.

Rabbit single-active-consumer + prefetch=1 serializes current-state rehydration.
Stop ALL index writers before offline rebuild. DLQ replay must rehydrate, never
write historical document snapshots.
"""
import asyncio
import logging

import aio_pika
import httpx

from app.config import Settings
from app.rebuild import RebuildError
from app.sync.indexer import Indexer, InvalidEvent, book_id

QUEUE = "mytruyen.search.sync.v1"
EXCHANGE = "mytruyen.catalog.search.v1"
log = logging.getLogger(__name__)


async def process(message, indexer, dead_letter, *, sleep=asyncio.sleep):
    try:
        identifier = book_id(message.body)
    except InvalidEvent:
        await dead_letter(message, "invalid_event")
        await message.ack()
        return
    for attempt in range(5):
        try:
            await indexer.synchronize(identifier)
        except RebuildError:
            log.warning("search_sync retry book_id=%s attempt=%s", identifier, attempt + 1)
            if attempt < 4:
                # Keep the original unacked. No broker TTL dead-letter hop can lose a retry.
                await sleep(min(2 ** attempt, 8))
                continue
            await dead_letter(message, "dependency_failure")
            await message.ack()
            log.error("search_sync dead_letter book_id=%s", identifier)
            return
        await message.ack()
        log.info("search_sync applied book_id=%s", identifier)
        return


async def run():
    settings = Settings()
    connection = await aio_pika.connect_robust(settings.rabbitmq_url.get_secret_value())
    async with connection, httpx.AsyncClient(timeout=settings.request_timeout, follow_redirects=False) as client:
        channel = await connection.channel(publisher_confirms=True, on_return_raises=True)
        await channel.set_qos(prefetch_count=1)
        exchange = await channel.declare_exchange(EXCHANGE, aio_pika.ExchangeType.DIRECT, durable=True)
        queue = await channel.declare_queue(QUEUE, durable=True, arguments={
            "x-single-active-consumer": True,
            "x-dead-letter-exchange": "", "x-dead-letter-routing-key": QUEUE + ".dead",
        })
        await channel.declare_queue(QUEUE + ".dead", durable=True)
        await queue.bind(exchange, "book.changed")
        indexer = Indexer(client, settings, settings.sync_task_timeout)

        async def dead_letter(message, reason):
            # Confirm DLQ delivery before acknowledging the original. A crash may duplicate,
            # but an unroutable/failed publish must leave the original available for recovery.
            await channel.default_exchange.publish(aio_pika.Message(
                body=message.body, content_type="application/json", message_id=message.message_id,
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                headers={"failure_reason": reason},
            ), routing_key=QUEUE + ".dead", mandatory=True, timeout=10)

        initialized = False
        async with queue.iterator() as messages:
            async for message in messages:
                try:
                    if not initialized:
                        await indexer.initialize()
                        initialized = True
                    await process(message, indexer, dead_letter)
                except Exception:
                    # Close the channel: Rabbit requeues all unacked deliveries. Let the
                    # supervisor restart rather than ACK an uncertain index/DLQ operation.
                    log.error("search_sync stopped with unacknowledged delivery")
                    raise


def main():
    logging.basicConfig(level=logging.INFO)
    # Do not print broker URLs or response bodies via dependency debug logs/tracebacks.
    for name in ("aio_pika", "aiormq", "httpx", "httpcore"):
        logging.getLogger(name).setLevel(logging.CRITICAL)
    try:
        asyncio.run(run())
    except KeyboardInterrupt:
        pass
    except Exception:
        log.error("search_sync process failed; inspect dependency health and DLQ")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
