from sqlmodel import SQLModel
from datetime import datetime

class BookStatusBase(SQLModel):
    name: str
    slug: str
    description: str | None = None

class BookStatusCreate(BookStatusBase):
    pass

class BookStatusUpdate(BookStatusBase):
    name: str | None = None
    slug: str | None = None
    description: str | None = None

class BookStatusPublic(BookStatusBase):
    id: int
    created_at: datetime
    updated_at: datetime



