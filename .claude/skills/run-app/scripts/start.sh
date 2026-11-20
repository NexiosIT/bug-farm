#!/usr/bin/env bash
# Start the lunch app locally: Spring Boot backend on :8080, Vite frontend on :5173.
# Login is always admin / admin (a fresh BCrypt hash is made each run, never written to the repo).
#
# Usage: start.sh [--no-build]
#   --no-build   reuse app/target/lunch-app-*.jar instead of rebuilding it
#
# Returns once both servers answer. Logs and PIDs go to target/run-app/.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
cd "$ROOT"

BUILD=1
[ "${1:-}" = "--no-build" ] && BUILD=0

# 1. Ports must be free; we never kill something we didn't start.
for p in $BACKEND_PORT $FRONTEND_PORT; do
  if port_busy "$p"; then
    echo "ERROR: port $p is already in use (pid: $(port_pid "$p" || true))." >&2
    echo "If that is an earlier run of this app, run stop.sh first. Otherwise ask before stopping it." >&2
    exit 1
  fi
done

mkdir -p "$RUN_DIR"

# 2. Backend jar. Skip tests and the bundled frontend build: Vite serves the frontend.
if [ "$BUILD" = 1 ] || ! ls app/target/lunch-app-*.jar >/dev/null 2>&1; then
  echo "Building backend jar (tests skipped)..."
  ./mvnw -q package -DskipTests -Dskip.npm -Dskip.installnodenpm
fi
JAR="$(ls app/target/lunch-app-*.jar | head -1)"

# 3. Frontend dependencies, once.
if [ ! -d frontend/node_modules ]; then
  echo "Installing frontend dependencies (npm ci)..."
  npm --prefix frontend ci
fi

# 4. BCrypt hash for the password "admin", using the jars Maven already downloaded.
M="$HOME/.m2/repository/org/springframework"
CRYPTO="$(ls "$M"/security/spring-security-crypto/*/spring-security-crypto-*[0-9].jar 2>/dev/null | tail -1)"
JCL="$(ls "$M"/spring-jcl/*/spring-jcl-*[0-9].jar 2>/dev/null | tail -1)"
if [ -z "$CRYPTO" ] || [ -z "$JCL" ]; then
  echo "ERROR: spring-security-crypto / spring-jcl not in ~/.m2. Run without --no-build once." >&2
  exit 1
fi
JSH="$RUN_DIR/hash.jsh"
printf 'System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("admin"));\n/exit\n' > "$JSH"
if [ "$IS_WINDOWS" = 1 ]; then
  CP="$(cygpath -w "$CRYPTO");$(cygpath -w "$JCL")"; JSH_ARG="$(cygpath -w "$JSH")"
else
  CP="$CRYPTO:$JCL"; JSH_ARG="$JSH"
fi
HASH="$(jshell --class-path "$CP" "$JSH_ARG" 2>/dev/null | grep -E '^\$2[aby]\$' | head -1 | tr -d '\r')"
rm -f "$JSH"
[ -n "$HASH" ] || { echo "ERROR: could not generate the admin password hash." >&2; exit 1; }

# 5. Launch both, detached, in their own process groups so stop.sh can take down children too.
launch() { # name, then command...
  local name=$1; shift
  if command -v setsid >/dev/null 2>&1; then
    setsid nohup "$@" > "$RUN_DIR/$name.log" 2>&1 < /dev/null &
  else
    nohup "$@" > "$RUN_DIR/$name.log" 2>&1 < /dev/null &
  fi
  echo $! > "$RUN_DIR/$name.pid"
}

LUNCH_ADMIN_USERNAME=admin LUNCH_ADMIN_PASSWORD_HASH="$HASH" \
  launch backend java -jar "$JAR"
launch frontend npm --prefix frontend run dev -- --port $FRONTEND_PORT --strictPort

# 6. Wait until both answer (any HTTP status counts), or a process dies.
wait_for() { # name, port, timeout seconds
  local name=$1 port=$2 limit=$3 i
  for ((i = 0; i < limit; i++)); do
    curl -s -o /dev/null "http://localhost:$port/" && { echo "$name is up on :$port"; return 0; }
    if ! kill -0 "$(cat "$RUN_DIR/$name.pid")" 2>/dev/null; then
      echo "ERROR: $name exited. Last log lines:" >&2; tail -30 "$RUN_DIR/$name.log" >&2; return 1
    fi
    sleep 1
  done
  echo "ERROR: $name did not answer on :$port within ${limit}s. Last log lines:" >&2
  tail -30 "$RUN_DIR/$name.log" >&2
  return 1
}
wait_for backend $BACKEND_PORT 180
wait_for frontend $FRONTEND_PORT 60

# 7. Which optional settings are present (names only, never values).
for v in LUNCH_TEAMS_SECURITY_TOKEN LUNCH_TIME_ZONE; do
  if [ -n "${!v:-}" ]; then echo "env $v: set"; else echo "env $v: not set"; fi
done
echo "Frontend: http://localhost:$FRONTEND_PORT  Backend: http://localhost:$BACKEND_PORT  Login: admin / admin"
echo "Logs: $RUN_DIR/backend.log, $RUN_DIR/frontend.log"
