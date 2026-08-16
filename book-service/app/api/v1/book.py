from fastapi import APIRouter, status
from app.api.deps import SessionDep, CurrentAdmin, ClientDep, MeiliSearchClientDep

from app.crud import book as crud_book
from app.service import book as service_book

from app.schema.book import BookPublic, BookUpdate, BookRegister
from app.schema.response import Response, ResponseList, ResponsePage

router = APIRouter(prefix="/books", tags=["book"])


@router.post(
    "", response_model=Response[BookPublic], status_code=status.HTTP_201_CREATED
)
async def create_book(
    session: SessionDep,
    meili_client: MeiliSearchClientDep,
    current_admin: CurrentAdmin,
    book_register: BookRegister,
):
    db_book = await service_book.create_book(
        session, meili_client, str(current_admin.id), book_register
    )
    return Response(
        status_code=201, success=True, message="Book created successfully", data=db_book
    )


@router.get("", response_model=ResponsePage[BookPublic])
async def get_books(
    session: SessionDep,
    page: int = 1,
    limit: int = 10,
    status: int | None = None,
    sort: str | None = None,
):
    skip = (page - 1) * limit
    db_books, pagination = await service_book.get_books(
        session=session,
        skip=skip,
        limit=limit,
        status=status,
        sort=sort,
    )
    return ResponsePage(
        status_code=200,
        success=True,
        message="Books retrieved successfully",
        data=db_books,
        pagination=pagination,
    )


@router.get("/id/{book_id}", response_model=Response[BookPublic])
async def get_book_by_id(book_id: int, session: SessionDep):
    db_book = await service_book.get_book_by_id(session, book_id)
    return Response(
        status_code=200,
        success=True,
        message="Book retrieved successfully",
        data=db_book,
    )


@router.patch("/id/{book_id}", response_model=Response[BookPublic])
async def update_book(
    book_id: int,
    session: SessionDep,
    current_admin: CurrentAdmin,
    meili_client: MeiliSearchClientDep,
    book: BookUpdate,
):
    updated_book = await service_book.update_book_by_id(
        session, meili_client, book_id, book
    )
    return Response(
        status_code=200,
        success=True,
        message="Book updated successfully",
        data=updated_book,
    )


@router.delete("/id/{book_id}", response_model=Response[None])
async def delete_book(
    book_id: int,
    session: SessionDep,
    current_admin: CurrentAdmin,
    meili_client: MeiliSearchClientDep,
):
    await service_book.delete_book_by_id(session, meili_client, book_id)
    return Response(
        status_code=200, success=True, message="Book deleted successfully", data=None
    )


@router.get("/slug/{slug}", response_model=Response[BookPublic])
async def get_book_by_slug(slug: str, session: SessionDep):
    db_book = await service_book.get_book_by_slug(session, slug)
    return Response(
        status_code=200,
        success=True,
        message="Book retrieved successfully",
        data=db_book,
    )


@router.patch("/slug/{slug}", response_model=Response[BookPublic])
async def update_book(
    slug: str,
    session: SessionDep,
    current_admin: CurrentAdmin,
    meili_client: MeiliSearchClientDep,
    book: BookUpdate,
):
    updated_book = await service_book.update_book_by_slug(
        session, meili_client, slug, book
    )
    return Response(
        status_code=200,
        success=True,
        message="Book updated successfully",
        data=updated_book,
    )


@router.delete("/slug/{slug}", response_model=Response[None])
async def delete_book(
    slug: str,
    session: SessionDep,
    current_admin: CurrentAdmin,
    meili_client: MeiliSearchClientDep,
):
    await service_book.delete_book_by_slug(session, meili_client, slug)
    return Response(
        status_code=200, success=True, message="Book deleted successfully", data=None
    )


@router.get("/topboxes")
async def get_top_boxes(client: ClientDep, session: SessionDep, kind: int, limit: int):
    res = await client.get(
        f"https://backend.metruyencv.com/api/topboxes?filter%5Btopboxable.kind%5D={kind}&limit={limit}"
    )
    return res.json()
