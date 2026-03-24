"""
The HTTP 404 Not Found response status code indicates that the server cannot find the requested resource.
"""

import fastapi
from fastapi import HTTPException

from app.utilities.messages.exceptions.http.exc_details import (
    http_404_author_details,
    http_404_chapter_details,
    http_404_email_details,
    http_404_id_details,
    http_404_status_details,
    http_404_username_details,
    http_404_book_details,
    http_404_genre_details,
    http_404_chapter_content_details,
    http_404_tag_details,
)


def http_exc_404_email_not_found_request(email: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_email_details(email=email),
    )


def http_exc_404_id_not_found_request(id: int):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_id_details(id=id),
    )


def http_exc_404_username_not_found_request(username: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_username_details(username=username),
    )


def http_exc_404_book_not_found_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_book_details(string=string),
    )


def http_exc_404_genre_not_found(genre_id: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_genre_details(genre_id=genre_id),
    )

def http_exc_404_chapter_not_found_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_chapter_details(string=string),
    )

def http_exc_404_chapter_content_not_found_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_chapter_content_details(string=string),
    )

def http_exc_404_tag_not_found_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_tag_details(string=string),
    )

def http_exc_404_author_not_found_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_author_details(string=string),
    )

def http_exc_404_status_not_found_request(string: str):
    raise HTTPException(
        status_code=fastapi.status.HTTP_404_NOT_FOUND,
        detail=http_404_status_details(string=string),
    )   