import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.sync import replay


@pytest.mark.parametrize("fail_publish", [False, True])
def test_replay_confirms_before_ack_and_is_bounded(monkeypatch, fail_publish):
    message = SimpleNamespace(body=b'{"schema_version":1,"book_id":1}', message_id="event", ack=AsyncMock())
    queue = SimpleNamespace(get=AsyncMock(return_value=message))

    async def publish(*args, **kwargs):
        message.ack.assert_not_awaited()
        assert kwargs["mandatory"] is True
        if fail_publish:
            raise RuntimeError("offline")

    channel = SimpleNamespace(declare_queue=AsyncMock(return_value=queue),
                              default_exchange=SimpleNamespace(publish=AsyncMock(side_effect=publish)))
    connection = MagicMock()
    connection.__aenter__ = AsyncMock(return_value=connection)
    connection.__aexit__ = AsyncMock(return_value=False)
    connection.channel = AsyncMock(return_value=channel)
    monkeypatch.setattr(replay.aio_pika, "connect_robust", AsyncMock(return_value=connection))
    if fail_publish:
        with pytest.raises(RuntimeError):
            asyncio.run(replay.replay(1))
        message.ack.assert_not_awaited()
    else:
        assert asyncio.run(replay.replay(1)) == 1
        message.ack.assert_awaited_once()
        queue.get.assert_awaited_once()


def test_invalid_limit_does_not_connect(monkeypatch):
    connect = AsyncMock()
    monkeypatch.setattr(replay.aio_pika, "connect_robust", connect)
    with pytest.raises(ValueError):
        asyncio.run(replay.replay(0))
    connect.assert_not_awaited()
