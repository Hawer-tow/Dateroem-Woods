#!/usr/bin/env bash
# Health check of the hosted server (Hosting and Deployment Guide, DWS-CFG-03).
# Run any time:  sudo bash /srv/dateroem/deploy/scripts/check-server.sh
# Shows each server and public address, the settings still empty (names
# only, never values), the certificate's expiry, the last backup and disk space.
set -uo pipefail

DOMAIN="$(cat /etc/dateroem/domain 2>/dev/null || echo localhost)"
FAILED=0
check() {
  local name="$1" url="$2" want="${3:-200}" code
  code="$(curl -s -o /dev/null -w '%{http_code}' -m 15 "$url")"
  if [[ "$code" == "$want" ]]; then
    printf '  OK    %-38s %s\n' "$name" "$code"
  else
    printf '  FAIL  %-38s %s (expected %s)\n' "$name" "$code" "$want"
    FAILED=1
  fi
}

echo "== Servers on this machine"
check "Backend" http://127.0.0.1:8080/api/health
check "Website server" http://127.0.0.1:8000/health
check "Phone app server" http://127.0.0.1:8001/health
check "Desktop app server" http://127.0.0.1:8002/health

echo "== Public addresses (https://$DOMAIN)"
check "Website" "https://$DOMAIN/"
check "Phone app server (/mobile-api)" "https://$DOMAIN/mobile-api/health"
check "Desktop app server (/admin-api)" "https://$DOMAIN/admin-api/health"
# Safaricom POSTs here; a GET answering 405 proves the path reaches the backend.
check "Payment callback (/api/dateroem-payments)" "https://$DOMAIN/api/dateroem-payments/stk" 405

echo "== Settings still empty (fill in, then restart that server)"
EMPTY=0
for f in /etc/dateroem/*.env; do
  names="$(grep -E '^[A-Z0-9_]+=$' "$f" | cut -d= -f1 | tr '\n' ' ')"
  if [[ -n "$names" ]]; then
    echo "  $(basename "$f"): $names"
    EMPTY=1
  fi
done
(( EMPTY )) || echo "  none"
echo "  (an empty line switches that setting off; the three MPESA_B2C lines stay"
echo "   empty until Safaricom provides Send Money)"
CERT="$(sed -n 's/^MPESA_B2C_CERT_PATH=//p' /etc/dateroem/backend.env)"
if [[ -n "$CERT" && ! -f "$CERT" ]]; then echo "  Send Money certificate missing: $CERT"; fi

echo "== HTTPS certificate"
END="$(echo | openssl s_client -connect "$DOMAIN:443" -servername "$DOMAIN" 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)"
if [[ -n "$END" ]]; then
  echo "  valid until $END (Caddy renews it by itself)"
else
  echo "  not available yet - is the domain pointing at this server?"
fi

echo "== Backups"
LAST="$(ls -t /srv/dateroem/backups/db-*.sql.gz 2>/dev/null | head -1)"
if [[ -n "$LAST" ]]; then
  echo "  last: $(basename "$LAST") ($(date -r "$LAST" '+%F %H:%M'))"
else
  echo "  none yet (the first runs tonight at 02:30, or: sudo dateroem-backup)"
fi

echo "== Memory (the server has 2 GB; the swap file catches peaks)"
free -h | awk '/^Mem:/ {print "  memory: " $3 " in use of " $2 ", " $7 " available"} /^Swap:/ {print "  swap:   " $3 " in use of " $2}'

echo "== Disk"
df -h / | awk 'NR == 2 {print "  " $4 " free of " $2 " (" $5 " used)"}'

exit $FAILED
