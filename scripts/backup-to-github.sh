#!/bin/bash
# Backs the code up to GitHub (github.com/Hawer-tow/Dateroem-Woods, branch main).
#
#   bash scripts/backup-to-github.sh "what changed"
#
# 1. Copies the phone app in from Android Studio's folder (mobile-app/ is a copy:
#    keep editing the app in C:\Users\PC\AndroidStudioProjects\DateroemWoodSales).
# 2. Stages everything .gitignore allows - never the secrets, database backups,
#    logs or build output it lists.
# 3. Stops if any staged file holds a real secret (scripts/check_no_secrets.py).
# 4. Commits with today's date and pushes.
set -euo pipefail
cd "$(dirname "$0")/.."

PHONE=/mnt/c/Users/PC/AndroidStudioProjects/DateroemWoodSales
if [ -d "$PHONE/app" ]; then
  rsync -a --delete \
    --exclude .git --exclude build --exclude .gradle --exclude .idea --exclude .kotlin --exclude .cxx \
    --exclude local.properties --exclude '*.apk' --exclude '*.aab' --exclude '*.jks' --exclude '*.keystore' \
    "$PHONE/" mobile-app/
fi

git add -A
if git diff --cached --quiet; then
  echo "Nothing new to back up."
  exit 0
fi
git diff --cached --name-only -z | python3 scripts/check_no_secrets.py

git commit -q -m "Backup $(date +%Y-%m-%d): ${1:-latest changes}"
git push origin main
echo "Backed up to GitHub."
