import uuid
from sqlmodel import SQLModel
from datetime import datetime

class ChapterBase(SQLModel):
    index: int
    name: str
    word_count: int
    published: bool

class ChapterRegister(ChapterBase):
    id: int | None = None

class ChapterCreate(ChapterBase):
    id: int | None = None
    book_id: int
    creator_id: uuid.UUID

class ChapterUpdate(ChapterBase):
    book_id: int | None = None
    index: int | None = None
    name: str | None = None
    published: bool | None = None
    word_count: int | None = None

class ChapterPublic(ChapterBase):
    id: int
    published_at: datetime | None
    view_count: int
    comment_count: int
    book_id: int
    created_at: datetime
    updated_at: datetime

class ChapterContentBase(SQLModel):
    content: str

class ChapterContentCreate(ChapterContentBase):
    chapter_id: int
class ChapterContentRegister(ChapterContentBase):
    pass

class ChapterContentUpdate(ChapterContentBase):
    content: str | None = None
    chapter_id: int | None = None

class ChapterContentPublic(ChapterContentBase):
    id: int
    created_at: datetime
    updated_at: datetime
