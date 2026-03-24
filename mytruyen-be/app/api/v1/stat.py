from fastapi import APIRouter

from app.api.deps import SessionDep, CurrentAdmin
from app.crud import book as crud_book, chapter as crud_chapter
from app.schema.response import Response

router = APIRouter(prefix="/stats", tags=["stat"])

@router.get("/books/count", response_model=Response[int])
async def get_book_count(session: SessionDep) -> Response[int]:
    count = await crud_book.get_book_count(session)
    return Response(status_code=200, success=True, message="Book count retrieved successfully", data=count)

@router.get("/chapters/count", response_model=Response[int])
async def get_chapter_count(session: SessionDep) -> Response[int]:
    count = await crud_chapter.get_chapter_count(session)
    return Response(status_code=200, success=True, message="Chapter count retrieved successfully", data=count)

@router.get("/chapter_content/count", response_model=Response[int])
async def get_chapter_content_count(session: SessionDep) -> Response[int]:
    count = await crud_chapter.get_chapter_content_count(session)
    return Response(status_code=200, success=True, message="Chapter content count retrieved successfully", data=count)


