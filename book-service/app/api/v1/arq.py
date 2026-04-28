from fastapi import APIRouter
from app.api.deps import CurrentAdmin, RedisDep
from app.schema.response import Response

router = APIRouter(prefix="/arq", tags=["arq"])

@router.post("/crawl-genres", response_model=Response[None])
async def crawl_genres(current_admin: CurrentAdmin, redis: RedisDep) -> Response[None]:
    await redis.enqueue_job("crawl_genres")
    return Response(status_code=200, success=True, message="Crawl genres job enqueued", data=None)

@router.post("/crawl-tags", response_model=Response[None])
async def crawl_tags(current_admin: CurrentAdmin, redis: RedisDep) -> Response[None]:
    await redis.enqueue_job("crawl_tags")
    return Response(status_code=200, success=True, message="Crawl tags job enqueued", data=None)

@router.post("/crawl-book-statuses", response_model=Response[None])
async def crawl_book_statuses(current_admin: CurrentAdmin, redis: RedisDep) -> Response[None]:
    await redis.enqueue_job("crawl_book_statuses")
    return Response(status_code=200, success=True, message="Crawl book statuses job enqueued", data=None)

@router.post("/crawl-books", response_model=Response[None])
async def crawl_books(current_admin: CurrentAdmin, redis: RedisDep, limit: int = 20) -> Response[None]:
    await redis.enqueue_job("crawl_all_books", limit=limit)
    return Response(status_code=200, success=True, message="Crawl books job enqueued", data=None)