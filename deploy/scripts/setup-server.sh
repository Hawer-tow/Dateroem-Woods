#!/usr/bin/env bash
# One-time preparation of a NEW Ubuntu 24.04 server for Dateroem
# (Hosting and Deployment Guide, DWS-CFG-03). Run as root from the unpacked
# release, with the company domain and an email for certificate notices:
#   sudo bash dateroem/deploy/scripts/setup-server.sh dateroemwoods.co.ke dateroemwoodsales@gmail.com
#
# Installs Java 17, MySQL 8, Python 3, Caddy and the firewall; creates the
# "dateroem" service account, the folders, the database and its account;
# writes the settings files in /etc/dateroem with the domain, a new database
# password and a new sign-in secret filled in. Safe to run again: existing
# settings files are kept as they are.
set -euo pipefail

DOMAIN="${1:-}"
EMAIL="${2:-}"
if [[ -z "$DOMAIN" || -z "$EMAIL" ]]; then
  echo "usage: sudo bash setup-server.sh <domain> <email for certificate notices>"
  exit 1
fi
[[ $EUID -eq 0 ]] || { echo "Run it with sudo."; exit 1; }
# Accept "https://www.example.co.ke/" as well as "example.co.ke".
DOMAIN="${DOMAIN#https://}"; DOMAIN="${DOMAIN#http://}"; DOMAIN="${DOMAIN%%/*}"; DOMAIN="${DOMAIN#www.}"
DEPLOY="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
step() { printf '\n== %s\n' "$*"; }

step "Packages: Java 17, MySQL 8, Python 3, firewall"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q openjdk-17-jre-headless mysql-server python3 python3-venv python3-pip \
  rsync curl gnupg openssl ufw unattended-upgrades apt-transport-https

step "Memory: a 2 GB swap file and a lighter MySQL (the 2 GB HostAfrica C3 plan)"
# Measured on the company computer: about 1.1 GB in use idle, up to about
# 2 GB busy. The swap file catches peaks; MySQL's performance schema (about
# 400 MB) isn't needed. The backend is held to 512 MB (dateroem-backend.service).
if ! swapon --show --noheadings | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo "  swap: 2 GB at /swapfile"
else
  echo "  swap already on"
fi
cat > /etc/mysql/mysql.conf.d/zz-dateroem.cnf <<'EOF'
# Dateroem on a 2 GB server (setup-server.sh).
[mysqld]
performance_schema = OFF
EOF
systemctl restart mysql

step "Caddy (from its official package repository)"
if ! command -v caddy >/dev/null; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -q
  apt-get install -y -q caddy
fi

step "Automatic security updates"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

step "Firewall: only remote login (SSH), HTTP and HTTPS from outside"
# awk reads all of sshd's answer (no early "exit"): stopping early broke the
# pipe, and with pipefail that ended the whole script (first real server,
# 25 October 2026).
SSH_PORT="$(sshd -T 2>/dev/null | awk '/^port /{p = $2} END {print p}')" || SSH_PORT=22
ufw allow "${SSH_PORT:-22}/tcp" >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null
ufw status | sed 's/^/  /'

step "Service account and folders"
id dateroem >/dev/null 2>&1 || useradd --system --home-dir /srv/dateroem --shell /usr/sbin/nologin dateroem
install -d -o dateroem -g dateroem -m 755 /srv/dateroem /srv/dateroem/media /srv/dateroem/downloads
install -d -o root -g root -m 700 /srv/dateroem/backups
install -d -o root -g dateroem -m 750 /etc/dateroem /etc/dateroem/mpesa
echo "$DOMAIN" > /etc/dateroem/domain

step "Database and its account"
systemctl enable --now mysql >/dev/null 2>&1
if [[ -f /etc/dateroem/backend.env ]]; then
  DB_PASSWORD="$(grep -E '^DATEROEM_DB_PASSWORD=' /etc/dateroem/backend.env | cut -d= -f2-)"
else
  DB_PASSWORD="$(openssl rand -hex 24)"
fi
JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n/+=')"
mysql --protocol=socket -u root <<SQL
CREATE DATABASE IF NOT EXISTS dateroem CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'dateroem_backend'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'dateroem_backend'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER 'dateroem_backend'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER 'dateroem_backend'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON dateroem.* TO 'dateroem_backend'@'localhost';
GRANT ALL PRIVILEGES ON dateroem.* TO 'dateroem_backend'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
echo "  database dateroem and account dateroem_backend ready (MySQL listens only on this machine)"

step "Settings files in /etc/dateroem (existing ones are kept)"
for name in backend website-server mobile-server admin-server; do
  target="/etc/dateroem/$name.env"
  if [[ -f "$target" ]]; then
    echo "  kept $target"
    continue
  fi
  sed -e "s|__DOMAIN__|$DOMAIN|g" -e "s|__DB_PASSWORD__|$DB_PASSWORD|g" -e "s|__JWT_SECRET__|$JWT_SECRET|g" \
    "$DEPLOY/env/$name.env.example" > "$target"
  chown root:dateroem "$target"
  chmod 640 "$target"
  echo "  made $target"
done

step "Caddy: the domain for the router"
install -d /etc/systemd/system/caddy.service.d
cat > /etc/systemd/system/caddy.service.d/dateroem.conf <<EOF
[Service]
Environment=DATEROEM_DOMAIN=$DOMAIN
Environment=DATEROEM_EMAIL=$EMAIL
EOF
systemctl daemon-reload

ADDRESS="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1)}')"
# Set apart from the message below: an apostrophe inside ${...:-...} in it
# stopped the script at its very end (25 October 2026).
[[ -n "$ADDRESS" ]] || ADDRESS="<the public IP of this server>"
step "Done. Next:"
cat <<EOF
  1. At the domain registrar, point $DOMAIN and www.$DOMAIN to this server
     (two "A" records, value $ADDRESS).
  2. Copy the database here and run:
       sudo bash $DEPLOY/scripts/import-database.sh dateroem-db-....sql.gz
  3. Fill in what the release doesn't carry - the Gmail App Password for the
     email form and the Anthropic key for picture scanning (the M-Pesa, SMS,
     Groq and worker keys come with the release):
       sudo nano /etc/dateroem/backend.env
       sudo nano /etc/dateroem/website-server.env
  4. Install the release and start everything:
       sudo bash $DEPLOY/scripts/install-release.sh dateroem-release-....tar.gz
EOF
