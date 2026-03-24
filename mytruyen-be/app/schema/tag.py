from datetime import datetime

from sqlmodel import SQLModel


class TagBase(SQLModel):
    name: str
    slug: str
    type: str
    description: str | None = None


class TagCreate(TagBase):
    pass


class TagUpdate(TagBase):
    name: str | None = None
    slug: str | None = None
    description: str | None = None


class TagPublic(TagBase):
    id: int
    created_at: datetime
    updated_at: datetime