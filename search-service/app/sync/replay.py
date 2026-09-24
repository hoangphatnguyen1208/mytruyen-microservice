"""Explicit, bounded DLQ replay after the underlying failure has been repaired."""
import argparse
import asyncio
import logging

import aio_pika

from app.config import Settings
from app.sync.indexer import book_id
from app.sync.worker import QUEUE


async def replay(max_messages):
    if not 1 <= max_messages <= 10000:
        raise ValueError("max-messages must be between 1 and 10000")
    connection = await aio_pika.connect_robust(Settings().rabbitmq_url.get_secret_value())
    async with connection:
        channel = await connection.channel(publisher_confirms=True, on_return_raises=True)
        dead = await channel.declare_queue(QUEUE + ".dead", passive=True)
        count = 0
        while count < max_messages:
            message = await dead.get(fail=False)
            if message is None:
                break
            # Invalid messages are left unacked and requeued on channel close, not retried forever.
            book_id(message.body)
            await channel.default_exchange.publish(aio_pika.Message(
                body=message.body, content_type="application/json", message_id=message.message_id,
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            ), routing_key=QUEUE, mandatory=True, timeout=10)
            await message.ack()
            count += 1
        return count


def main():
    for name in ("aio_pika", "aiormq"):
        logging.getLogger(name).setLevel(logging.CRITICAL)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--max-messages", type=int, default=100)
    args = parser.parse_args()
    try:
        print(f"Replayed {asyncio.run(replay(args.max_messages))} messages")
    except Exception:
        raise SystemExit("Replay stopped; inspect broker health and invalid DLQ events. Unacked events remain queued.")


if __name__ == "__main__":
    main()
