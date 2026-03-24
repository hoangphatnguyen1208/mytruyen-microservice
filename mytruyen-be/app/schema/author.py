from sqlmodel import SQLModel
from datetime import datetime
import uuid

class AuthorBase(SQLModel):
    name: str
    local_name: str | None = None
    avatar: str | None = None

class AuthorCreate(AuthorBase):
    pass

class AuthorPublic(AuthorBase):
    id: uuid.UUID
    created_at: datetime
    updated_at: datetime

class AuthorUpdate(SQLModel):
    name: str | None = None
    local_name: str | None = None
    avatar: str | None = None