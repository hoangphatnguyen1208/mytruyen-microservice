from sqlmodel import SQLModel, Field
from pydantic import ConfigDict
from app.schema.user import UserPublic
from app.schema.genre import GenrePublic
from app.schema.author import AuthorPublic

from datetime import datetime
import uuid

class BookBase(SQLModel):
    name: str
    slug: str
    kind: int
    sex: int
    status_id: int
    chapter_per_week: int
    published: bool
    synopsis: str
    note: str
    poster: dict
    chapter_count: int = 0
    word_count: int = 0
    
class BookRegister(BookBase):
    id: int | None = None
    author_id: uuid.UUID | None = None
    genre_ids: list[int]
    tag_ids: list[int]

class BookCreate(BookBase):
    id: int | None = None
    author_id: uuid.UUID | None = None
    creator_id: uuid.UUID
    genre_ids: list[int]

class BookPublic(BookBase):
    id: int
    latest_chapter: str | None
    view_count: int
    chapter_count: int
    word_count: int
    comment_count: int
    review_count: int
    average_rating: float
    bookmark_count: int
    new_chap_at: datetime | None
    created_at: datetime
    updated_at: datetime
    published_at: datetime | None
    
    author: AuthorPublic | None
    creator: UserPublic
    genres: list[GenrePublic] 

class BookUpdate(SQLModel):
    name: str | None = None
    slug: str | None = None
    kind: int | None = None
    sex: int | None = None
    status_id: int | None = None
    chapter_per_week: int | None = None
    published: bool | None = None
    synopsis: str | None = None
    note: str | None = None
    genre_ids: list[int] | None = None
    tag_ids: list[int] | None = None
    poster: dict | None = None
    chapter_count: int | None = None
    view_count: int | None = None
    word_count: int | None = None
    comment_count: int | None = None
    review_count: int | None = None
    average_rating: float | None = None
    bookmark_count: int | None = None



