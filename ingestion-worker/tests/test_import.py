def test_worker_module_imports() -> None:
    from app.main import main

    assert callable(main)
