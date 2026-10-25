#!/usr/bin/env bash
# Installs a release made by make-release.sh - the first one and every update
# after it - then restarts the servers and checks them (Hosting and
# Deployment Guide, DWS-CFG-03). Run as root on the hosted server:
#   sudo bash install-release.sh dateroem-release-20261010-1200.tar.gz
#
# Kept as they are: the settings in /etc/dateroem, the pictures in
# /srv/dateroem/media (a release only adds pictures the server lacks), the
# downloads, the backups, the database and the Python environments.
set -euo pipefail

TARBALL="${1:-}"
[[ -f "$TARBALL" ]] || { echo "usage: sudo bash install-release.sh <dateroem-release-....tar.gz>"; exit 1; }
[[ $EUID -eq 0 ]] || { echo "Run it with sudo."; exit 1; }
[[ -f /etc/dateroem/backend.env ]] || { echo "Run setup-server.sh first (no /etc/dateroem/backend.env)."; exit 1; }
APP=/srv/dateroem
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
step() { printf '\n== %s\n' "$*"; }

step "Unpacking $(basename "$TARBALL")"
tar xzf "$TARBALL" -C "$STAGE"
NEW="$STAGE/dateroem"
for f in backend/backend.jar frontend/app/main.py frontend/website/dist/index.html \
         mobileServer/app/main.py admin_server_control/server/app/main.py deploy/Caddyfile; do
  [[ -e "$NEW/$f" ]] || { echo "Not a complete release: $f is missing."; exit 1; }
done

step "Stopping the Dateroem servers (the website shows an error for about a minute)"
systemctl stop dateroem-admin-server dateroem-mobile-server dateroem-website-server dateroem-backend 2>/dev/null || true

step "Copying the new version"
install -d -o dateroem -g dateroem "$APP/backend"
# The backend carries the live keys (make-release.sh): readable by the
# service account only.
install -m 640 -o dateroem -g dateroem "$NEW/backend/backend.jar" "$APP/backend/backend.jar"
for d in frontend mobileServer admin_server_control/server; do
  install -d -o dateroem -g dateroem "$APP/$d"
  rsync -a --delete --exclude 'venv/' "$NEW/$d/" "$APP/$d/"
done
rsync -a --delete "$NEW/sql scripts/" "$APP/sql scripts/"
rsync -a --delete "$NEW/deploy/" "$APP/deploy/"
if [[ -d "$NEW/media" ]]; then rsync -a --ignore-existing "$NEW/media/" "$APP/media/"; fi
if [[ -d "$NEW/downloads" ]]; then rsync -a --ignore-existing "$NEW/downloads/" "$APP/downloads/"; fi
# Safaricom's production certificate for Send Money (MPESA_B2C_CERT_PATH).
if [[ -f "$NEW/mpesa/ProductionCertificate.cer" && ! -f /etc/dateroem/mpesa/ProductionCertificate.cer ]]; then
  install -d -o root -g dateroem -m 750 /etc/dateroem/mpesa
  install -m 640 -o root -g dateroem "$NEW/mpesa/ProductionCertificate.cer" /etc/dateroem/mpesa/ProductionCertificate.cer
  echo "  Send Money certificate installed in /etc/dateroem/mpesa"
fi
chown -R dateroem:dateroem "$APP/backend" "$APP/frontend" "$APP/mobileServer" "$APP/admin_server_control" \
  "$APP/sql scripts" "$APP/deploy" "$APP/media" "$APP/downloads"

step "Python packages for the three small servers"
for d in frontend mobileServer admin_server_control/server; do
  [[ -x "$APP/$d/venv/bin/python" ]] || sudo -u dateroem python3 -m venv "$APP/$d/venv"
  sudo -u dateroem "$APP/$d/venv/bin/pip" install -q --no-cache-dir -r "$APP/$d/requirements.txt"
  echo "  $d ready"
done

step "Services, router and the daily backup"
install -m 644 "$APP"/deploy/systemd/*.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable -q dateroem-backend dateroem-website-server dateroem-mobile-server dateroem-admin-server
DOMAIN="$(cat /etc/dateroem/domain)"
EMAIL="$(sed -n 's/^Environment=DATEROEM_EMAIL=//p' /etc/systemd/system/caddy.service.d/dateroem.conf)"
DATEROEM_DOMAIN="$DOMAIN" DATEROEM_EMAIL="$EMAIL" caddy validate --config "$APP/deploy/Caddyfile" --adapter caddyfile >/dev/null 2>&1 \
  || { echo "The new Caddyfile is not valid; the old one is kept."; exit 1; }
install -m 644 "$APP/deploy/Caddyfile" /etc/caddy/Caddyfile
systemctl enable -q caddy
systemctl reload caddy 2>/dev/null || systemctl restart caddy
install -m 755 "$APP/deploy/scripts/backup.sh" /usr/local/sbin/dateroem-backup
echo "30 2 * * * root /usr/local/sbin/dateroem-backup >> /var/log/dateroem-backup.log 2>&1" > /etc/cron.d/dateroem-backup

step "Starting the servers"
systemctl start dateroem-backend
printf '  waiting for the backend'
for _ in $(seq 1 60); do
  curl -s -o /dev/null -m 2 http://127.0.0.1:8080/api/health && break
  printf '.'
  sleep 2
done
echo
systemctl start dateroem-website-server dateroem-mobile-server dateroem-admin-server
# On one processor they need longer than a moment to start (25 October 2026):
# wait for each to answer, up to a minute.
printf '  waiting for the three small servers'
for _ in $(seq 1 30); do
  up=0
  for port in 8000 8001 8002; do
    curl -s -o /dev/null -m 2 "http://127.0.0.1:$port/health" && up=$((up + 1))
  done
  (( up == 3 )) && break
  printf '.'
  sleep 2
done
echo

bash "$APP/deploy/scripts/check-server.sh"
