from fastapi import APIRouter
from app.api.deps import CurrentAdmin, RedisDep
from app.schema.response import Response

router = APIRouter(prefix="/arq", tags=["arq"])


@router.post("/crawl-genres", response_model=Response[None])
async def crawl_genres(current_admin: CurrentAdmin, redis: RedisDep) -> Response[None]:
    await redis.enqueue_job("crawl_genres")
    return Response(
        status_code=200, success=True, message="Crawl genres job enqueued", data=None
    )


@router.post("/crawl-tags", response_model=Response[None])
async def crawl_tags(current_admin: CurrentAdmin, redis: RedisDep) -> Response[None]:
    await redis.enqueue_job("crawl_tags")
    return Response(
        status_code=200, success=True, message="Crawl tags job enqueued", data=None
    )


@router.post("/crawl-book-statuses", response_model=Response[None])
async def crawl_book_statuses(
    current_admin: CurrentAdmin, redis: RedisDep
) -> Response[None]:
    await redis.enqueue_job("crawl_book_statuses")
    return Response(
        status_code=200,
        success=True,
        message="Crawl book statuses job enqueued",
        data=None,
    )


@router.post("/crawl-chapters/{book_id}", response_model=Response[None])
async def crawl_chapters(
    current_admin: CurrentAdmin, redis: RedisDep, book_id: str
) -> Response[None]:
    await redis.enqueue_job("crawl_chapters", book_id=book_id)
    return Response(
        status_code=200, success=True, message="Crawl chapters job enqueued", data=None
    )


@router.post("/crawl-books", response_model=Response[None])
async def crawl_books(
    current_admin: CurrentAdmin, redis: RedisDep, limit: int = 20
) -> Response[None]:
    await redis.enqueue_job("crawl_all_books", limit=limit)
    return Response(
        status_code=200, success=True, message="Crawl books job enqueued", data=None
    )


@router.post("/add-all-books-to-meili", response_model=Response[None])
async def add_all_books_to_meili(
    current_admin: CurrentAdmin, redis: RedisDep
) -> Response[None]:
    await redis.enqueue_job("add_all_books_to_meili")
    return Response(
        status_code=200,
        success=True,
        message="Add all books to Meilisearch job enqueued",
        data=None,
    )
