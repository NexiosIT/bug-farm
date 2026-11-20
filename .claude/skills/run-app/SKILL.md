---
name: run-app
description: Start, check and stop the bug-farm lunch app locally - the Spring Boot backend on :8080 and the Vite admin frontend on :5173, logged in as admin/admin. Use this whenever someone wants to run, start, launch, spin up, restart or demo the app (frontend, backend or both), try the admin screen in a browser, see a change working in the real app rather than in tests, or shut the running app down again - even if they only say "run it", "start everything", "can I click through it" or "stop the servers".
---

# Run the lunch app

Three scripts in `scripts/` (next to this file) do the mechanical work, so every run
goes the same way for every developer:

| Script | What it does |
|---|---|
| `start.sh [--no-build]` | Rebuilds the backend jar (tests skipped), makes a fresh BCrypt hash for the password `admin`, starts the backend on :8080 and Vite on :5173 in the background, and returns once both answer. Logs and PIDs go to `target/run-app/` (git-ignored). |
| `smoke.sh` | Logs in as admin/admin **through the Vite proxy** and calls `me`, `orders/today`, `menu` and `settings`. Ends with `SMOKE OK` or a `FAIL:` line. |
| `stop.sh` | Stops what `start.sh` started and confirms both ports are free. |

Run them from the repo root, e.g. `bash .claude/skills/run-app/scripts/start.sh`.

## Starting

1. Run `start.sh` in the foreground with a generous timeout (up to 10 minutes; a cold
   Maven build is slow on `/mnt/c`). It detaches the servers itself, so don't use
   `run_in_background` - you want its exit code and its ready lines.
   - Pass `--no-build` only when the user says the backend hasn't changed or asks for
     a quick restart. By default rebuild: the working tree is usually ahead of the jar,
     and running stale backend code is the kind of mistake that wastes an afternoon.
2. Run `smoke.sh`. Starting is not the same as working: the smoke check proves the
   proxy, the login (CSRF cookie + form login) and the database all line up.
3. Report to the user in a short table: frontend URL, backend URL, login `admin` / `admin`,
   and what the smoke check returned (number of orders today, menu contents, cut-off).
   Also mention from start.sh's `env` lines which optional settings are missing and what
   that means:
   - `ANTHROPIC_API_KEY` not set -> free-text Teams messages get the fallback reply.
   - `LUNCH_TEAMS_SECURITY_TOKEN` not set -> the Teams webhook can't be tested.
   Say plainly that you only checked the API, not how the screens look, unless you
   actually looked at them.
4. Tell them the servers keep running until they ask you to stop them.

The login is always admin/admin on purpose: the hash is made fresh for each run and only
lives in the backend process's environment, so nothing secret touches the repo (see the
Global Constraints in the build plan).

Point people at **:5173** for UI work. The jar on :8080 also serves a bundled frontend,
but start.sh skips that build, so it may be an older copy.

## Stopping

When the user says stop / shut down / kill the servers: run `stop.sh` and report its
last line. If it says a port is still in use, show the PID it printed and ask before
killing anything else.

## When something goes wrong

- **"port … is already in use"** - often an earlier run. If `target/run-app/*.pid`
  exists, run `stop.sh` and start again. Otherwise the port belongs to something else
  (another checkout, IntelliJ, a teammate's demo): tell the user what holds it and ask
  before stopping it.
- **backend exited / didn't answer** - start.sh prints the last 30 log lines; the full
  log is `target/run-app/backend.log`. Common causes: Java isn't 21, or the H2 file in
  `./data/` is locked by another running copy. Deleting `./data/` resets the database,
  but that throws away local orders and menu changes - ask first.
- **frontend exited** - see `target/run-app/frontend.log`; usually `node_modules` is out
  of date (`npm --prefix frontend ci`) or Node is older than the README asks for.
- **smoke.sh FAIL at login** - check backend.log for the security filter's message.
  Look for a username/hash mismatch: someone may have changed `LUNCH_ADMIN_USERNAME`
  handling.

## Variants

- **Frontend only, no backend:** `VITE_MOCK=1 npm --prefix frontend run dev` serves mock
  data. Use it when the user only wants to look at UI and the backend isn't needed.
- **Backend only:** run `start.sh`, then stop just the frontend with
  `kill -- -$(cat target/run-app/frontend.pid)` - or simply tell the user :8080 works on
  its own for API calls.
