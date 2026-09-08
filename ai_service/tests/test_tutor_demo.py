"""Public demo lesson: guest token, name hygiene, abuse controls."""
from app.services.tutor import demo
from app.core.security import decode_access_token


def test_guest_token_verifies_with_the_platform_secret():
    tok = demo.mint_guest_token(user_id="demo-abc", tutor_session_id="s1", institute_id="i1")
    claims = decode_access_token(tok)
    assert claims and claims["user"] == "demo-abc" and claims["demo"] == "s1"


def test_sanitize_name_keeps_first_word_only():
    assert demo.sanitize_name("priya sharma") == "Priya"
    assert "<" not in demo.sanitize_name("  <script>alert(1)</script> ")
    assert demo.sanitize_name("") == "Friend"
    assert demo.sanitize_name("राहुल") in ("राहुल", "Friend")


class _DB:
    def __init__(self, counts):
        self.counts = list(counts)
        self.sql = []
    def execute(self, stmt, params=None):
        self.sql.append(str(stmt)); n = self.counts.pop(0)
        class R:
            def scalar(_): return n
        return R()
    def commit(self): pass


def test_grant_limits():
    assert demo.grant_allowed(_DB([0, 0]), iph="h", per_ip_per_day=1, daily_cap=200) is None
    assert "today" in (demo.grant_allowed(_DB([200]), iph="h", per_ip_per_day=1, daily_cap=200) or "")
    assert "already" in (demo.grant_allowed(_DB([5, 1]), iph="h", per_ip_per_day=1, daily_cap=200) or "")
    assert demo.grant_allowed(_DB([]), iph="h", per_ip_per_day=0, daily_cap=0) is None


def test_ip_bucketing():
    class Req:
        headers = {"x-forwarded-for": "2001:db8:1234:5678:abcd::1, 10.0.0.1"}
        client = None
    assert demo.client_ip(Req()) == "2001:db8:1234:5678::"
    Req.headers = {}
    class C: host = "1.2.3.4"
    Req.client = C()
    assert demo.client_ip(Req()) == "1.2.3.4"
    assert demo.ip_hash("1.2.3.4") != demo.ip_hash("1.2.3.5")


def test_public_topics_requires_everything(monkeypatch):
    monkeypatch.setattr(demo, "config", lambda db=None: {"enabled": True, "institute_id": "", "package_session_id": "p",
                                                         "topics": [{"key": "k", "slide_id": "s", "title": "T"}], "minutes": 3,
                                                         "per_ip_per_day": 1, "daily_cap": 200, "teacher_name": ""})
    assert demo.public_topics()["enabled"] is False
    monkeypatch.setattr(demo, "config", lambda db=None: {"enabled": True, "institute_id": "i", "package_session_id": "p",
                                                         "topics": [{"key": "k", "slide_id": "s", "title": "T"}], "minutes": 3,
                                                         "per_ip_per_day": 1, "daily_cap": 200, "teacher_name": ""})
    out = demo.public_topics()
    assert out["enabled"] and out["topics"][0]["key"] == "k" and out["minutes"] == 3
