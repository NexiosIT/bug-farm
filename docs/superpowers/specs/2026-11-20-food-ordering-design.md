# Office Food Ordering via Teams — Design

Date: 2026-11-20
Status: approved 2026-11-20 (including AI fallback for reading messages)

## Intent

Colleagues order lunch by @mentioning a bot in the **Sandwishes** channel of
the **Lunch** team in Microsoft Teams. At the daily cut-off (10:00) ordering
closes. The order manager opens a small web admin screen, copies a ready-made
draft email with all of today's orders, and sends it to the sandwich shop
from their own mailbox. Sandwiches are the first food type; the design must
allow more types later.

This is a real tool for the office, built by three colleagues during the
Nexios AI Bootcamp. A hard requirement is that the work divides into **three
parts that do not overlap**, so each person can build and test independently.
The team communicates in Teams.

## Goals

- Anyone in the Sandwishes channel can place, view, replace and cancel their
  order for today by @mentioning the bot, and gets an immediate confirmation.
- Ordering closes at the cut-off time (default 10:00).
- The order manager can log into a web screen to see/remove today's orders,
  manage the menu, change settings (cut-off time, shop email address) and
  copy a draft order email to paste into Outlook.
- Three people can each own one part with a clear, agreed contract between
  parts.

## Non-goals (for now)

- **Sending the email automatically.** The manager copies and sends it. Planned
  for later; the draft-building code should be reusable for that.
- Prices, payment, or tracking who owes what.
- Ordering for future days; only "today".
- Public holidays (weekdays only; no holiday calendar).
- Proactive bot messages (reminders, "your order was removed by the manager").
  Outgoing webhooks cannot post unprompted; this is accepted.
- Ordinary group chats or 1:1 chat with the bot. Outgoing webhooks only work
  in team channels; the Lunch team solves this.
- Microsoft (Entra ID) login for the admin screen.
- Multiple offices / multiple shops at once.
- Remembering half-finished orders. When the bot asks a question, the person
  retypes the whole order; every message is read on its own.

## Anti-goals (would be a failure even if it "works")

- An order is silently lost, or someone gets a confirmation and their order
  is missing from the draft email.
- Anyone needs to install anything or ask a Microsoft 365 admin to start using it.
- Two of the three builders end up editing the same code.

## Constraints

- Backend: **Java + Spring Boot**. Front-end: **React**. (Decided by the team.)
- Teams integration: **Teams outgoing webhook** on the Lunch team. (Decided;
  Lunch team created, and *Manage team → Apps → Create an outgoing webhook*
  is confirmed available.)
  - Teams POSTs each @mention to our HTTPS endpoint, signed with HMAC-SHA256
    using the security token Teams shows once when the webhook is created.
  - We must reply within ~5 seconds; the reply is shown in the thread.
  - The endpoint must be publicly reachable over HTTPS.
  - The webhook applies to the whole team; the bot answers in whichever
    channel it is @mentioned. Sandwishes is the intended one.
- Only the order manager sees the admin screen.
- The manager must be able to change the shop email address (to switch
  sandwich shop).

## Approaches considered

**Teams connection** (team chose the webhook):
1. *Outgoing webhook*: chosen. A team owner can add it, no tenant admin
   needed, all logic stays in our code. Limits: team channels only,
   @mention required, no proactive messages.
2. *Power Automate*: low/no code, but logic lives in a flow designer, which is
   hard to split across three people and hard to test.
3. *Full Teams bot*: most capable (group chats, 1:1, reminders, cards) but
   needs the Microsoft 365 admin to allow the app. Possible later upgrade;
   only Part 1 would change.

**Application structure** (team chose A):
- **A. One Spring Boot app as a Maven multi-module project + React app**:
  chosen. Module boundaries enforce separation between builders; one
  database, one deployment.
- B. Two separate Java services + React: stricter isolation, but two
  deployments, inter-service HTTP and its failure modes; too heavy for one
  office's lunch.

**Reading messages** (team chose B):
- A. Every message goes to the AI. One path, but every order waits on the
  AI and costs a call.
- **B. Strict format first, AI as a fallback**: chosen. `sandwich: ham &
  cheese` and the fixed commands are handled instantly by code. Only
  messages that don't fit go to the AI.

