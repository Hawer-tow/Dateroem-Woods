#!/bin/bash
# Serves the website adverts for filming (25 October 2026): builds the website
# as it is live (the forest look), puts ad.html and the brand fonts beside it
# and serves both on port 4192 with the LIVE shop's data (live-data.vite.
# config.mjs) - the window in the adverts shows what customers see. Then,
# from Windows:
#   node --experimental-websocket record-short.mjs 9333 http://127.0.0.1:4192/ad/ad.html <frames folder>
#   node --experimental-websocket record-long.mjs  9333 http://127.0.0.1:4192/ad/ad.html <frames folder>
# Nothing here touches dist/, and the recorders never write to the live site.
set -euo pipefail
ROOT=/root/fathersProject
OUT=/tmp/dateroem-ad-site
cd "$ROOT/frontend/website"
npx vite build --outDir "$OUT" --emptyOutDir > /tmp/dateroem-ad-build.log 2>&1
mkdir -p "$OUT/ad"
cp "$ROOT/marketing/website-ad/ad.html" "$OUT/ad/"
cp "$ROOT"/brand/fonts/Montserrat-*.ttf "$OUT/ad/"
exec npx vite preview --config "$ROOT/marketing/website-ad/live-data.vite.config.mjs" --outDir "$OUT" >> /tmp/dateroem-ad-build.log 2>&1
