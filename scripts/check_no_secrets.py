"""Stops a backup if a file about to be committed holds a real secret.

The real values are read from where they live on this computer (the backend's
start script and application.properties, every .env file) and compared with
the staged files; well-known key shapes are checked too. Only file names and
the NAME of what was found are printed - never a value. Exit code 1 = found.

Usage (from the repository root, in WSL):
    git diff --cached --name-only -z | python3 scripts/check_no_secrets.py
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SECRET_NAME = re.compile(r"(PASSWORD|SECRET|PASSKEY|CREDENTIAL|API[_-]?KEY|AUTHTOKEN|TOKEN|CONSUMER[_-]?KEY|PRIVATE[_-]?KEY)", re.I)
SKIP_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".mp4", ".mov", ".webm", ".pdf", ".zip", ".gz",
            ".jar", ".ttf", ".otf", ".woff", ".woff2", ".docx", ".xlsx"}
SHAPES = {
    "Anthropic API key": re.compile(r"sk-ant-[A-Za-z0-9_-]{20,}"),
    "Groq API key": re.compile(r"gsk_[A-Za-z0-9]{20,}"),
    "Google API key": re.compile(r"AIza[0-9A-Za-z_-]{30,}"),
    "GitHub token": re.compile(r"gh[pousr]_[A-Za-z0-9]{30,}"),
    "private key block": re.compile(r"BEGIN (RSA |EC |OPENSSH |)PRIVATE KEY"),
    "password hash": re.compile(r"\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}"),
}

secrets = {}


def add(name, value):
    value = (value or "").strip().strip("\"'")
    if len(value) >= 6 and not value.startswith("${") and value.lower() not in ("none", "changeme", "change_me", "true", "false"):
        secrets.setdefault(value, name)


for script in ("/root/run_backend.sh", "/root/start_backend.sh"):
    try:
        for name, value in re.findall(r"export ([A-Z_]+)=([^ \"&\n]+)", Path(script).read_text(encoding="utf-8")):
            if SECRET_NAME.search(name):
                add(name, value)
    except OSError:
        pass

for props in (ROOT / "backend/src/main/resources").glob("application*.properties"):
    for line in props.read_text(encoding="utf-8").splitlines():
        if not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            if re.search(r"(password|secret|passkey|consumer-key|api-key|jwt-secret|initiator-password)", key, re.I):
                m = re.fullmatch(r"\$\{[A-Z0-9_]+:(.*)\}", value.strip())
                add(key.strip(), m.group(1) if m else value)

for env in ROOT.rglob(".env*"):
    if env.is_dir() or env.name.endswith(".example") or "node_modules" in env.parts or "venv" in env.parts:
        continue
    try:
        for line in env.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                name, value = line.split("=", 1)
                if SECRET_NAME.search(name):
                    add(f"{name.strip()} ({env.parent.name}/{env.name})", value)
    except OSError:
        pass

names = [n for n in sys.stdin.read().split("\0") if n]
found = 0
for name in names:
    path = ROOT / name
    if path.suffix.lower() in SKIP_EXT or not path.is_file() or path.stat().st_size > 8 * 1024 * 1024:
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    hits = [label for value, label in secrets.items() if value in text]
    hits += [label for label, shape in SHAPES.items() if shape.search(text)]
    if hits:
        found += 1
        print(f"SECRET in {name}: {', '.join(sorted(set(hits)))}")
print(f"secret check: {len(names)} files, compared with {len(secrets)} real values - {'STOP' if found else 'clean'}")
sys.exit(1 if found else 0)
