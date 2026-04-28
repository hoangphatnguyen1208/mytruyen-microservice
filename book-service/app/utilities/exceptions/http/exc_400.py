"""
The HyperText Transfer Protocol (HTTP) 400 Bad Request response status code indicates that the server
cannot or will not process the request due to something that is perceivedto be a client error
(for example, malformed request syntax, invalid request message framing, or deceptive request routing).
"""
import fastapi
from fastapi import HTTPException

from app.utilities.messages.exceptions.http.exc_details import (
    http_400_email_details,
    http_400_sigin_credentials_details,
    http_400_signup_credentials_details,
    http_400_status_details,
    http_400_username_details,
    http_400_book_details,
    http_400_genre_details,
    http_400_chapter_details,
    http_400_chapter_content_details,
    http_400_tag_details,
    http_400_author_details,
)


def http_exc_400_credentials_bad_signup_request():
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_signup_credentials_details(),
    )


def http_exc_400_credentials_bad_signin_request():
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_sigin_credentials_details(),
    )


def http_exc_400_bad_username_request(username: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_username_details(username=username),
    )


def http_exc_400_bad_email_request(email: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_email_details(email=email),
    )


def http_exc_400_bad_book_request(slug: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_book_details(string=slug),
    )


def http_exc_400_genre_bad_request(slug: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_genre_details(string=slug),
    )

def http_exc_400_chapter_bad_request(slug: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_chapter_details(string=slug),
    )

def http_exc_400_chapter_content_bad_request(slug: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_chapter_content_details(string=slug),
    )

def http_exc_400_tag_bad_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_tag_details(string=string),
    )

def http_exc_400_author_bad_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_author_details(string=string),
    )

def http_exc_400_status_bad_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_status_details(string=string),
    )   

def http_exc_400_user_bad_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_400_BAD_REQUEST,
        detail=http_400_email_details(email=string),
    )