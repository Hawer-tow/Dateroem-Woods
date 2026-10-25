#!/bin/bash
# Serves the website advert for filming (25 October 2026): builds the website
# as it is live (the forest look), puts ad.html and the brand fonts beside it
# and serves both on port 4192, with the same /svc and /media forwarding as
# the website preview - so the window in the advert shows this computer's
# copy of the shop. Then, from Windows:
#   node --experimental-websocket record.mjs 9333 http://127.0.0.1:4192/ad/ad.html <frames folder>
# Nothing here touches dist/ or the live site.
set -euo pipefail
ROOT=/root/fathersProject
OUT=/tmp/dateroem-ad-site
cd "$ROOT/frontend/website"
npx vite build --outDir "$OUT" --emptyOutDir > /tmp/dateroem-ad-build.log 2>&1
mkdir -p "$OUT/ad"
cp "$ROOT/marketing/website-ad/ad.html" "$OUT/ad/"
cp "$ROOT"/brand/fonts/Montserrat-*.ttf "$OUT/ad/"
exec npx vite preview --outDir "$OUT" --port 4192 --strictPort >> /tmp/dateroem-ad-build.log 2>&1
