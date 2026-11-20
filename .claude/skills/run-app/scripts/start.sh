#!/usr/bin/env bash
# Start the lunch app locally: backend + PostgreSQL in Docker (compose services `app` and `db`)
# on :8080, and the Vite frontend on :5173.
# Login is always admin / admin: a fresh BCrypt hash is made each run and only passed to the
# Docker app's environment; .env and the repo are never changed for it.
# The ngrok tunnel is never started here (it would put admin/admin on the internet).
#
# Usage: start.sh [--no-build]
#   --no-build   reuse the existing lunch-app image instead of rebuilding it
#
# Returns once both servers answer. Frontend log and PID go to target/run-app/.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
cd "$ROOT"

BUILD=1
[ "${1:-}" = "--no-build" ] && BUILD=0

# 1. Docker must be running.
if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker isn't running. Start Docker Desktop and try again." >&2
  exit 1
fi

# 2. .env: create it from .env.example with a random database password when missing.
if [ ! -f "$ENV_FILE" ]; then
  pw="$(head -c 24 /dev/urandom | base64 | tr -d '/+=\n')"
  sed "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$pw|" "$ROOT/.env.example" > "$ENV_FILE"
  echo "Created .env from .env.example with a random POSTGRES_PASSWORD (git-ignored)."
fi
if [ -z "$(env_value POSTGRES_PASSWORD)" ]; then
  echo "ERROR: POSTGRES_PASSWORD is empty in .env. Set it to any long random string." >&2
  exit 1
fi

# 3. Never run admin/admin behind the public tunnel.
if service_running tunnel; then
  echo "ERROR: the ngrok tunnel is running, so the app is public. run-app uses admin/admin," >&2
  echo "which must not be reachable from the internet. Stop the tunnel first:" >&2
  echo "  docker compose stop tunnel" >&2
  exit 1
fi

# 4. Ports. :8080 may be held by our own Docker app (it gets recreated); anything else, we stop.
if port_busy "$BACKEND_PORT" && ! service_running app; then
  echo "ERROR: port $BACKEND_PORT is already in use (pid: $(port_pid "$BACKEND_PORT" || true), $(pid_name "$(port_pid "$BACKEND_PORT")" || true))." >&2
  echo "Probably a java -jar run or another checkout. Ask before stopping it." >&2
  exit 1
fi
if port_busy "$FRONTEND_PORT"; then
  echo "ERROR: port $FRONTEND_PORT is already in use (pid: $(port_pid "$FRONTEND_PORT" || true))." >&2
  echo "If that is an earlier run of this app, run stop.sh first. Otherwise ask before stopping it." >&2
  exit 1
fi

mkdir -p "$RUN_DIR"

# 5. Frontend dependencies, once.
if [ ! -d frontend/node_modules ]; then
  echo "Installing frontend dependencies (npm ci)..."
  npm --prefix frontend ci
fi

# 6. BCrypt hash for the password "admin", made in a throwaway container (no Java needed).
HASH="$(MSYS_NO_PATHCONV=1 docker run --rm httpd:2.4-alpine htpasswd -bnBC 10 "" admin | tr -d ':\r\n')"
case "$HASH" in
  '$2'?'$'*) ;;
  *) echo "ERROR: could not generate the admin password hash." >&2; exit 1 ;;
esac

# 7. Backend + database in Docker. The shell variables below override .env for this run only.
UP_ARGS=(up -d db app)
[ "$BUILD" = 1 ] && UP_ARGS+=(--build) && echo "Building the app image (cached layers make repeat builds faster)..."
LUNCH_ADMIN_USERNAME=admin LUNCH_ADMIN_PASSWORD_HASH="$HASH" compose "${UP_ARGS[@]}"

# 8. Frontend, detached, in its own process group so stop.sh can take down its children too.
if command -v setsid >/dev/null 2>&1; then
  setsid nohup npm --prefix frontend run dev -- --port $FRONTEND_PORT --strictPort \
    > "$RUN_DIR/frontend.log" 2>&1 < /dev/null &
else
  nohup npm --prefix frontend run dev -- --port $FRONTEND_PORT --strictPort \
    > "$RUN_DIR/frontend.log" 2>&1 < /dev/null &
fi
echo $! > "$RUN_DIR/frontend.pid"

# 9. Wait until both answer (any HTTP status counts), or something dies.
wait_backend() {
  local i
  for ((i = 0; i < 180; i++)); do
    curl -s -o /dev/null "http://localhost:$BACKEND_PORT/" && { echo "backend is up on :$BACKEND_PORT (Docker)"; return 0; }
    if ! service_running app; then
      echo "ERROR: the app container stopped. Last log lines:" >&2; compose logs --tail 30 app >&2; return 1
    fi
    sleep 1
  done
  echo "ERROR: backend did not answer on :$BACKEND_PORT within 180s. Last log lines:" >&2
  compose logs --tail 30 app >&2
  return 1
}
wait_frontend() {
  local i
  for ((i = 0; i < 60; i++)); do
    curl -s -o /dev/null "http://localhost:$FRONTEND_PORT/" && { echo "frontend is up on :$FRONTEND_PORT"; return 0; }
    if ! kill -0 "$(cat "$RUN_DIR/frontend.pid")" 2>/dev/null; then
      echo "ERROR: frontend exited. Last log lines:" >&2; tail -30 "$RUN_DIR/frontend.log" >&2; return 1
    fi
    sleep 1
  done
  echo "ERROR: frontend did not answer on :$FRONTEND_PORT within 60s. Last log lines:" >&2
  tail -30 "$RUN_DIR/frontend.log" >&2
  return 1
}
wait_backend
wait_frontend

# 10. Which optional settings the Docker app got from .env (names only, never values).
for v in LUNCH_TEAMS_SECURITY_TOKEN LUNCH_TIME_ZONE; do
  if [ -n "$(env_value "$v")" ]; then echo "env $v: set"; else echo "env $v: not set"; fi
done
echo "Frontend: http://localhost:$FRONTEND_PORT  Backend: http://localhost:$BACKEND_PORT  Login: admin / admin"
echo "Logs: docker compose logs -f app, $RUN_DIR/frontend.log"
