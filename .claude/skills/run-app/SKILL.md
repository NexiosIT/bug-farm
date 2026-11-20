---
name: run-app
description: Start, check and stop the bug-farm lunch app locally - the backend and PostgreSQL in Docker on :8080 and the Vite admin frontend on :5173, logged in as admin/admin. Use this whenever someone wants to run, start, launch, spin up, restart or demo the app (frontend, backend or both), try the admin screen in a browser, see a change working in the real app rather than in tests, or shut the running app down again - even if they only say "run it", "start everything", "can I click through it" or "stop the servers".
---

# Run the lunch app

The backend and its PostgreSQL database run in Docker (the `app` and `db` services from
`compose.yaml`); the admin screen runs in Vite on the host for fast UI work. Three scripts
in `scripts/` (next to this file) do the mechanical work, so every run goes the same way
for every developer:

| Script | What it does |
|---|---|
| `start.sh [--no-build]` | Checks Docker is running, creates `.env` from `.env.example` if missing (random database password), makes a fresh BCrypt hash for the password `admin`, builds and starts the Docker services `db` and `app` on :8080, starts Vite on :5173, and returns once both answer. Frontend log and PID go to `target/run-app/` (git-ignored). |
| `smoke.sh` | Logs in as admin/admin **through the Vite proxy** and calls `me`, `orders/today`, `menu` and `settings`. Ends with `SMOKE OK` or a `FAIL:` line. |
| `stop.sh` | Stops the frontend and the Docker services `app` and `db` (the data stays), and confirms both ports are free. |

Run them from the repo root, e.g. `bash .claude/skills/run-app/scripts/start.sh`.
Needs Docker (Docker Desktop on Windows) and Node for Vite; no Java.

## Starting

1. Run `start.sh` in the foreground with a generous timeout (up to 10 minutes; the first
   image build downloads Maven dependencies, Node and npm packages). It detaches the
   servers itself, so don't use `run_in_background` - you want its exit code and its
   ready lines.
   - Pass `--no-build` only when the user says the backend hasn't changed or asks for
     a quick restart. By default rebuild: the working tree is usually ahead of the image,
     and running stale backend code is the kind of mistake that wastes an afternoon.
2. Run `smoke.sh`. Starting is not the same as working: the smoke check proves the
   proxy, the login (CSRF cookie + form login) and the database all line up.
3. Report to the user in a short table: frontend URL, backend URL, login `admin` / `admin`,
   and what the smoke check returned (number of orders today, menu contents, cut-off).
   Also mention from start.sh's `env` lines which optional settings are missing in `.env`
   and what that means:
   - `LUNCH_TEAMS_SECURITY_TOKEN` not set -> the Teams webhook can't be tested.
   Say plainly that you only checked the API, not how the screens look, unless you
   actually looked at them.
4. Tell them the servers keep running until they ask you to stop them.

The login is always admin/admin on purpose: the hash is made fresh for each run and only
passed to the Docker app's environment for that run, so `.env` and the repo never change
(see the Global Constraints in the build plan). A later plain `docker compose up -d`
goes back to the login in `.env`.

**The tunnel is never started by this skill.** With the ngrok tunnel up, the app is on the
internet, and admin/admin must not be. If the tunnel is running, `start.sh` refuses and says
how to stop it (`docker compose stop tunnel`). Running the app for Teams is the README's
"Run with Docker" flow with the password from `.env`, not this skill.

Point people at **:5173** for UI work. The app on :8080 also serves the bundled frontend
from the image, which is as new as the last build.

## Stopping

When the user says stop / shut down / kill the servers: run `stop.sh` and report its
last line. It stops the Docker services (data kept) and only ever kills the frontend's own
`node` process. If it says a port is still in use, show the PID and process name it
printed and ask before stopping anything else.

## When something goes wrong

- **"Docker isn't running"** - start Docker Desktop, wait until it says it's running, try again.
- **"port 8080 is already in use"** - something other than our Docker app holds it, e.g. a
  `java -jar` run, IntelliJ, or another checkout. Tell the user the PID and name start.sh
  printed and ask before stopping it.
- **"port 5173 is already in use"** - often an earlier run. If `target/run-app/frontend.pid`
  exists, run `stop.sh` and start again; otherwise ask.
- **"the ngrok tunnel is running"** - ask the user before stopping the tunnel: someone may be
  testing Teams.
- **"POSTGRES_PASSWORD is empty in .env"** - `.env` exists but isn't filled in. Ask the user;
  it holds their secrets, so don't rewrite it.
- **backend container stopped / didn't answer** - start.sh prints the last 30 log lines; the
  full log is `docker compose logs app`. A Flyway or database error usually means the
  `lunch-db` volume holds data from an older schema; `docker compose down -v` resets it, but
  that deletes all local orders and menu changes - ask first.
- **image build fails** - rerun `docker compose build app` to see the error. A network
  problem (Maven, Node or npm download) is the usual cause.
- **frontend exited** - see `target/run-app/frontend.log`; usually `node_modules` is out
  of date (`npm --prefix frontend ci`) or Node is older than the README asks for.
- **smoke.sh FAIL at login** - check `docker compose logs app` for the security filter's
  message. Look for a username/hash mismatch: someone may have changed `LUNCH_ADMIN_USERNAME`
  handling or the hash variable in `compose.yaml`.

## Variants

- **Frontend only, no backend:** `VITE_MOCK=1 npm --prefix frontend run dev` serves mock
  data. Use it when the user only wants to look at UI and the backend isn't needed.
- **Backend only:** run `start.sh`, then stop just the frontend with
  `kill -- -$(cat target/run-app/frontend.pid)` - or simply tell the user :8080 works on
  its own for API calls.
- **Look at the data:** `docker compose exec db psql -U lunch -d lunch`.