**Email** (team decided): automatic sending dropped for now (it needed either
Microsoft 365 admin consent or a third-party mail service). Replaced with a
copy-paste draft on the admin screen.

## Design

### Repository layout

```
bug-farm/
  pom.xml                 parent POM (shared, change by agreement only)
  contracts/              shared types + interfaces + openapi.yaml (agreed day 1)
  orders/                 Bert: Teams webhook + order rules + order storage
  admin/                  Thijs: admin API, login, menu, settings, draft email
  app/                    Spring Boot main class + application config (Thijs owns)
  frontend/               Robin: React admin screen
  docs/
```

Dependencies: `orders` → `contracts`; `admin` → `contracts`; `app` → all.
`orders` and `admin` never depend on each other. They meet only through
interfaces in `contracts`, wired by Spring at runtime.

### The contracts (agreed together on day 1, before other work)

In `contracts`:

- `Order` (record): `id`, `teamsUserId` (AAD object id), `displayName`,
  `foodType`, `item`, `notes`, `orderDate`, `placedAt`. Food type and item
  are stored as text at order time, so later menu edits don't change
  existing orders.
- `OrderService`: *implemented by orders, used by admin*.
  `List<Order> ordersFor(LocalDate)`, `void deleteOrder(id)`.
- `MenuCatalog`: *implemented by admin, used by orders*.
  List food types and their items; look up an item by type + name.
- `OrderingWindow`: *implemented by admin, used by orders*.
  `boolean isOpen(Instant now)` (false on weekends and after the cut-off),
  `LocalTime cutOff()`.
- `OrderInterpreter`: *implemented by admin, used by orders*.
  `Interpretation interpret(String messageText)` → intent (`ORDER`,
  `CANCEL`, `MY_ORDER`, `MENU`, `UNCLEAR`), food type, item, notes, and a
  reply text for the person when unclear. Gets only the message text,
  never the person's name or Teams id.
- `openapi.yaml`: the admin REST API, **owned by Thijs**, consumed by
  Robin. Changes after day 1 are announced in Teams before merging.

### Part 1: Teams & orders (Bert, `orders` module)

- `POST /api/teams/webhook`: verify HMAC signature (reject otherwise), strip
  the `<at>…</at>` mention, parse the command, reply with a Teams message JSON.
