from fastapi import FastAPI
from pydantic import BaseModel


class Health(BaseModel):
    status: str
    service: str


app = FastAPI(title="MyTruyen Search Service", version="0.1.0")


@app.get("/health", response_model=Health, tags=["operations"])
async def health() -> Health:
    return Health(status="up", service="search-service")
