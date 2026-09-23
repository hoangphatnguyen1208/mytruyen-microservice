from fastapi import APIRouter, HTTPException, Query, Request

from app.service import SearchService

router = APIRouter(prefix="/api/v1/search", tags=["search"])


@router.get("/meili")
async def search(request: Request, query: str = Query(max_length=500),
                 limit: int = Query(default=10, ge=1, le=100),
                 page: int = Query(default=1, ge=1, le=10000)):
    service = SearchService(request.app.state.client, request.app.state.settings)
    return await service.search(query, limit, page)


@router.get("/hybrid")
@router.post("/audio")
@router.post("/youtube")
async def disabled():
    raise HTTPException(503, "Tính năng này tạm thời bị vô hiệu hóa.")
