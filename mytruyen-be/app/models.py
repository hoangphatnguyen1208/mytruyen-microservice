from sqlmodel import SQLModel, Field, Relationship
import uuid
from sqlalchemy import Column, DateTime, func, MetaData, Index, UniqueConstraint
from datetime import datetime, timezone
from enum import Enum
from sqlalchemy.dialects.postgresql import JSONB


naming_convention = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s"
}

metadata = MetaData(naming_convention=naming_convention)
SQLModel.metadata = metadata

class Author(SQLModel, table=True):
    __tablename__ = "author"
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    name: str = Field(index=True, unique=True, nullable=False)
    local_name: str | None = Field(default=None)
    avatar: str | None = Field(default=None)
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    updated_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False, onupdate=lambda: datetime.now(timezone.utc)),
        default_factory=lambda: datetime.now(timezone.utc)
    )

    books: list["Book"] = Relationship(back_populates="author")
class BookStatus(SQLModel, table=True):
    __tablename__ = "book_status"
    id: int = Field(default=None, primary_key=True)
    name: str = Field(index=True, unique=True, nullable=False)
    slug: str = Field(index=True, unique=True, nullable=False)
    description: str | None = Field(default=None)
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    updated_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False, onupdate=lambda: datetime.now(timezone.utc)),
        default_factory=lambda: datetime.now(timezone.utc)
    )

    books: list["Book"] = Relationship(back_populates="status")

class BookTag(SQLModel, table=True):
    __tablename__ = "book_tag_link"
    book_id: int = Field(foreign_key="book.id", nullable=False, primary_key=True, ondelete="CASCADE")
    tag_id: int = Field(foreign_key="tag.id", nullable=False, primary_key=True, ondelete="CASCADE")

class BookGenre(SQLModel, table=True):
    __tablename__ = "book_genre_link"
    genre_id: int = Field(foreign_key="genre.id", nullable=False, primary_key=True, ondelete="CASCADE")
    book_id: int = Field(foreign_key="book.id", nullable=False, primary_key=True, ondelete="CASCADE")

class Tag(SQLModel, table=True):
    __tablename__ = "tag"
    id: int | None = Field(default=None, primary_key=True)
    name: str = Field(index=True, unique=True, nullable=False)
    slug: str = Field(index=True, unique=True, nullable=False)
    type: str = Field(index=True, nullable=False)
    description: str | None = Field(default=None)
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    updated_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False, onupdate=lambda: datetime.now(timezone.utc)),
        default_factory=lambda: datetime.now(timezone.utc)
    )

    books: list["Book"] = Relationship(back_populates="tags", link_model=BookTag)
class Genre(SQLModel, table=True):
    __tablename__ = "genre"
    id: int | None = Field(default=None, primary_key=True)
    name: str = Field(index=True, unique=True, nullable=False)
    slug: str = Field(index=True, unique=True, nullable=False)
    description: str | None = Field(default=None)
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    updated_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False, onupdate=lambda: datetime.now(timezone.utc)),
        default_factory=lambda: datetime.now(timezone.utc)
    )

    books: list["Book"] = Relationship(back_populates="genres", link_model=BookGenre)

class Book(SQLModel, table=True):
    __tablename__ = "book"
    id: int | None = Field(default=None, primary_key=True)
    author_id: uuid.UUID | None = Field(default=None, foreign_key="author.id", nullable=True, index=True)
    creator_id: uuid.UUID = Field(nullable=False, index=True)
    name: str = Field(index=True, nullable=False)
    slug: str = Field(index=True, unique=True, nullable=False)
    kind: int = Field(index=True, nullable=False)
    sex: int = Field(index=True, nullable=False)
    status_id: int = Field(foreign_key="book_status.id", nullable=False)
    chapter_per_week: int = Field(default=0, nullable=False)
    published: bool = Field(default=False, nullable=False)
    latest_chapter: uuid.UUID | None = Field(default=None)
    synopsis: str = Field(nullable=False)
    view_count: int = Field(default=0, nullable=False)
    chapter_count: int = Field(default=0, nullable=False)
    word_count: int = Field(default=0, nullable=False)
    comment_count: int = Field(default=0, nullable=False)
    review_count: int = Field(default=0, nullable=False)
    average_rating: float = Field(default=0.0, nullable=False)
    bookmark_count: int = Field(default=0, nullable=False)
    poster: dict = Field(sa_column=Column(JSONB))
    note: str = Field(nullable=False)
    new_chap_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)

    )
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    updated_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False, onupdate=lambda: datetime.now(timezone.utc)),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    published_at: datetime | None = Field(
        default=None,
        sa_column=Column(DateTime(timezone=True), nullable=True)
    )

    tags: list[Tag] = Relationship(back_populates="books", link_model=BookTag, sa_relationship_kwargs={"lazy": "selectin"})
    genres: list[Genre] = Relationship(back_populates="books", link_model=BookGenre, sa_relationship_kwargs={"lazy": "selectin"})
    status: BookStatus = Relationship(back_populates="books", sa_relationship_kwargs={"lazy": "selectin"})
    author: Author = Relationship(back_populates="books", sa_relationship_kwargs={"lazy": "selectin"})
    creator: User = Relationship(back_populates="books", sa_relationship_kwargs={"lazy": "selectin"})
    chapters: list["Chapter"] = Relationship(back_populates="book", cascade_delete=True)
    
