from fastapi import APIRouter

from app.api.v1 import (
    arq,
    author,
    book,
    book_status,
    chapter,
    genre,
    rabbitmq,
    search,
    tag,
)

api_router = APIRouter()

api_router.include_router(book.router)
api_router.include_router(genre.router)
api_router.include_router(chapter.router)
api_router.include_router(tag.router)
api_router.include_router(author.router)
api_router.include_router(book_status.router)
api_router.include_router(arq.router)
api_router.include_router(search.router)
api_router.include_router(rabbitmq.router)
