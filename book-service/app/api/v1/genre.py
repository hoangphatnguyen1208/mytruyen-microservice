import uuid
from fastapi import APIRouter, HTTPException
from uuid import UUID

from app.models import Genre
from app.api.deps import SessionDep, CurrentAdmin
from app.schema.genre import GenreCreate, GenreUpdate, GenrePublic
from app.crud import genre as genre_crud
from app.schema.response import ResponseList, Response

from app.utilities.exceptions.http.exc_404 import http_exc_404_genre_not_found
from app.utilities.exceptions.http.exc_400 import http_exc_400_genre_bad_request

router = APIRouter(prefix="/genres", tags=["Genre"])

@router.post("", response_model=Response[GenrePublic], status_code=201)
async def create_genre(session: SessionDep, genre_in: GenreCreate, current_admin: CurrentAdmin):
    existing_genre_slug = await genre_crud.get_genre_by_slug(session, genre_in.slug)
    if existing_genre_slug:
        raise http_exc_400_genre_bad_request(slug=genre_in.slug)
    genre = await genre_crud.create_genre(session, genre_in)
    return Response(status_code=201, success=True, message="Genre created successfully", data=genre)

@router.get("", response_model=ResponseList[GenrePublic])
async def read_genres(session: SessionDep):
    genres = await genre_crud.get_genres(session)
    return ResponseList(status_code=200, success=True, message="Genres retrieved successfully", data=genres)

@router.get("/{slug}", response_model=Response[GenrePublic])
async def read_genre(session: SessionDep, slug: str):
    genre_db =  await genre_crud.get_genre_by_slug(session, slug)
    if not genre_db:
        raise http_exc_404_genre_not_found(genre_id=slug)
    return Response(status_code=200, success=True, message="Genre retrieved successfully", data=genre_db)

@router.patch("/update/{genre_id}", response_model=Response[GenrePublic])
async def update_genre(session: SessionDep, genre_id: int, genre_in: GenreUpdate, current_admin: CurrentAdmin):
    genre_db = await genre_crud.get_genre_by_id(session, genre_id)
    if not genre_db:
        raise http_exc_404_genre_not_found(genre_id=genre_id)
    genre = await genre_crud.update_genre(session, genre_id, genre_in)
    return Response(status_code=200, success=True, message="Genre updated successfully", data=genre)

@router.delete("/delete/{genre_id}", response_model=Response[dict])
async def delete_genre(session: SessionDep, genre_id: int, current_admin: CurrentAdmin):
    genre_db = await genre_crud.get_genre_by_id(session, genre_id)
    if not genre_db:
        raise http_exc_404_genre_not_found(genre_id=genre_id)
    await genre_crud.delete_genre(session, genre_id)
    return Response(status_code=200, success=True, message="Genre deleted successfully")
