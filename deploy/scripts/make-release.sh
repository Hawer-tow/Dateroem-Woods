#!/usr/bin/env bash
# Packs everything the hosted server runs into one file, after building the
# backend (backend.jar) and the website (dist):
#   deploy/out/dateroem-release-<date>-<time>.tar.gz
#
# Run on this computer (in Ubuntu/WSL), from anywhere:
#   bash /root/fathersProject/deploy/scripts/make-release.sh               an update
#   bash /root/fathersProject/deploy/scripts/make-release.sh --with-media  the first install
# --with-media also packs the product and campaign pictures (media/) and the
# phone app downloads (downloads/); the server never lets a release replace
# pictures it already has.
#
# Never packed: settings files (.env), secrets, Python venvs, node_modules,
# logs or the database (export-database.sh does that, separately).
# Hosting and Deployment Guide (DWS-CFG-03).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WITH_MEDIA=false
[[ "${1:-}" == "--with-media" ]] && WITH_MEDIA=true
OUT="$ROOT/deploy/out"
NAME="dateroem-release-$(date +%Y%m%d-%H%M)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
step() { printf '\n== %s\n' "$*"; }

step "Building the backend (backend.jar)"
(cd "$ROOT/backend" && mvn -q -DskipTests package)
JAR="$(ls -t "$ROOT"/backend/target/backend-*.jar | head -1)"

step "Building the website"
(cd "$ROOT/frontend/website" && npm run -s build >/dev/null)

step "Collecting the files"
R="$STAGE/dateroem"
mkdir -p "$R/backend" "$R/frontend/website"
cp "$JAR" "$R/backend/backend.jar"
# Python servers: their code and package list only.
for d in frontend mobileServer admin_server_control/server; do
  mkdir -p "$R/$d"
  cp -r "$ROOT/$d/app" "$R/$d/app"
  cp "$ROOT/$d/requirements.txt" "$R/$d/"
done
cp -r "$ROOT/frontend/website/dist" "$R/frontend/website/dist"
cp -r "$ROOT/sql scripts" "$R/sql scripts"
mkdir -p "$R/deploy"
cp -r "$ROOT/deploy/Caddyfile" "$ROOT/deploy/systemd" "$ROOT/deploy/env" "$ROOT/deploy/scripts" "$ROOT/deploy/README.md" "$R/deploy/"
if $WITH_MEDIA; then
  cp -r "$ROOT/media" "$R/media"
  cp -r "$ROOT/downloads" "$R/downloads"
fi
find "$R" -name __pycache__ -type d -prune -exec rm -rf {} +
find "$R" \( -name '*.pyc' -o -name '*.log' \) -delete

step "Checking that no secret is inside"
python3 - "$R" "$ROOT" <<'PY'
# Loads this computer's secret values into memory (never printed) and
# refuses the release if any of them, or a secrets file, is in it.
import re, sys
from pathlib import Path
release, root = Path(sys.argv[1]), Path(sys.argv[2])
values = set()
def read(path, pattern):
    try:
        for line in open(path, encoding='utf-8'):
            m = re.match(pattern, line.strip())
            # MAVEN_OPTS holds Java options, not a secret.
            if m and m.group(1) != 'MAVEN_OPTS':
                value = m.group(2).strip().strip('"\'')
                if len(value) >= 8 and not value.startswith(('http://', 'https://', 'jdbc:')):
                    values.add(value)
    except FileNotFoundError:
        pass
read('/root/run_backend.sh', r'export\s+([A-Z0-9_]+)=(.+)')
for env in ('frontend/.env', 'mobileServer/.env', 'admin_server_control/server/.env'):
    read(root / env, r'([A-Z0-9_]*(?:PASSWORD|SECRET|KEY|TOKEN)[A-Z0-9_]*)=(.+)')
values |= {v.replace(' ', '') for v in values}
bad = []
for path in release.rglob('*'):
    if path.is_dir():
        continue
    if path.name == '.env' or path.suffix == '.tex' or path.name.startswith('other details'):
        bad.append(str(path.relative_to(release)))
        continue
    data = path.read_bytes()
    if any(v.encode() in data for v in values):
        bad.append(str(path.relative_to(release)))
if bad:
    print('STOPPED - secret values or files found in:', *bad, sep='\n  ')
    sys.exit(1)
print(f'clean ({len(values)} secret values compared, none found)')
PY

step "Packing"
mkdir -p "$OUT"
tar czf "$OUT/$NAME.tar.gz" -C "$STAGE" dateroem
SIZE="$(du -h "$OUT/$NAME.tar.gz" | cut -f1)"
echo "Release: $OUT/$NAME.tar.gz ($SIZE)$($WITH_MEDIA && echo ', with pictures and downloads')"
echo "Next: copy it to the server and run there:"
echo "  sudo bash dateroem/deploy/scripts/install-release.sh $NAME.tar.gz"