class Chapter(SQLModel, table=True):
    __tablename__ = "chapter"

    __table_args__ = (
        Index("idx_chapter_book_index", "book_id", "index"),  # composite index
        UniqueConstraint("book_id", "index", name="uq_chapter_book_index"),  # unique constraint
    )

    id: int | None = Field(default=None, primary_key=True)
    creator_id: uuid.UUID = Field(foreign_key="user.id", nullable=False, index=True, ondelete="CASCADE")
    book_id: int = Field(foreign_key="book.id", nullable=False, index=True, ondelete="CASCADE")
    name: str = Field(index=True, nullable=False)
    index: int = Field(nullable=False)
    word_count: int = Field(default=0, nullable=False)
    view_count: int = Field(default=0, nullable=False)
    comment_count: int = Field(default=0, nullable=False)
    published: bool = Field(default=False, nullable=False)
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    updated_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False, onupdate=lambda: datetime.now(timezone.utc)),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    published_at: datetime | None = Field(
        default=None,
        sa_column=Column(DateTime(timezone=True), nullable=True)
    )

    book: "Book" = Relationship(back_populates="chapters")
    comments: list["Comment"] = Relationship(back_populates="chapter", cascade_delete=True)
    chapter_content: "ChapterContent" = Relationship(back_populates="chapter", cascade_delete=True)
class ChapterContent(SQLModel, table=True):
    __tablename__ = "chapter_content"
    id: int | None = Field(default=None, primary_key=True)
    chapter_id: int = Field(foreign_key="chapter.id", nullable=False, unique=True, ondelete="CASCADE")
    content: str = Field(nullable=False)
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    updated_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False, onupdate=lambda: datetime.now(timezone.utc)),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    
    chapter: "Chapter" = Relationship(back_populates="chapter_content")
class Comment(SQLModel, table=True):
    __tablename__ = "comment"
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    user_id: uuid.UUID = Field(foreign_key="user.id", nullable=False, index=True, ondelete="CASCADE")
    chapter_id: int = Field(foreign_key="chapter.id", nullable=False, index=True, ondelete="CASCADE")
    parent_id: uuid.UUID | None = Field(default=None, foreign_key="comment.id")
    content: str = Field(nullable=False)
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    updated_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False, onupdate=lambda: datetime.now(timezone.utc)),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    
    chapter: "Chapter" = Relationship(back_populates="comments")
    parent: "Comment" = Relationship(back_populates="replies", sa_relationship_kwargs={"remote_side": "Comment.id"})
    replies: list["Comment"] = Relationship(back_populates="parent")
class Review(SQLModel, table=True):
    __tablename__ = "review"
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    user_id: uuid.UUID = Field(foreign_key="user.id", nullable=False, index=True, ondelete="CASCADE")
    book_id: int = Field(foreign_key="book.id", nullable=False, index=True, ondelete="CASCADE")
    rating: int = Field(nullable=False)
    content: str | None = Field(default=None)
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    updated_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False, onupdate=lambda: datetime.now(timezone.utc)),
        default_factory=lambda: datetime.now(timezone.utc)
    )

class RefreshToken(SQLModel, table=True):
    __tablename__ = "refresh_token"
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    user_id: uuid.UUID = Field(foreign_key="user.id", nullable=False, index=True, ondelete="CASCADE")
    token: str = Field(nullable=False, unique=True, index=True)
    created_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False),
        default_factory=lambda: datetime.now(timezone.utc)
    )
    expires_at: datetime = Field(
        sa_column=Column(DateTime(timezone=True), nullable=False)
    )
    revoked: bool = Field(default=False, nullable=False)
    revoked_at: datetime | None = Field(
        default=None,
        sa_column=Column(DateTime(timezone=True), nullable=True)
    )