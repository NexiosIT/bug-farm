# Shared helpers for start.sh / stop.sh / smoke.sh. Sourced, not run.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
RUN_DIR="$ROOT/target/run-app"          # git-ignored (target/)
BACKEND_PORT=8080
FRONTEND_PORT=5173

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) IS_WINDOWS=1 ;;
  *) IS_WINDOWS=0 ;;
esac

# Prints the PID listening on a TCP port, or nothing.
port_pid() {
  local port=$1
  if [ "$IS_WINDOWS" = 1 ]; then
    netstat -ano | awk -v p=":$port" '$2 ~ p"$" && $4 == "LISTENING" {print $5; exit}'
  elif command -v lsof >/dev/null 2>&1; then
    lsof -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -1
  else
    ss -ltnpH "sport = :$port" 2>/dev/null | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2
  fi
}

port_busy() {
  if [ "$IS_WINDOWS" = 1 ]; then
    [ -n "$(port_pid "$1")" ]
  else
    # ss works without root even when it can't see the owning PID.
    if command -v ss >/dev/null 2>&1; then
      [ -n "$(ss -ltnH "sport = :$1" 2>/dev/null)" ]
    else
      [ -n "$(port_pid "$1")" ]
    fi
  fi
}

# Process name for a PID (Windows: e.g. node.exe), or nothing.
pid_name() {
  if [ "$IS_WINDOWS" = 1 ]; then
    tasklist //FI "PID eq $1" //FO CSV //NH 2>/dev/null | awk -F'"' 'NF > 1 {print $2; exit}'
  else
    ps -o comm= -p "$1" 2>/dev/null
  fi
}

# ---- Docker (backend + PostgreSQL run in Docker Compose, see compose.yaml) ----

ENV_FILE="$ROOT/.env"

# Value of NAME in .env (quotes stripped), or nothing.
env_value() {
  [ -f "$ENV_FILE" ] || return 0
  grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | tr -d '\r' | sed -E "s/^'(.*)'\$/\1/; s/^\"(.*)\"\$/\1/"
}

# docker compose, from the repo root. Compose reads the whole file, including the tunnel's
# required settings; this skill never starts the tunnel, so placeholders are fine when .env
# doesn't have them yet. Real values in .env always win over these placeholders.
compose() {
  local token domain
  token="$(env_value NGROK_AUTHTOKEN)"; domain="$(env_value NGROK_DOMAIN)"
  ( cd "$ROOT" && \
    NGROK_AUTHTOKEN="${token:-not-used-by-run-app}" \
    NGROK_DOMAIN="${domain:-not-used-by-run-app.invalid}" \
    docker compose "$@" )
}

# True when the given compose service is running.
service_running() {
  compose ps --status running --services 2>/dev/null | tr -d '\r' | grep -qx "$1"
}
