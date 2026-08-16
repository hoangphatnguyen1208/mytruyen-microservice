from sqlmodel import SQLModel


class Data(SQLModel):
    id: int
    name: str
    author: str


class Message(SQLModel):
    type: str
    data: Data
