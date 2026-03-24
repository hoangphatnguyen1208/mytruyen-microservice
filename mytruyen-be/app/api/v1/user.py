from fastapi import APIRouter
from app.api.deps import SessionDep, CurrentAdmin, CurrentUser
from app.schema.response import Response, ResponseList

from app.crud import user as user_crud
from app.schema.user import UserCreate, UserPublic, UserUpdate
from app.utilities.exceptions.http.exc_400 import http_exc_400_user_bad_request
from app.utilities.exceptions.http.exc_404 import (
    http_exc_404_id_not_found_request, 
    http_exc_404_username_not_found_request, 
    http_exc_404_email_not_found_request
)
from app.utilities.exceptions.http.exc_403 import http_exc_403_forbidden_request

router = APIRouter(prefix="/users", tags=["user"])

@router.post("", response_model=Response[UserPublic], status_code=201)
async def create_user(session: SessionDep, current_admin: CurrentAdmin, user_in: UserCreate) -> Response[UserPublic]:
    existing_user = await user_crud.get_user_by_email(session, user_in.email)
    if existing_user:
        raise http_exc_400_user_bad_request(string=user_in.email)
    db_user = await user_crud.create_user(session, user_in)
    return Response(status_code=201, success=True, message="User created successfully", data=db_user)

@router.get("", response_model=ResponseList[UserPublic])
async def get_users(session: SessionDep) -> ResponseList[UserPublic]:
    users = await user_crud.get_users(session)
    return ResponseList(status_code=200, success=True, message="Users retrieved successfully", data=users)

@router.get("/{user_id}", response_model=Response[UserPublic])
async def get_user_by_id(session: SessionDep, user_id: str) -> Response[UserPublic]:
    user = await user_crud.get_user_by_id(session, user_id)
    if not user:
        raise http_exc_404_id_not_found_request(string=user_id)
    return Response(status_code=200, success=True, message="User retrieved successfully", data=user)

@router.get("/by-email/{email}", response_model=Response[UserPublic])
async def get_user_by_email(session: SessionDep, email: str) -> Response[UserPublic]:
    user = await user_crud.get_user_by_email(session, email)
    if not user:
        raise http_exc_404_email_not_found_request(string=email)
    return Response(status_code=200, success=True, message="User retrieved successfully", data=user)

@router.get("/by-username/{username}", response_model=Response[UserPublic])
async def get_user_by_username(session: SessionDep, username: str) -> Response[UserPublic]:
    user = await user_crud.get_user_by_username(session, username)
    if not user:
        raise http_exc_404_username_not_found_request(string=username)
    return Response(status_code=200, success=True, message="User retrieved successfully", data=user)

@router.patch("/{user_id}", response_model=Response[UserPublic])
async def update_user(session: SessionDep, user_id: str, user_in: UserUpdate, current_user: CurrentUser) -> Response[UserPublic]:
    existing_user = await user_crud.get_user_by_id(session, user_id)
    if not existing_user:
        raise http_exc_404_id_not_found_request(string=user_id)
    if str(existing_user.id) != str(current_user.id) and not current_user.is_admin:
        raise http_exc_403_forbidden_request()
    updated_user = await user_crud.update_user(session, user_id, user_in)
    return Response(status_code=200, success=True, message="User updated successfully", data=updated_user)

@router.delete("/{user_id}", response_model=Response[None])
async def delete_user(session: SessionDep, user_id: str, current_user: CurrentUser) -> Response[None]:
    existing_user = await user_crud.get_user_by_id(session, user_id)
    if not existing_user:
        raise http_exc_404_id_not_found_request(string=user_id)
    if str(existing_user.id) != str(current_user.id) and not current_user.is_admin:
        raise http_exc_403_forbidden_request()
    await user_crud.delete_user(session, user_id)
    return Response(status_code=200, success=True, message="User deleted successfully", data=None)



