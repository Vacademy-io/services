"""Public 3-minute taste of the tutor for unauthenticated visitors (tutezy.ai).

A visitor gives a name and picks one of a few pre-compiled topics; we open a
real tutor session on the demo batch under a guest identity, mint a
short-lived guest JWT (same secret the socket verifies), cap the lesson at a
few minutes and do not bill it. Abuse controls: one session per IP per day
(hashed), a global daily cap, and a kill switch — all platform settings.
"""
from __future__ import annotations

import base64
import hashlib
import ipaddress
import json
import logging
import time
import uuid
from typing import Any, Dict, List, Optional

from fastapi import Request
from jose import jwt
from sqlalchemy import text
from sqlalchemy.orm import Session

from ...config import get_settings
from ..platform_settings_service import get_platform_setting

logger = logging.getLogger(__name__)

GUEST_TOKEN_TTL_SECONDS = 20 * 60
DEFAULT_TOPICS = json.dumps([])

_ENSURE = [
    """
    CREATE TABLE IF NOT EXISTS tutor_demo_grant (
        id                VARCHAR(36) PRIMARY KEY,
        ip_hash           VARCHAR(64) NOT NULL,
        visitor_name      VARCHAR(80),
        topic_key         VARCHAR(64),
        tutor_session_id  VARCHAR(36),
        user_agent        VARCHAR(255),
        created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
    )
    """,
    "CREATE INDEX IF NOT EXISTS idx_tutor_demo_grant_ip ON tutor_demo_grant(ip_hash, created_at)",
    "CREATE INDEX IF NOT EXISTS idx_tutor_demo_grant_day ON tutor_demo_grant(created_at)",
]


def ensure_tutor_demo_schema(db: Session) -> None:
    try:
        for stmt in _ENSURE:
            db.execute(text(stmt))
        db.commit()
        logger.info("tutor_demo_grant schema ensured.")
    except Exception as exc:  # noqa: BLE001
        db.rollback()
        logger.warning("ensure_tutor_demo_schema failed: %s", exc)


# ── configuration (platform settings, editable in the health portal) ────────

def config(db: Optional[Session] = None) -> Dict[str, Any]:
    def g(key: str, default: Any) -> Any:
        try:
            v = get_platform_setting(key, default=default, db=db)
            return default if v in (None, "") else v
        except Exception:  # noqa: BLE001
            return default
    try:
        topics = json.loads(str(g("tutor.demo.topics", DEFAULT_TOPICS)) or "[]")
    except Exception:  # noqa: BLE001
        topics = []
    topics = [t for t in topics if isinstance(t, dict) and t.get("key") and t.get("slide_id")]
    return {
        "enabled": str(g("tutor.demo.enabled", "false")).lower() in ("1", "true", "yes", "on"),
        "institute_id": str(g("tutor.demo.institute_id", "")),
        "package_session_id": str(g("tutor.demo.package_session_id", "")),
        "topics": topics,
        "minutes": max(1, min(10, int(float(g("tutor.demo.minutes", 3))))),
        "per_ip_per_day": max(0, int(float(g("tutor.demo.per_ip_per_day", 1)))),
        "daily_cap": max(0, int(float(g("tutor.demo.daily_cap", 200)))),
        "teacher_name": str(g("tutor.demo.teacher_name", "")),
    }


def public_topics(db: Optional[Session] = None) -> Dict[str, Any]:
    c = config(db)
    ready = bool(c["enabled"] and c["institute_id"] and c["package_session_id"] and c["topics"])
    return {"enabled": ready, "minutes": c["minutes"],
            "topics": [{"key": t["key"], "title": t.get("title") or t["key"], "emoji": t.get("emoji") or "",
                        "language": t.get("language") or "en"} for t in c["topics"]]}


# ── abuse controls ───────────────────────────────────────────────────────────

def client_ip(request: Request) -> str:
    fwd = request.headers.get("x-forwarded-for") or request.headers.get("cf-connecting-ip") or ""
    ip = (fwd.split(",")[0].strip() if fwd else "") or (request.client.host if request.client else "") or "0.0.0.0"
    try:
        parsed = ipaddress.ip_address(ip)
        # IPv6: bucket by /64 so one household is one visitor.
        if isinstance(parsed, ipaddress.IPv6Address):
            ip = str(ipaddress.ip_network(f"{ip}/64", strict=False).network_address)
    except ValueError:
        pass
    return ip


def ip_hash(ip: str) -> str:
    salt = (get_settings().jwt_secret_key or "tutezy")[:16]
    return hashlib.sha256(f"{salt}:{ip}".encode()).hexdigest()


def grant_allowed(db: Session, *, iph: str, per_ip_per_day: int, daily_cap: int) -> Optional[str]:
    """None when allowed; otherwise the visitor-facing reason."""
    if daily_cap:
        n = db.execute(text("SELECT count(*) FROM tutor_demo_grant WHERE created_at > now() - interval '1 day'")).scalar() or 0
        if int(n) >= daily_cap:
            return "The free lessons for today are all taken. Book a demo and we will run one for you."
    if per_ip_per_day:
        n = db.execute(text("SELECT count(*) FROM tutor_demo_grant WHERE ip_hash = :h AND created_at > now() - interval '1 day'"),
                       {"h": iph}).scalar() or 0
        if int(n) >= per_ip_per_day:
            return "You have already had your free lesson today. Book a demo to see the whole thing."
    return None


def record_grant(db: Session, *, iph: str, name: str, topic_key: str, tutor_session_id: str, user_agent: str) -> None:
    db.execute(text("""
        INSERT INTO tutor_demo_grant (id, ip_hash, visitor_name, topic_key, tutor_session_id, user_agent)
        VALUES (:id, :h, :n, :t, :s, :ua)
    """), {"id": str(uuid.uuid4()), "h": iph, "n": name[:80], "t": topic_key[:64], "s": tutor_session_id, "ua": user_agent[:255]})
    db.commit()


# ── guest identity ───────────────────────────────────────────────────────────

def guest_user_id() -> str:
    return f"demo-{uuid.uuid4()}"


def mint_guest_token(*, user_id: str, tutor_session_id: str, institute_id: str) -> str:
    """Signed like a platform access token (the socket verifies with the same
    secret) but carrying only what the tutor socket checks: `user`."""
    settings = get_settings()
    secret = settings.jwt_secret_key
    if len(secret) % 4:
        secret += "=" * (4 - len(secret) % 4)
    now = int(time.time())
    claims = {"user": user_id, "sub": user_id, "demo": tutor_session_id, "institute_id": institute_id,
              "authorities": {}, "iat": now, "exp": now + GUEST_TOKEN_TTL_SECONDS}
    return jwt.encode(claims, base64.b64decode(secret), algorithm=settings.jwt_algorithm)


def sanitize_name(name: str) -> str:
    import unicodedata
    # Letters, digits and combining marks (Devanagari vowel signs are marks, not alnum).
    cleaned = "".join(ch for ch in (name or "")
                      if ch.isalnum() or ch in " .'-" or unicodedata.category(ch).startswith("M")).strip()
    return (cleaned[:40] or "Friend").split(" ")[0].capitalize()


def topic_by_key(topics: List[Dict[str, Any]], key: str) -> Optional[Dict[str, Any]]:
    for t in topics:
        if t.get("key") == key:
            return t
    return None
