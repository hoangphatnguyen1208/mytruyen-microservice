from sqlmodel import SQLModel, Field


class RabbitMQB(SQLModel):
    book_id: int
