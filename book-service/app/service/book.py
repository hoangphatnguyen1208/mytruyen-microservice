from meilisearch import Client as MeiliSearchClient
from app.core.config import settings
from app.crud import (
    author as crud_author,
    book as crud_book,
    book_status as crud_book_status,
    genre as crud_genre,
    tag as crud_tag,
)
from app.models import Book
from app.schema.book import BookCreate, BookRegister, BookUpdate
from app.schema.search import Data, Message
from app.schema.response import Pagination
from app.utilities.exceptions.http.exc_400 import http_exc_400_bad_book_request
from app.utilities.exceptions.http.exc_404 import (
    http_exc_404_author_not_found_request,
    http_exc_404_book_not_found_request,
    http_exc_404_genre_not_found,
    http_exc_404_status_not_found_request,
    http_exc_404_tag_not_found_request,
)


async def get_books(
    session, skip: int, limit: int, sort: str | None = None, status: int | None = None
) -> tuple[list[Book], Pagination]:
    books = await crud_book.get_books(session, skip, limit, sort, status)
    total = await crud_book.get_book_count(session, status)
    pagination = Pagination(
        page=(skip // limit) + 1,
        size=limit,
        total_items=total,
        total_pages=(total + limit - 1) // limit,
    )
    return books, pagination


async def create_book(
    session,
    meili_client: MeiliSearchClient,
    creator_id: str,
    book_register: BookRegister,
) -> Book:
    existing_book = await crud_book.get_book_by_slug(session, book_register.slug)
    if existing_book:
        raise http_exc_400_bad_book_request(slug=book_register.slug)

    author = await crud_author.get_author_by_id(session, book_register.author_id)
    if not author:
        raise http_exc_404_author_not_found_request(string=book_register.author_id)

    for tag_id in book_register.tag_ids:
        tag = await crud_tag.get_tag_by_id(session, tag_id)
        if not tag:
            raise http_exc_404_tag_not_found_request(string=tag_id)

    book_status = await crud_book_status.get_book_status_by_id(
        session, book_register.status_id
    )
    if not book_status:
        raise http_exc_404_status_not_found_request(string=book_register.status_id)

    for genre_id in book_register.genre_ids:
        genre = await crud_genre.get_genre_by_id(session, genre_id)
        if not genre:
            raise http_exc_404_genre_not_found(genre_id=genre_id)

    book_in = BookCreate.model_validate(
        book_register, update={"creator_id": creator_id}
    )
    db_book = await crud_book.create_book(session, book_in)

    # Update Meilisearch index
    meili_client.index("books").add_documents(
        [{"id": db_book.id, "name": db_book.name, "author": db_book.author.name}]
    )
    return db_book


async def get_book_by_id(session, book_id: int) -> Book:
    db_book = await crud_book.get_book_by_id(session, book_id)
    if not db_book:
        raise http_exc_404_book_not_found_request(string=book_id)
    return db_book


async def get_book_by_slug(session, slug: str) -> Book:
    db_book = await crud_book.get_book_by_slug(session, slug)
    if not db_book:
        raise http_exc_404_book_not_found_request(string=slug)
    return db_book


async def update_book_by_id(
    session, meili_client: MeiliSearchClient, book_id: int, book: BookUpdate
) -> Book:
    db_book = await get_book_by_id(session, book_id)
    if not db_book:
        raise http_exc_404_book_not_found_request(string=book_id)
    new_book = await crud_book.update_book(session, db_book.id, book)

    # Update Meilisearch index
    meili_client.index("books").update_documents(
        [{"id": new_book.id, "name": new_book.name, "author": new_book.author.name}]
    )
    return new_book


async def update_book_by_slug(
    session, meili_client: MeiliSearchClient, slug: str, book: BookUpdate
) -> Book:
    db_book = await get_book_by_slug(session, slug)
    if not db_book:
        raise http_exc_404_book_not_found_request(string=slug)
    new_book = await crud_book.update_book(session, db_book.id, book)

    # Update Meilisearch index
    meili_client.index("books").update_documents(
        [{"id": new_book.id, "name": new_book.name, "author": new_book.author.name}]
    )
    return new_book


async def delete_book_by_id(
    session, meili_client: MeiliSearchClient, book_id: int
) -> None:
    db_book = await get_book_by_id(session, book_id)
    if not db_book:
        raise http_exc_404_book_not_found_request(string=book_id)
    await crud_book.delete_book(session, db_book.id)

    # Update Meilisearch index
    meili_client.index("books").delete_documents([db_book.id])


async def delete_book_by_slug(
    session, meili_client: MeiliSearchClient, slug: str
) -> None:
    db_book = await get_book_by_slug(session, slug)
    if not db_book:
        raise http_exc_404_book_not_found_request(string=slug)
    await crud_book.delete_book(session, db_book.id)

    # Update Meilisearch index
    meili_client.index("books").delete_documents([db_book.id])
