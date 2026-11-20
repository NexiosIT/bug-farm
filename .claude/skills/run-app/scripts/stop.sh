#!/usr/bin/env bash
# Stop the backend and frontend that start.sh launched, then confirm both ports are free.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

stopped_any=0
for name in backend frontend; do
  pidfile="$RUN_DIR/$name.pid"
  [ -f "$pidfile" ] || continue
  pid=$(cat "$pidfile")
  # Whole process group first (npm -> node -> vite), then the PID itself.
  kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
  rm -f "$pidfile"
  stopped_any=1
  echo "stopped $name (pid $pid)"
done

if [ "$stopped_any" = 1 ]; then
  for _ in $(seq 1 15); do
    port_busy $BACKEND_PORT || port_busy $FRONTEND_PORT || break
    sleep 1
  done
  # On Windows (Git Bash) the MSYS PID doesn't always own the native java/node process.
  if [ "$IS_WINDOWS" = 1 ]; then
    for p in $BACKEND_PORT $FRONTEND_PORT; do
      wpid=$(port_pid "$p"); [ -n "$wpid" ] && taskkill //F //T //PID "$wpid" >/dev/null 2>&1
    done
    sleep 1
  fi
else
  echo "no PID files in $RUN_DIR: nothing started by start.sh is recorded"
fi

busy=0
for p in $BACKEND_PORT $FRONTEND_PORT; do
  if port_busy "$p"; then echo "port $p still in use (pid: $(port_pid "$p" || true))"; busy=1; fi
done
[ "$busy" = 0 ] && echo "ports $BACKEND_PORT and $FRONTEND_PORT are free"
exit $busy
