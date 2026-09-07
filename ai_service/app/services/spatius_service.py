"""Spatius (spatius.ai) teacher avatar — premium layer (owner decision 2026-09-07).

The avatar is rendered on the learner's device by Spatius AvatarKit; their
Motion Server turns the teacher's speech audio into a small motion stream.
This module holds the server side: minting short-lived session tokens for a
lesson, creating an avatar from the institute's teacher photo, and polling
that job. Env: SPATIUS_API_KEY, SPATIUS_APP_ID, optional SPATIUS_CONSOLE_HOST
(default console.us-west.spatius.ai). Nothing here runs when the keys are
absent — the feature stays dark.
"""
from __future__ import annotations

import logging
import os
import time
from typing import Any, Dict, Optional

import httpx

logger = logging.getLogger(__name__)

DEFAULT_CONSOLE_HOST = "console.us-west.spatius.ai"
SESSION_TOKEN_TTL_SECONDS = 2 * 60 * 60
# Per-minute vendor cost (overage rate, Starter/Builder plans), for usage rows.
SPATIUS_USD_PER_MINUTE = 0.0072


def _api_key() -> str:
    return (os.environ.get("SPATIUS_API_KEY") or "").strip()


def app_id() -> str:
    return (os.environ.get("SPATIUS_APP_ID") or "").strip()


def base_url() -> str:
    host = (os.environ.get("SPATIUS_CONSOLE_HOST") or DEFAULT_CONSOLE_HOST).strip().rstrip("/")
    return f"https://{host}/v1/console"


def available() -> bool:
    return bool(_api_key() and app_id())


def _headers() -> Dict[str, str]:
    return {"X-API-Key": _api_key(), "Content-Type": "application/json"}


async def mint_session_token(ttl_seconds: int = SESSION_TOKEN_TTL_SECONDS) -> Dict[str, Any]:
    """A session token for one lesson (Direct mode: the browser connects to
    the Motion Server with it). expireAt must be within 24 hours."""
    if not available():
        raise RuntimeError("Spatius is not configured")
    expire_at = int(time.time()) + max(60, min(ttl_seconds, 23 * 3600))
    async with httpx.AsyncClient(timeout=20.0) as client:
        r = await client.post(f"{base_url()}/session-tokens", headers=_headers(), json={"expireAt": expire_at})
    if r.status_code >= 300:
        raise RuntimeError(f"Spatius session token HTTP {r.status_code}: {r.text[:200]}")
    data = r.json()
    token = data.get("sessionToken") or data.get("session_token")
    if not token:
        raise RuntimeError("Spatius returned no session token")
    return {"session_token": token, "expires_at": expire_at, "app_id": app_id()}


async def create_avatar(image_url: str, name: Optional[str] = None) -> Dict[str, Any]:
    """Queue an avatar from one clear face photo (public https URL, JPEG/PNG
    ≤ 5 MiB, shorter side ≥ 340 px). Returns {job_id, status}."""
    if not available():
        raise RuntimeError("Spatius is not configured")
    body: Dict[str, Any] = {"imageUrl": image_url}
    if name:
        body["name"] = name[:80]
    async with httpx.AsyncClient(timeout=30.0) as client:
        r = await client.post(f"{base_url()}/avatars", headers=_headers(), json=body)
    if r.status_code == 402:
        raise RuntimeError("Spatius avatar creations are used up for this plan")
    if r.status_code >= 300:
        raise RuntimeError(f"Spatius avatar creation HTTP {r.status_code}: {r.text[:200]}")
    data = r.json()
    return {"job_id": data.get("jobId") or data.get("id"), "status": data.get("status") or "queued", "raw": data}


async def avatar_job(job_id: str) -> Dict[str, Any]:
    """{status: queued|processing|succeeded|failed, avatar_id, error}."""
    if not available():
        raise RuntimeError("Spatius is not configured")
    async with httpx.AsyncClient(timeout=20.0) as client:
        r = await client.get(f"{base_url()}/avatar-jobs/{job_id}", headers=_headers())
    if r.status_code >= 300:
        raise RuntimeError(f"Spatius avatar job HTTP {r.status_code}: {r.text[:200]}")
    data = r.json()
    avatar = data.get("avatar") if isinstance(data.get("avatar"), dict) else {}
    return {"status": data.get("status") or "unknown",
            "avatar_id": data.get("avatarId") or data.get("avatar_id") or avatar.get("id") or avatar.get("avatarId"),
            "error": data.get("error") or data.get("failureReason"), "raw": data}
