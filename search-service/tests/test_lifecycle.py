import asyncio
import threading

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


def test_read_only_mode_does_not_start_consumer(monkeypatch):
    async def unexpected(*args, **kwargs):
        raise AssertionError("Consumer must be disabled")

    monkeypatch.setattr("app.main.run_sync", unexpected)
    with TestClient(create_app(Settings(search_sync_enabled=False))) as client:
        assert client.get("/health/ready").json()["search_sync"] == "disabled"


def test_integrated_consumer_uses_write_key_and_is_cancelled_on_shutdown(monkeypatch):
    started = threading.Event()
    stopped = threading.Event()
    captured = []

    async def consume(settings, *, on_ready):
        captured.append(settings.meili_master_key.get_secret_value())
        on_ready()
        started.set()
        try:
            await asyncio.Future()
        finally:
            stopped.set()

    monkeypatch.setattr("app.main.run_sync", consume)
    config = Settings(search_sync_enabled=True, meili_master_key="read-key", meili_write_key="write-key")
    with TestClient(create_app(config)) as client:
        assert started.wait(2)
        assert client.get("/health/ready").status_code == 200
        assert config.meili_master_key.get_secret_value() == "read-key"
    assert stopped.wait(2)
    assert captured == ["write-key"]


def test_failed_consumer_recovers_without_exposing_credentials(monkeypatch, caplog):
    failed = threading.Event()
    recovered = threading.Event()
    attempts = []

    async def consume(settings, *, on_ready):
        attempts.append(1)
        if len(attempts) == 1:
            failed.set()
            raise RuntimeError("secret-broker-password")
        on_ready()
        recovered.set()
        await asyncio.Future()

    monkeypatch.setattr("app.main.run_sync", consume)
    with TestClient(create_app(Settings(search_sync_enabled=True))) as client:
        assert failed.wait(2)
        assert client.get("/health").status_code == 200
        assert client.get("/health/ready").status_code == 503
        assert recovered.wait(7)
        assert client.get("/health/ready").status_code == 200
    assert len(attempts) == 2
    assert "secret-broker-password" not in caplog.text
