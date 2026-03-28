"""Authentication middleware stub for Code IQ server."""
from __future__ import annotations

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response


class AuthMiddleware(BaseHTTPMiddleware):
    """No-op auth middleware. Replace dispatch logic to add authentication."""

    async def dispatch(self, request: Request, call_next):
        # Future: validate request.headers.get("Authorization")
        # request.state.user = validated_user
        response = await call_next(request)
        return response
