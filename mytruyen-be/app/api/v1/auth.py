from fastapi import APIRouter, Depends
from fastapi.security import OAuth2PasswordRequestForm
from typing import Annotated
from datetime import datetime, timedelta


from app.core.config import settings
from app.core.security import create_access_token, create_refresh_token
from app.api.deps import SessionDep

from app.schema.auth import Message, Token, RefreshTokenRequest, Register, Login
from app.schema.response import Response

from app.service import auth as auth_service
from app.crud import user as user_crud

from app.utilities.exceptions.http.exc_400 import (
    http_exc_400_credentials_bad_signin_request,
    http_exc_400_bad_email_request
)

router = APIRouter(prefix="/auth", tags=["auth"])

@router.post("/login/access-token", response_model=Token)
async def login_access_token(session: SessionDep, form_data: Annotated[OAuth2PasswordRequestForm, Depends()]) -> Token:
    user = await auth_service.authenticate(session, form_data.username, form_data.password)
    if not user:
        raise http_exc_400_credentials_bad_signin_request()
    refresh_token = create_refresh_token()
    refresh_token_expires_at = timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS) + datetime.now()
    await auth_service.save_refresh_token(session, str(user.id), refresh_token, expires_at=refresh_token_expires_at)
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    token = Token(
        access_token=create_access_token(
            data = {
                "id": str(user.id),
                "email": user.email,
                "role": user.role.value
            }, 
            expires_delta=access_token_expires
        ),
        refresh_token=refresh_token,
        token_type="bearer"
    )
    return token

@router.post("/login", response_model=Response[Token])
async def login(session: SessionDep, login_data: Login) -> Response[Token]:
    user = await auth_service.authenticate(session, login_data.email, login_data.password)
    print("User:", user)
    if not user:
        raise http_exc_400_credentials_bad_signin_request()
    refresh_token = create_refresh_token()
    refresh_token_expires_at = timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS) + datetime.now() 
    await auth_service.save_refresh_token(session, str(user.id), refresh_token, expires_at= refresh_token_expires_at)
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    token = Token(
        access_token=create_access_token(
            data = {
                "id": str(user.id),
                "email": user.email,
                "role": user.role.value
            }, 
            expires_delta=access_token_expires
        ),
        refresh_token=refresh_token,
        token_type="bearer"
    )
    return Response(status_code=200, success=True, message="Login successful", data=token)

@router.post("/register", response_model=Response[Message])
async def register(session: SessionDep, register_data: Register) -> Response[Message]:
    existing_user = await user_crud.get_user_by_email(session, register_data.email)
    if existing_user:
        raise http_exc_400_bad_email_request(email=register_data.email)
    user_create = user_crud.UserCreate(
        email=register_data.email,
        password=register_data.password
    )
    user = await user_crud.create_user(session, user_create)
    return Response(status_code=201, success=True, message="User registered successfully", data=Message(message="User registered successfully"))

@router.get("/test", response_model=Response[dict])
async def test() -> Response[dict]:
    return Response(status_code=200, success=True, message="Test endpoint", data={"message": "Test endpoint"})

@router.post("/refresh-token", response_model=Response[Token])
async def refresh_token(session: SessionDep, refresh_data: RefreshTokenRequest) -> Response[Token]:
    access_token, refresh_token = await auth_service.rotate_refresh_token(session, refresh_data.refresh_token)
    return Response(status_code=200, success=True, message="Token refreshed successfully", data=Token(access_token=access_token, refresh_token=refresh_token, token_type="bearer"))
