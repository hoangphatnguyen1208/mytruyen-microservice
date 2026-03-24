from sqlmodel import SQLModel
from pydantic import EmailStr
from app.models import user_role as UserRole

import uuid

class UserBase(SQLModel):
    id: uuid.UUID
    email: str
    full_name: str | None = None
    is_active: bool = True

class UserCreate(SQLModel):
    email: str
    password: str
    role: UserRole = UserRole.USER

class UserPublic(UserBase):
    pass

class UserUpdate(SQLModel):
    full_name: str | None = None