from fastapi import APIRouter, status
from app.api.deps import CurrentAdmin, CurrentUser, SessionDep
from app.schema.response import Response, ResponseList

from app.crud import author as crud_author
from app.schema.author import AuthorCreate, AuthorPublic, AuthorUpdate

from app.utilities.exceptions.http.exc_400 import http_exc_400_author_bad_request
from app.utilities.exceptions.http.exc_404 import http_exc_404_author_not_found_request
import uuid


router = APIRouter(prefix="/authors", tags=["author"])

@router.post("", response_model=Response[AuthorPublic], status_code=status.HTTP_201_CREATED)
async def create_author(session: SessionDep, current_admin: CurrentAdmin, author_in: AuthorCreate) -> Response[AuthorPublic]:
    existing_author = await crud_author.get_author_by_name(session, author_in.name)
    if existing_author:
        raise http_exc_400_author_bad_request(string=author_in.name)
    db_author = await crud_author.create_author(session, author_in)
    return Response(status_code=201, success=True, message="Author created successfully", data=db_author)

@router.get("", response_model=ResponseList[AuthorPublic])
async def get_authors(session: SessionDep, page: int = 1, limit: int = 10) -> ResponseList[AuthorPublic]:
    skip = (page - 1) * limit
    authors = await crud_author.get_authors(session, skip=skip, limit=limit)
    return ResponseList(status_code=200, success=True, message="Authors retrieved successfully", data=authors)

@router.get("/{name}", response_model=Response[AuthorPublic])
async def get_author_by_name(session: SessionDep, name: str) -> Response[AuthorPublic]:
    author = await crud_author.get_author_by_name(session, name)
    if not author:
        raise http_exc_404_author_not_found_request(string=name)
    return Response(status_code=200, success=True, message="Author retrieved successfully", data=author)

@router.patch("/{author_id}", response_model=Response[AuthorPublic])
async def update_author(session: SessionDep, author_id: uuid.UUID, author_in: AuthorUpdate, current_admin: CurrentAdmin) -> Response[AuthorPublic]:
    existing_author = await crud_author.get_author_by_id(session, author_id)
    if not existing_author:
        raise http_exc_404_author_not_found_request(string=str(author_id))
    updated_author = await crud_author.update_author(session, author_id, author_in)
    return Response(status_code=200, success=True, message="Author updated successfully", data=updated_author)

@router.delete("/{author_id}", response_model=Response[None])
async def delete_author(session: SessionDep, author_id: uuid.UUID, current_admin: CurrentAdmin) -> Response[None]:
    existing_author = await crud_author.get_author_by_id(session, author_id)
    if not existing_author:
        raise http_exc_404_author_not_found_request(string=str(author_id))
    await crud_author.delete_author(session, author_id)
    return Response(status_code=200, success=True, message="Author deleted successfully", data=None)