- Commands (case-insensitive):
  - `menu`: lists food types and items from `MenuCatalog`.
  - `<food type>: <item>[, <notes>]`, e.g. `sandwich: ham & cheese, no butter`.
    The item must match a menu item (case-insensitive). Creates or
    **replaces** the sender's order for today. Reply: "Got it, Jan:
    Sandwich, Ham & cheese (no butter)".
  - `my order`: shows today's order or "no order yet".
  - `cancel`: removes today's order.
  - `help`: short usage text, including a note that free-text messages
    are read by an AI service, so don't put other people's details in them.
  - **Anything else** goes to `OrderInterpreter`, and its result is handled
    like the matching command above. `UNCLEAR` → its reply text is sent
    back (e.g. "We don't have salami. Did you mean Ham & cheese or Chicken
    curry?") and nothing is saved.
- Before saving, the item is always checked against the menu in code,
  whichever path produced it. Not on the menu → reply with that food
  type's menu, nothing saved.
- If `OrderingWindow.isOpen` is false: "Ordering is closed for today."
- One order per person per day, keyed on the Teams AAD object id.
- Owns the `orders` table and its Flyway migrations.
- Owns the security config for `/api/teams/**`: excluded from admin login,
  protected by the HMAC check instead.

### Part 2: Admin backend & draft email (Thijs, `admin` + `app` modules)

- Login: one admin account (username + BCrypt password hash from
  configuration), Spring Security session cookie. All `/api/admin/**`
  routes require it.
- REST API (defined in `openapi.yaml`): today's orders (via `OrderService`),
  delete order, CRUD for food types and menu items, get/update settings,
  get today's draft email.
- Settings stored in the DB: cut-off time (default 10:00), shop email
  address. Time zone from configuration.
- Implements `MenuCatalog` and `OrderingWindow`. The cut-off is a clock
  check when a message arrives; no scheduled job is needed.
- Draft email endpoint returns `to`, `subject` (e.g. "Order Lunch, Fri
  20 Nov") and `body` (plain text):
  1. Summary: each item with a count (e.g. "3 × Ham & cheese").
  2. Per person: name, item, notes, for labelling the bags.

  The draft is built in its own class so automatic sending can reuse it later.
- Owns menu and settings tables and their migrations.
- *Replaced on 2026-11-20 by numbered menu suggestions; see
  `2026-11-20-menu-suggestions-design.md`. The AI part below is no longer built.*
- Implements `OrderInterpreter` with **Claude Haiku 5.5**
  (`claude-haiku-5-5`) through Anthropic's official Java SDK
  (`com.anthropic`):
  - One call per message: today's menu in the system prompt, the message
    text as the user turn, a JSON-schema structured output for the
    `Interpretation` fields. Low effort; it's a short extraction.
  - Sends only the message text and the menu.
  - Hard time limit about 3.5 s (Teams gives us 5 s in total), no retries.
    Timeout, error or refusal → `UNCLEAR` with "Sorry, I couldn't read
    that. Try e.g. `sandwich: ham & cheese, no butter`."
  - API key from the `ANTHROPIC_API_KEY` environment variable. The team
    needs an Anthropic API account.
  - Can be tested without the real API by faking the client; the interface
    lets Bert use a stub until it's ready.

### Part 3: Admin screen (Robin, `frontend`)

React + TypeScript (Vite). Built against a mock of `openapi.yaml` until the
real API is ready.

```
┌ Lunch orders ─ Fri 20 Nov ──────────── Ordering open until 10:00 ┐
│ [Today's orders]  [Menu]  [Settings]                              │
│  Jan Peeters    Sandwich   Ham & cheese      no butter     [x]    │
│  Lisa Maes      Sandwich   Tuna               —            [x]    │
│  12 orders                                   [Draft email]        │
└───────────────────────────────────────────────────────────────────┘

┌ Draft email ──────────────────────────────────────────────────────┐
│ To:      orders@sandwichshop.be                         [Copy]    │
│ Subject: Order Lunch, Fri 20 Nov                        [Copy]    │
│ ┌───────────────────────────────────────────────────────┐         │
│ │ 3 × Ham & cheese                                      │ [Copy]  │
│ │ 2 × Tuna  ...                                         │         │
│ │ Jan Peeters: Ham & cheese (no butter) ...             │         │
│ └───────────────────────────────────────────────────────┘         │
└───────────────────────────────────────────────────────────────────┘
```

- Login page.
- Today's orders: list, remove (with confirm), open/closed status.
- Menu: add/rename/remove food types and items.
- Settings: cut-off time, shop email address.
- Draft email: To / Subject / Body, each with a Copy button. The draft can
  be opened before the cut-off, with a note that orders may still change.

### Deployment

- Built React assets are bundled into the Spring Boot jar and served by it:
  one deployable.
- First milestone: runs on a developer laptop, reachable by Teams through a
  tunnel (ngrok or VS Code dev tunnels). Hosting target afterwards: Azure
  App Service (Basic tier or similar), HTTPS by default.
- Database: H2 file database locally; PostgreSQL when hosted. Flyway
  migrations per module, each in its own folder (`db/migration/orders`,
  `db/migration/admin`) with its own version range (orders `V1xx`, admin
  `V2xx`), run by one Flyway instance configured in `app`.
- Secrets (Teams security token, admin password hash, Anthropic API key)
  come from environment variables, never from the repo.

## Decisions made by Claude (check these)

- Java 21, Spring Boot 3, Maven multi-module, React with TypeScript + Vite.
- `OrderInterpreter` (the AI part) owned by Thijs, to balance the work
  and because Thijs owns the menu.
- 3.5 s limit on the AI call, no retries, and a fixed fallback reply.
- `app` module owned by Thijs.
- Draft email format: counts per item + per-person list; no prices.
- Single admin account from configuration.
- Laptop + tunnel first, Azure hosting later (hosting and timeline weren't
  decided).
- Time zone configurable.

## Left to the builder

- Exact package names, class structure inside each module, test tooling.
- Teams reply wording.
- Mock tooling for the front-end (e.g. MSW / Prism).
- UI styling and component library.
- CI setup.
