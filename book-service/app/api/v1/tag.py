from fastapi import APIRouter
from app.api.deps import SessionDep, CurrentAdmin, CurrentUser
from app.schema.response import Response, ResponseList

from app.crud import tag as tag_crud
from app.schema.tag import TagCreate, TagPublic, TagUpdate

from app.utilities.exceptions.http.exc_400 import http_exc_400_tag_bad_request
from app.utilities.exceptions.http.exc_404 import http_exc_404_tag_not_found_request


router = APIRouter(prefix="/tags", tags=["tag"])

@router.post("", response_model=Response[TagPublic], status_code=201)
async def create_tag(session: SessionDep, current_admin: CurrentAdmin, tag_in: TagCreate) -> Response[TagPublic]:
    existing_tag = await tag_crud.get_tag_by_name(session, tag_in.name)
    if existing_tag:
        raise http_exc_400_tag_bad_request(string=tag_in.name)
    tag = await tag_crud.create_tag(session, tag_in)
    return Response(status_code=201, success=True, message="Tag created successfully", data=tag)

@router.get("", response_model=ResponseList[TagPublic])
async def get_tags(session: SessionDep) -> ResponseList[TagPublic]:
    tags = await tag_crud.get_tags(session)
    return ResponseList(status_code=200, success=True, message="Tags retrieved successfully", data=tags)

@router.get("/{slug}", response_model=Response[TagPublic])
async def get_tag_by_slug(session: SessionDep, slug: str) -> Response[TagPublic]:
    tag = await tag_crud.get_tag_by_slug(session, slug)
    if not tag:
        raise http_exc_404_tag_not_found_request(string=slug)
    return Response(status_code=200, success=True, message="Tag retrieved successfully", data=tag)

@router.patch("/{slug}", response_model=Response[TagPublic])
async def update_tag(session: SessionDep, slug: str, tag_in: TagUpdate) -> Response[TagPublic]:
    existing_tag = await tag_crud.get_tag_by_slug(session, slug)
    if not existing_tag:
        raise http_exc_404_tag_not_found_request(string=slug)
    updated_tag = await tag_crud.update_tag(session, existing_tag.id, tag_in)
    return Response(status_code=200, success=True, message="Tag updated successfully", data=updated_tag)

@router.delete("/{slug}", response_model=Response[None])
async def delete_tag(session: SessionDep, slug: str, current_admin: CurrentAdmin) -> Response[None]:
    existing_tag = await tag_crud.get_tag_by_slug(session, slug)
    if not existing_tag:
        raise http_exc_404_tag_not_found_request(string=slug)
    await tag_crud.delete_tag(session, existing_tag.id)
    return Response(status_code=200, success=True, message="Tag deleted successfully", data=None)

