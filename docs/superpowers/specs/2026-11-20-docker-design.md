# Run everything in Docker — Design (short)

Date: 2026-11-20
Status: approved 2026-11-20
Session: Bert, Thijs and Robin together (joint session)

## Intent

Run the whole lunch app with one command, `docker compose up`, without anyone installing
Java, Node or a database. "Everything" means the app, its database **and the Teams tunnel**;
the tunnel is mandatory, because the bot is useless if Teams can't reach it.

## Goals

- `docker compose up -d --build` starts app + PostgreSQL + ngrok tunnel.
- Orders, menu and settings survive restarts and rebuilds (database on a named volume).
- Teams reaches the bot at a **fixed** address, so the webhook keeps working after a restart.
- Secrets come only from environment variables (a git-ignored `.env` file), never from the
  image or the repo.

## Non-goals

- Hosting on Azure (this prepares for it, but deploying is a separate step).
- CI, image registry, or multi-environment setup.
- Replacing the non-Docker way of running: `java -jar` with H2 keeps working as now.

## Anti-goals

- A secret ends up in the image, in git, or in `docker compose config` output that gets shared.
- Orders are lost when a container is recreated.
- The admin screen is public on the internet with a weak password.

## Approaches considered

| | What | Trade-off |
| --- | --- | --- |
| **A (chosen)** | App + PostgreSQL + tunnel | Same database as the hosting target, so PostgreSQL problems show up now. Needs the Flyway PostgreSQL module. |
| B | App + H2 on a volume + tunnel | One container fewer, but tests a different database than the one we'll host on. |
| (dropped) | App only, or tunnel optional | Ruled out: the tunnel is mandatory. |

The team picked **A**.

## Design

Three services in `compose.yaml` at the repo root:

1. **`db`**: `postgres` (pinned major version), database `lunch`, data on volume `lunch-db`.
   Health check with `pg_isready`; the app waits for it.
2. **`app`**: built from a `Dockerfile` at the repo root.
   - Build stage: Java 21 JDK, `./mvnw -q package -DskipTests` (Maven downloads its own Node and
     builds the frontend, as in Task 14). Tests are not run inside the image build; they run as now.
   - Run stage: Java 21 JRE only, runs as a non-root user, memory capped.
   - Connects to `db` through `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
   - Port 8080 published on `127.0.0.1` only, for local use of the admin screen.
3. **`tunnel`**: the official ngrok image, forwarding the **fixed ngrok domain** to `app:8080`.
   `NGROK_AUTHTOKEN` and `NGROK_DOMAIN` come from `.env`.

Code change: `app/pom.xml` gets `org.flywaydb:flyway-database-postgresql`, without which Flyway
refuses PostgreSQL. The migrations already use SQL that works on both H2 and PostgreSQL.

Configuration: a committed `.env.example` lists every variable with a comment;
the real `.env` is git-ignored (`.gitignore` gets `.env`). `.dockerignore` keeps `target/`,
`node_modules/`, `data/`, `.env` and `.git` out of the build.

README gets a "Run with Docker" section: create `.env`, make the BCrypt hash, claim the
ngrok domain, `docker compose up -d --build`, point the Teams webhook at
`https://<NGROK_DOMAIN>/api/teams/webhook`.

## Calls made by Claude (check these)

- Fixed ngrok domain rather than a changing one (one free static domain per ngrok account;
  check your account's current plan).
- The tunnel exposes the whole app, admin screen included. The README says to use a
  strong admin password; `secret` is for laptop-only use.
- Docker work is based on branch `task-14` (it has the frontend bundling), not on `develop`,
  because `task-14` is not merged yet.
- Verification: build the image, `docker compose up`, check that Flyway applies all
  migrations on PostgreSQL, the admin login works, data survives `docker compose down` / `up`,
  and the webhook answers 401 to an unsigned request through the public ngrok address.

## Left to the builder

Exact image tags (pinned), memory limits, health-check timings, and wording of `.env.example`.
