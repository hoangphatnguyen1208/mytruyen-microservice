from fastapi import APIRouter, status

from app.crud import book_status as book_status_crud
from app.api.deps import SessionDep, CurrentAdmin, CurrentUser

from app.schema.book_status import BookStatusCreate, BookStatusPublic
from app.schema.response import Response, ResponseList

from app.utilities.exceptions.http.exc_400 import http_exc_400_status_bad_request
from app.utilities.exceptions.http.exc_404 import http_exc_404_status_not_found_request

router = APIRouter(prefix="/book-statuses", tags=["book_status"])

@router.post("", response_model=Response[BookStatusPublic], status_code=status.HTTP_201_CREATED)
async def create_book_status(session: SessionDep, book_status: BookStatusCreate) -> Response[BookStatusPublic]:
    existing_status = await book_status_crud.get_book_status_by_name(session, book_status.name)
    if existing_status:
        raise http_exc_400_status_bad_request(string=book_status.name)
    db_book_status = await book_status_crud.create_book_status(session, book_status)
    return Response(status_code=201, success=True, message="Book status created successfully", data=BookStatusPublic.model_validate(db_book_status))

@router.get("", response_model=ResponseList[BookStatusPublic])
async def get_book_statuses(session: SessionDep) -> ResponseList[BookStatusPublic]:
    db_book_statuses = await book_status_crud.get_book_statuses(session)
    data = [BookStatusPublic.model_validate(status) for status in db_book_statuses]
    return ResponseList(status_code=200, success=True, message="Book statuses retrieved successfully", data=data)

@router.get("/{slug}", response_model=Response[BookStatusPublic])
async def get_book_status_by_slug(session: SessionDep, slug: str) -> Response[BookStatusPublic]:
    db_book_status = await book_status_crud.get_book_status_by_slug(session, slug)
    if not db_book_status:
        raise http_exc_404_status_not_found_request(string=slug)
    return Response(status_code=200, success=True, message="Book status retrieved successfully", data=BookStatusPublic.model_validate(db_book_status))

@router.patch("/{slug}", response_model=Response[BookStatusPublic])
async def update_book_status(session: SessionDep, slug: str, book_status_in: BookStatusCreate) -> Response[BookStatusPublic]:
    db_book_status = await book_status_crud.get_book_status_by_slug(session, slug)
    if not db_book_status:
        raise http_exc_404_status_not_found_request(string=slug)
    updated_status = await book_status_crud.update_book_status(session, db_book_status.id, book_status_in)
    return Response(status_code=200, success=True, message="Book status updated successfully", data=BookStatusPublic.model_validate(updated_status))

@router.delete("/{slug}", response_model=Response[None])
async def delete_book_status(session: SessionDep, slug: str, current_admin: CurrentAdmin) -> Response[None]:
    db_book_status = await book_status_crud.get_book_status_by_slug(session, slug)
    if not db_book_status:
        raise http_exc_404_status_not_found_request(string=slug)
    await book_status_crud.delete_book_status(session, db_book_status.id)
    return Response(status_code=200, success=True, message="Book status deleted successfully", data=None)

