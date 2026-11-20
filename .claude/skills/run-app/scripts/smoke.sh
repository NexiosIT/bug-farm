#!/usr/bin/env bash
# Drive the running app like the admin screen does: log in through the Vite proxy (:5173)
# and call the admin API. Prints each response; exits non-zero on the first failure.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

BASE="http://localhost:$FRONTEND_PORT/api/admin"
JAR="$(mktemp)"; trap 'rm -f "$JAR"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }

code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/orders/today")
[ "$code" = 401 ] || fail "orders/today before login gave $code, expected 401"
echo "before login: orders/today -> 401 (good)"

# Spring Security wants the XSRF-TOKEN cookie echoed back as a header.
curl -s -o /dev/null -c "$JAR" -b "$JAR" "$BASE/me"
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' "$JAR")
code=$(curl -s -o /dev/null -w '%{http_code}' -c "$JAR" -b "$JAR" \
  -H "X-XSRF-TOKEN: $XSRF" --data 'username=admin&password=admin' "$BASE/login")
[ "$code" = 200 ] || fail "login as admin/admin gave $code"
echo "login admin/admin -> 200"

check() { # path, grep pattern the body must match
  local body; body=$(curl -s -b "$JAR" "$BASE/$1")
  echo "$1 -> $body" | cut -c1-400
  echo "$body" | grep -q "$2" || fail "$1 did not contain $2"
}
check me '"username":"admin"'
check orders/today '"orders"'
check menu '^\['
check settings '"cutOff"'
echo "SMOKE OK"
