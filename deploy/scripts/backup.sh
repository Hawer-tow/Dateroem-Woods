#!/usr/bin/env bash
# Daily backup on the hosted server (installed as /usr/local/sbin/dateroem-backup
# by install-release.sh; runs at 02:30 from /etc/cron.d/dateroem-backup).
# Run it by hand before anything risky:  sudo dateroem-backup
#   /srv/dateroem/backups/db-<date>-<time>.sql.gz      kept 30 days
#   /srv/dateroem/backups/media-<date>-<time>.tar.gz   kept 7 days
# These stay on the same server: copy them off it every week (Hosting and
# Deployment Guide, DWS-CFG-03, "Backups").
set -euo pipefail

DIR=/srv/dateroem/backups
STAMP="$(date +%Y%m%d-%H%M)"
umask 077
mkdir -p "$DIR"

mysqldump --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 dateroem | gzip > "$DIR/db-$STAMP.sql.gz.part"
mv "$DIR/db-$STAMP.sql.gz.part" "$DIR/db-$STAMP.sql.gz"
tar czf "$DIR/media-$STAMP.tar.gz" -C /srv/dateroem media

find "$DIR" -name 'db-*.sql.gz' -mtime +30 -delete
find "$DIR" -name 'media-*.tar.gz' -mtime +7 -delete
find "$DIR" -name '*.part' -mtime +1 -delete
echo "$(date '+%F %T') backup done: db-$STAMP.sql.gz ($(du -h "$DIR/db-$STAMP.sql.gz" | cut -f1)), media-$STAMP.tar.gz"
