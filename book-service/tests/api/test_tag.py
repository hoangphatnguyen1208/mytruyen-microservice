import pytest
from app.core.config import settings


API = settings.API_V1_STR


@pytest.mark.asyncio
async def test_get_tags_list(client):
    # Ensure at least one tag exists from fixtures or create one via CRUD/fixture
    response = await client.get(f"{API}/tags")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)


@pytest.mark.asyncio
async def test_get_tag_by_slug(client, test_tag):
    response = await client.get(f"{API}/tags/{test_tag.slug}")
    assert response.status_code == 200
    data = response.json()
    assert data["slug"] == test_tag.slug
    assert data["name"] == test_tag.name


@pytest.mark.asyncio
async def test_create_tag_requires_admin(client, user_token):
    payload = {"name": "new-tag", "slug": "new-tag", "description": "desc"}
    headers = {"Authorization": f"Bearer {user_token}"}
    response = await client.post(f"{API}/tags", json=payload, headers=headers)
    assert response.status_code == 403


@pytest.mark.asyncio
async def test_create_tag_as_admin(client, admin_token):
    payload = {"name": "new-tag-admin", "slug": "new-tag-admin", "type": "general", "description": "desc"}
    headers = {"Authorization": f"Bearer {admin_token}"}
    response = await client.post(f"{API}/tags", json=payload, headers=headers)
    assert response.status_code == 201
    data = response.json()
    assert data["name"] == payload["name"]
    assert data["slug"] == payload["slug"]


@pytest.mark.asyncio
async def test_create_duplicate_tag_fails(client, admin_token):
    payload = {"name": "dup-tag", "slug": "dup-tag", "type": "general", "description": "desc"}
    headers = {"Authorization": f"Bearer {admin_token}"}
    # first create
    r1 = await client.post(f"{API}/tags", json=payload, headers=headers)
    assert r1.status_code == 201
    r2 = await client.post(f"{API}/tags", json=payload, headers=headers)
    assert r2.status_code == 400


@pytest.mark.asyncio
async def test_update_tag_as_admin(client, admin_token, test_tag):
    payload = {"name": "updated-name", "slug": test_tag.slug, "type": "general", "description": "updated"}
    headers = {"Authorization": f"Bearer {admin_token}"}
    r = await client.patch(f"{API}/tags/{test_tag.slug}", json=payload, headers=headers)
    assert r.status_code == 200
    data = r.json()
    assert data["name"] == payload["name"]
    assert data["description"] == payload["description"]


@pytest.mark.asyncio
async def test_delete_tag_requires_admin(client, user_token, test_tag):
    headers = {"Authorization": f"Bearer {user_token}"}
    r = await client.delete(f"{API}/tags/{test_tag.slug}", headers=headers)
    assert r.status_code == 403


@pytest.mark.asyncio
async def test_delete_tag_as_admin(client, admin_token, test_tag):
    headers = {"Authorization": f"Bearer {admin_token}"}
    r = await client.delete(f"{API}/tags/{test_tag.slug}", headers=headers)
    assert r.status_code == 200
    data = r.json()
    assert "message" in data
