from sqlmodel.ext.asyncio.session import AsyncSession
from sqlmodel import select

from app.models import Genre
from app.schema.genre import GenreCreate, GenreUpdate
import uuid

async def create_genre(session: AsyncSession, genre_create: GenreCreate) -> Genre:
    db_genre = Genre.model_validate(genre_create)
    session.add(db_genre)
    await session.commit()
    await session.refresh(db_genre)
    return db_genre

async def get_genres(session: AsyncSession) -> list[Genre]:
    statement = select(Genre)
    genres = await session.exec(statement)
    return genres.all()

async def get_genre_by_id(session: AsyncSession, genre_id: int) -> Genre | None:
    genre = await session.get(Genre, genre_id)
    return genre

async def get_genre_by_slug(session: AsyncSession, slug: str) -> Genre | None:
    statement = select(Genre).where(Genre.slug == slug)
    genres = await session.exec(statement)
    return genres.first()

async def update_genre(session: AsyncSession, genre_id: int, genre: GenreUpdate) -> Genre | None:
    genre_data = genre.model_dump(exclude_unset=True)
    current_genre = await get_genre_by_id(session, genre_id)
    current_genre.sqlmodel_update(genre_data)
    session.add(current_genre)
    await session.commit()
    await session.refresh(current_genre)
    return current_genre

async def delete_genre(session: AsyncSession, genre_id: int) -> bool:
    genre = await get_genre_by_id(session, genre_id)
    await session.delete(genre)
    await session.commit()
    return True