from fastapi.testclient import TestClient
from main import app, token_hash, hasher

client = TestClient(app)


def test_health_without_database():
    assert client.get("/health").json() == {"status": "ok"}


def test_private_endpoints_require_session():
    for route in ["/me", "/addresses"]:
        assert client.get(route).status_code == 401


def test_invalid_address_rejected_before_database():
    from main import Address
    from pydantic import ValidationError
    import pytest

    with pytest.raises(ValidationError):
        Address(cep="123", logradouro="Rua", numero="1", cidade="Cidade", estado="SP")


def test_password_hash_and_token_storage():
    password_hash = hasher.hash("Senha-de-teste-123")
    assert hasher.verify(password_hash, "Senha-de-teste-123")
    assert token_hash("token") != "token"
    assert len(token_hash("token")) == 64


def test_unconfigured_database(monkeypatch):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    assert client.get("/products").status_code == 503
