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
