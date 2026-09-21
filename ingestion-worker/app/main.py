import asyncio
import logging
import os

import aio_pika


logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
logger = logging.getLogger("ingestion-worker")


async def run() -> None:
    url = os.getenv("RABBITMQ_URL", "amqp://mytruyen:mytruyen@localhost/")
    queue_name = os.getenv("INGESTION_QUEUE", "mytruyen.ingestion.commands.v1")
    connection = await aio_pika.connect_robust(url)
    async with connection:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=1)
        queue = await channel.declare_queue(queue_name, durable=True)
        logger.info("ingestion worker ready; queue=%s", queue_name)
        async with queue.iterator() as messages:
            async for message in messages:
                async with message.process(requeue=False):
                    logger.info("received command; bytes=%d", len(message.body))


def main() -> None:
    asyncio.run(run())


if __name__ == "__main__":
    main()
