from sqlmodel import SQLModel, Field
from datetime import datetime

class GenreBase(SQLModel):
    name: str
    slug: str
    description: str | None = Field(default=None)

class GenreCreate(GenreBase):
    pass

class GenreRegister(GenreBase):
    pass

class GenreUpdate(SQLModel):
    name: str | None = None
    slug: str | None = None
    description: str | None = None

class GenrePublic(GenreBase):
    id: int
    created_at: datetime
    updated_at: datetime
