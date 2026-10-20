#!/usr/bin/env bash
# Exports this computer's database for the hosted server, once, at the move:
#   deploy/out/dateroem-db-<date>-<time>.sql.gz
# Run on this computer (in Ubuntu/WSL):
#   bash /root/fathersProject/deploy/scripts/export-database.sh
#
# The file holds customers' names, phone numbers and orders: copy it only to
# the server, and delete it from both places once import-database.sh has run.
# Hosting and Deployment Guide (DWS-CFG-03).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/deploy/out"
FILE="$OUT/dateroem-db-$(date +%Y%m%d-%H%M).sql.gz"
mkdir -p "$OUT"

# The backend's database password, from the launch script (never printed).
eval "$(grep -E '^export DATEROEM_DB_PASSWORD=' /root/run_backend.sh)"
export MYSQL_PWD="$DATEROEM_DB_PASSWORD"
unset DATEROEM_DB_PASSWORD

mysqldump -h 127.0.0.1 -P 3309 -u dateroem_backend \
  --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 dateroem | gzip > "$FILE"
chmod 600 "$FILE"

TABLES="$(gunzip -c "$FILE" | grep -c '^CREATE TABLE')"
ROUTINES="$(gunzip -c "$FILE" | grep -c -E '^CREATE DEFINER=.* (FUNCTION|PROCEDURE)')"
echo "Database exported: $FILE ($(du -h "$FILE" | cut -f1), $TABLES tables, $ROUTINES functions and procedures)"
echo "Next: copy it to the server and run there:"
echo "  sudo bash dateroem/deploy/scripts/import-database.sh $(basename "$FILE")"
