#!/usr/bin/env bash
# Stop what start.sh started: the Vite frontend, and the Docker services `app` and `db`
# (stopped, not removed: the PostgreSQL data stays). Then confirm both ports are free.
# Only ever kills the frontend's own node process; never whatever else holds a port.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

# 1. Frontend.
pidfile="$RUN_DIR/frontend.pid"
if [ -f "$pidfile" ]; then
  pid=$(cat "$pidfile")
  # Whole process group first (npm -> node -> vite), then the PID itself.
  kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
  rm -f "$pidfile"
  echo "stopped frontend (pid $pid)"
  for _ in $(seq 1 10); do port_busy $FRONTEND_PORT || break; sleep 1; done
  # On Windows (Git Bash) the MSYS PID doesn't always own the native node process.
  if [ "$IS_WINDOWS" = 1 ] && port_busy $FRONTEND_PORT; then
    wpid=$(port_pid $FRONTEND_PORT)
    if [ "$(pid_name "$wpid")" = "node.exe" ]; then
      taskkill //F //T //PID "$wpid" >/dev/null 2>&1 && echo "stopped node on :$FRONTEND_PORT (pid $wpid)"
      sleep 1
    fi
  fi
else
  echo "no frontend PID file in $RUN_DIR: no frontend started by start.sh is recorded"
fi

# 2. Backend + database in Docker (data kept).
if ! docker info >/dev/null 2>&1; then
  echo "Docker isn't running: no Docker services to stop"
elif service_running app || service_running db; then
  compose stop app db >/dev/null 2>&1 && echo "stopped Docker services app and db (data kept)"
else
  echo "Docker services app and db were not running"
fi

busy=0
for p in $BACKEND_PORT $FRONTEND_PORT; do
  if port_busy "$p"; then
    wpid=$(port_pid "$p" || true)
    echo "port $p still in use (pid: $wpid, $(pid_name "$wpid" || true))"; busy=1
  fi
done
[ "$busy" = 0 ] && echo "ports $BACKEND_PORT and $FRONTEND_PORT are free"
exit $busy
