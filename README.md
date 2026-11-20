# bug-farm: lunch ordering via Teams

Colleagues order lunch by @mentioning **SandwichBot** in the **Sandwishes** channel of
the **Lunch** team. Ordering closes at the cut-off (default 10:00, Monday to Friday).
The order manager logs into the admin screen, checks today's orders and copies a draft
email for the sandwich shop.

- Design: `docs/superpowers/specs/2026-11-20-food-ordering-design.md`
- Build plan: `docs/superpowers/plans/2026-11-20-lunch-ordering.md`
- Who changes what: `CLAUDE.md`

## What you need

- Java 21 (`JAVA_HOME` pointing at it)
- Git
- Node.js is **not** needed to build the jar: Maven downloads its own copy into
  `app/target`. You only need Node (22.22+ / 24.15+ / 26+) to work on the frontend itself.
- For the Teams test: [ngrok](https://ngrok.com/) (or VS Code dev tunnels) and an
  Anthropic API key.

## Settings (environment variables only)

Never put these in a file in the repo.

| Variable | Needed for | Example / default |
| --- | --- | --- |
| `LUNCH_TEAMS_SECURITY_TOKEN` | Teams webhook (HMAC check) | the token Teams shows once when you create the webhook |
| `LUNCH_ADMIN_USERNAME` | admin login | default `admin` |
| `LUNCH_ADMIN_PASSWORD_HASH` | admin login | BCrypt hash, see below. Empty means nobody can log in. |
| `ANTHROPIC_API_KEY` | reading free-text messages with Claude | empty → free text gets the fallback reply |
| `LUNCH_TIME_ZONE` | cut-off and "today" | default `Europe/Brussels` |

### Making the BCrypt hash for the admin password

After one build (so Maven has downloaded the jars), in Git Bash:

```bash
M=~/.m2/repository/org/springframework
CP="$(cygpath -w $M/security/spring-security-crypto/*/spring-security-crypto-*[0-9].jar);$(cygpath -w $M/spring-jcl/*/spring-jcl-*[0-9].jar)"
printf 'System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("YOUR-PASSWORD"));\n/exit\n' > /tmp/hash.jsh
jshell --class-path "$CP" "$(cygpath -w /tmp/hash.jsh)"
rm /tmp/hash.jsh
```

It prints a hash that starts with `$2a$10$`. In PowerShell, set it with **single
quotes**, because `$` is special in double quotes:

```powershell
$env:LUNCH_ADMIN_PASSWORD_HASH = '$2a$10$...'
```

## Build and test

```bash
./mvnw -q verify                              # backend tests + frontend build, makes app/target/lunch-app-*.jar
./mvnw -q test -Dskip.npm -Dskip.installnodenpm   # backend tests only, faster
npm --prefix frontend ci                      # once, before the frontend tests
npm --prefix frontend test -- --run           # frontend tests
```

The live Claude test only runs with a key: `./mvnw -q -pl admin -am test -Dgroups=live
-Dtest=AnthropicClaudeCallerLiveTest -Dsurefire.failIfNoSpecifiedTests=false`.

## Run locally

```powershell
$env:LUNCH_ADMIN_PASSWORD_HASH = '$2a$10$...'
$env:ANTHROPIC_API_KEY = '...'
$env:LUNCH_TEAMS_SECURITY_TOKEN = '...'
java -jar app/target/lunch-app-0.0.1-SNAPSHOT.jar
```

- Admin screen: http://localhost:8080 (log in with `LUNCH_ADMIN_USERNAME` / your password).
- Data is kept in an H2 file database in `./data/` (git-ignored). Delete that folder to start fresh.
- Frontend development without the backend: `VITE_MOCK=1 npm --prefix frontend run dev`.
  With the backend running, `npm --prefix frontend run dev` proxies `/api` to port 8080.

## Run with Docker (app + PostgreSQL + tunnel)

Only Docker is needed; no Java, Node or database on your machine.

1. `cp .env.example .env` and fill it in. `.env` is git-ignored; never commit it.
   - `POSTGRES_PASSWORD`: any long random string.
   - `LUNCH_ADMIN_PASSWORD_HASH`: make the hash as described above and paste it **inside the
     single quotes**. Without them Docker Compose expands the `$` signs and nobody can log in.
   - `NGROK_AUTHTOKEN` and `NGROK_DOMAIN`: from the ngrok dashboard (Authtoken, and your
     fixed domain under Domains). `NGROK_DOMAIN` is the host name only, e.g.
     `calm-otter-123.ngrok-free.app`.
2. `docker compose up -d --build`
3. Admin screen: http://localhost:8080. Teams webhook callback URL:
   `https://<NGROK_DOMAIN>/api/teams/webhook`.

- `docker compose logs -f app` shows the app's log.
- `docker compose down` stops everything and **keeps** the data (volume `lunch-db`);
  `docker compose down -v` deletes the data too.
- The tunnel makes the whole app public, admin screen included: use a strong admin password.
- If a required setting is missing, `docker compose` stops and names it.

## Connect Teams (laptop + tunnel)

1. Start the app (above).
2. Start a tunnel: `ngrok http 8080`. Note the `https://….ngrok…` address.
3. In Teams, in the **Lunch** team: **⋯ → Manage team → Apps → Create an outgoing webhook**.
   - Name: `SandwichBot`
   - Callback URL: `https://<your-tunnel>/api/teams/webhook`
4. Teams shows a security token **once**. Put it in `LUNCH_TEAMS_SECURITY_TOKEN` and restart the app.
5. In the admin screen, add menu items (e.g. *Ham & cheese*, *Tuna*) and set the shop email.
6. In **Sandwishes**, try:
   - `@SandwichBot menu`
   - `@SandwichBot sandwich: tuna`
   - `@SandwichBot can I get a ham cheese no butter pls`
   - `@SandwichBot my order`
   - `@SandwichBot cancel`

The ngrok address changes every time you restart ngrok (on the free plan). Then edit the
webhook's callback URL in Teams, or create the webhook again and update the token.

## Using the bot

| Message | What happens |
| --- | --- |
| `menu` | lists today's menu |
| `sandwich: ham & cheese, no butter` | places or replaces your order for today |
| `my order` | shows your order for today |
| `cancel` | removes your order for today |
| `help` | short usage text |
| anything else | read by Claude; it confirms an order or asks you to try again |

Free-text messages are sent to Anthropic's API together with the menu (never your name or
Teams id), so don't put other people's details in them.
