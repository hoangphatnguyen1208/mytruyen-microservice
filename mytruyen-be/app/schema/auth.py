from datetime import datetime

from sqlmodel import SQLModel

class Token(SQLModel):
    access_token: str
    refresh_token: str = None
    token_type: str


class Login(SQLModel):
    email: str
    password: str

class Register(SQLModel):
    email: str
    password: str

class Message(SQLModel):
    message: str

class RefreshTokenRequest(SQLModel):
    refresh_token: str