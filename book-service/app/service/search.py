from meilisearch import Client as MeiliSearchClient
from app.models import Book
from app.schema.search import Data
from app.schema.response import Pagination
from sqlmodel.ext.asyncio.session import AsyncSession
from app.crud import book as crud_book


async def meilisearch_query(
    session: AsyncSession,
    meili_client: MeiliSearchClient,
    query: str,
    limit: int,
    page: int,
):
    index = meili_client.index("books")
    search_result = index.search(query, {"limit": limit, "offset": (page - 1) * limit})
    results = search_result.get("hits", [])
    books = []
    for book in results:
        db_book = await crud_book.get_book_by_id(session, book["id"])
        books.append(db_book)
    pagination = Pagination(
        page=page,
        size=limit,
        total_items=search_result.get("estimatedTotalHits", 0),
        total_pages=(search_result.get("estimatedTotalHits", 0) + limit - 1) // limit,
    )
    return books, pagination
