"""Profile photos (migration 039): saved in the shared media folder as
profiles/u<user id>-<random>.jpg - the only names the backend accepts for an
account's photo - and removed again when replaced. The same file is in the
website, phone and desktop servers."""
import re
import secrets
from pathlib import Path

# One folder deeper than the website and phone servers (admin_server_control/server).
MEDIA_DIR = Path(__file__).resolve().parents[4] / "media"
PROFILES_DIR = MEDIA_DIR / "profiles"
KEY = re.compile(r"^profiles/u(\d+)-[a-z0-9]{8,40}\.jpg$")
MAX_BYTES = 1024 * 1024  # a 512 px square JPEG is far smaller


def is_jpeg(data: bytes) -> bool:
    return data.startswith(b"\xff\xd8\xff") and data.rstrip(b"\x00").endswith(b"\xff\xd9")


def save(user_id: int, jpeg: bytes) -> str:
    """Writes the photo and answers its media key."""
    PROFILES_DIR.mkdir(parents=True, exist_ok=True)
    name = f"u{int(user_id)}-{secrets.token_hex(8)}.jpg"
    (PROFILES_DIR / name).write_bytes(jpeg)
    return f"profiles/{name}"


def remove(key) -> None:
    """Deletes a photo the profile no longer uses (only a profiles/ name)."""
    if isinstance(key, str) and KEY.match(key):
        try:
            (MEDIA_DIR / key).unlink()
        except FileNotFoundError:
            pass
