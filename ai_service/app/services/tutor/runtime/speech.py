"""How the tutor turns text into speech segments, and how a segment's audio
is keyed — shared by the live socket and the compile-time voice warm-up so
a prepared line is a cache hit in the lesson (design: cost review 2026-09-07).
"""
from __future__ import annotations

import hashlib
import re
from typing import List, Optional, Tuple

# The learner's own pace: fast / normal (medium) / slow / slower.
PACE_MULTIPLIER = {"slower": 0.7, "slow": 0.85, "normal": 1.0, "fast": 1.2}
PACE_ORDER = ["slower", "slow", "normal", "fast"]
# Spoken rhythm: a sentence per segment where possible, never a sentence
# split; definitions a touch slower.
TUTOR_SEGMENT_MAX_CHARS = 200
DEFINITION_PACE = 0.92
_DEFINITION_RE = re.compile(r"\b(means|is called|is defined|definition|refers to|in other words)\b", re.IGNORECASE)
_SENTENCE_END = re.compile(r"(?<=[.!?…।])\s+|\n+")


def tutor_segments(text_: str, max_chars: Optional[int] = None) -> List[Tuple[str, int, int]]:
    """(segment, first sentence index, sentence count): sentences packed up to
    `max_chars`, never cut mid-sentence, so the board can follow the words."""
    max_chars = max_chars or TUTOR_SEGMENT_MAX_CHARS
    sentences = [x.strip() for x in _SENTENCE_END.split((text_ or "").strip()) if x and x.strip()]
    out: List[Tuple[str, int, int]] = []
    buf, start, count = "", 0, 0
    for i, sent in enumerate(sentences):
        if buf and len(buf) + 1 + len(sent) > max_chars:
            out.append((buf, start, count))
            buf, start, count = sent, i, 1
        else:
            buf = f"{buf} {sent}".strip()
            count += 1
            if count == 1:
                start = i
    if buf:
        out.append((buf, start, count))
    return out


def step_pace(pace: str, delta: int) -> str:
    i = PACE_ORDER.index(pace) if pace in PACE_ORDER else PACE_ORDER.index("normal")
    return PACE_ORDER[max(0, min(len(PACE_ORDER) - 1, i + delta))]


def effective_pace(base_pace: float, learner_pace: str, segment: str = "") -> float:
    """Course/institute voice speed × the learner's pace × a touch slower on
    a definition, clamped to what the engines accept."""
    slow = DEFINITION_PACE if segment and _DEFINITION_RE.search(segment) else 1.0
    return round(max(0.5, min(2.0, float(base_pace or 1.0) * PACE_MULTIPLIER.get(learner_pace, 1.0) * slow)), 2)


def cache_key(provider: str, voice: str, lang: str, pace: str, text_: str) -> str:
    return hashlib.sha256(f"{provider}|{voice}|{lang}|{pace}|{text_}".encode("utf-8")).hexdigest()
