"""
The HTTP 403 Forbidden response status code indicates that the server understands the request but refuses to authorize it.
"""

import fastapi
from fastapi import HTTPException

from app.utilities.messages.exceptions.http.exc_details import http_403_forbidden_details


def http_exc_403_forbidden_request():
    raise HTTPException(
        status_code=fastapi.status.HTTP_403_FORBIDDEN,
        detail=http_403_forbidden_details(),
    )
