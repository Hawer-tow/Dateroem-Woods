#!/usr/bin/env bash
# Loads the database exported by export-database.sh into the hosted server,
# once, at the move (Hosting and Deployment Guide, DWS-CFG-03). Run as root:
#   sudo bash dateroem/deploy/scripts/import-database.sh dateroem-db-20261010-1200.sql.gz
#
# Refuses if the server's database already has tables - that is the live
# data. To replace it on purpose, back it up first (sudo dateroem-backup)
# and add --replace.
set -euo pipefail

DUMP="${1:-}"
[[ -f "$DUMP" ]] || { echo "usage: sudo bash import-database.sh <dateroem-db-....sql.gz> [--replace]"; exit 1; }
[[ $EUID -eq 0 ]] || { echo "Run it with sudo."; exit 1; }

TABLES="$(mysql -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dateroem'")"
if (( TABLES > 0 )); then
  if [[ "${2:-}" != "--replace" ]]; then
    echo "The dateroem database on this server already has $TABLES tables - importing would overwrite live data."
    echo "To replace it on purpose: sudo dateroem-backup, then run this again with --replace."
    exit 1
  fi
  echo "Replacing the existing database (--replace)."
  systemctl stop dateroem-admin-server dateroem-mobile-server dateroem-website-server dateroem-backend 2>/dev/null || true
  mysql -e "DROP DATABASE dateroem; CREATE DATABASE dateroem CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
fi

# The functions and procedures belong to dateroem_backend@'%' on the old
# computer; here that account exists only on this machine (@localhost).
gunzip -c "$DUMP" \
  | sed -e 's/DEFINER=`dateroem_backend`@`%`/DEFINER=`dateroem_backend`@`localhost`/g' \
  | mysql --default-character-set=utf8mb4 dateroem

mysql -N -e "SELECT CONCAT('Imported: ', (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dateroem'), ' tables, ',
  (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='dateroem'), ' functions and procedures, ',
  (SELECT COUNT(*) FROM dateroem.USERS), ' user accounts')"
echo "Delete the export file from this server and from the old computer now that it is in:"
echo "  rm $DUMP"
