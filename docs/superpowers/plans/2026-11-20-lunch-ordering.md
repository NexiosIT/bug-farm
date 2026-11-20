# Lunch Ordering via Teams Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Before any task, follow the repo's `CLAUDE.md`: confirm who is working, and only do that person's tasks in their folders.

**Goal:** Colleagues order lunch by @mentioning a bot in the Teams channel Sandwishes; ordering closes at 10:00; the order manager copies a draft email for the sandwich shop from a React admin screen.

**Architecture:** One Spring Boot app, built as a Maven multi-module project. `orders` (Bert) and `admin` (Thijs) depend only on `contracts` and are wired together by Spring at runtime. The `app` module (Thijs) holds the main class and configuration. `frontend` (Robin) is a React app, bundled into the jar at the end. Free text that doesn't match the strict format goes to Claude Haiku 5.5, and the result is always checked against the menu in code.

**Tech Stack:** Java 21, Spring Boot 3.5.x (web, security, jdbc, flyway), H2 (local), PostgreSQL (hosted), Anthropic Java SDK `com.anthropic:anthropic-java:3.3.0`, JUnit 5 + AssertJ + Spring Boot Test, React 18 + TypeScript + Vite, Vitest + Testing Library, MSW for API mocks.

**Spec:** `docs/superpowers/specs/2026-11-20-food-ordering-design.md`. Shared copy: https://claude.ai/artifact/CRXjDQTiR7NnDCJqsxTGy7

## How the three of you use this plan

| Section | Owner | Folders they may change | Tasks |
| --- | --- | --- | --- |
| Shared, day 1 | Bert, Thijs and Robin together | shared files (see `CLAUDE.md`) | 1, 2 |
| Bert's section | Bert | `orders/` | 3, 4, 5, 6 |
| Thijs's section | Thijs | `admin/`, `app/` | 7, 8, 9, 10, 14 |
| Robin's section | Robin | `frontend/` | 11, 12, 13 |

The repo's `CLAUDE.md` enforces these boundaries in every Claude session. Start each session by saying who is working, e.g. `I'm Robin, working on my part.` For the shared tasks, say `We're all three working together on Task 1.`

If a task needs a change outside your folders, Claude stops and asks you to raise it with the owner in the Lunch team in Teams. That includes `contracts/` and the parent `pom.xml` after day 1.

Each person works on their own branch: `bert-orders`, `thijs-admin`, `robin-frontend`. Task 14 is Thijs's, because it changes `app/`. All three merge their branches into `develop` together and do the live Teams test together.

Before the first commit, set your Git identity: `git config --global user.name "…"` and `git config --global user.email "…"`.

## Global Constraints

- Java 21; Spring Boot 3.5.x; Maven wrapper (`./mvnw`) committed at the repo root.
- Module dependencies: `orders → contracts`, `admin → contracts`, `app → contracts, orders, admin`. `orders` and `admin` never depend on each other.
- Base package `com.bugfarm.lunch` with sub-packages `.contracts`, `.orders`, `.admin`, `.app`.
- Migrations: orders in `orders/src/main/resources/db/migration/orders/` named `V1xx__*.sql`; admin in `admin/src/main/resources/db/migration/admin/` named `V2xx__*.sql`.
- Secrets and settings come from environment variables only: `LUNCH_TEAMS_SECURITY_TOKEN`, `LUNCH_ADMIN_USERNAME`, `LUNCH_ADMIN_PASSWORD_HASH` (BCrypt), `ANTHROPIC_API_KEY`, `LUNCH_TIME_ZONE` (default `Europe/Brussels`).
- One shared `java.time.Clock` bean in `app`, using `LUNCH_TIME_ZONE`. "Today" is always `LocalDate.now(clock)`. Never use the server's default zone.
- Ordering is open on Monday to Friday only, before the cut-off. Default cut-off is `10:00`.
- AI: model `claude-haiku-5-5`. Send only the message text and today's menu, never a name or Teams id. Time limit 3.5 s, 0 retries.
- Fixed reply texts:
  - Closed: `Ordering is closed for today.`
  - Confirmation: `Got it, {displayName}: {FoodType}, {Item}` plus ` ({notes})` when there are notes.
  - AI fallback: `Sorry, I couldn't read that. Try e.g. sandwich: ham & cheese, no butter.`
- One order per person per day, keyed on the Teams `aadObjectId`. A new order replaces the old one.

## Review Focus

1. **Teams message text arrives wrapped in HTML.** For example `<at>SandwichBot</at>&nbsp;sandwich: tuna`, sometimes inside `<p>…</p>`. The bot must read it exactly like `sandwich: tuna`. Test: Task 5 `MessageTextTest`.
2. **Time edges.** An order at 09:59:59 is accepted; at 10:00:00 it is refused, and on Saturdays it is refused. This uses the configured zone even when the server runs in UTC. A refusal also applies when the AI says "order". Tests: Task 7 `SettingsOrderingWindowTest`, Task 6 `OrderHandlerTest.closedWindowRefusesAiOrder`.
3. **The AI returns a spelling or item that isn't on the menu,** e.g. `ham and cheese`, `HAM & CHEESE` or `salami`. Matching is case-insensitive. What gets saved is the menu's own spelling, and anything not on the menu is never saved. Tests: Task 6 `OrderHandlerTest`.
4. **The same person orders twice, or cancels and orders again.** There is always exactly one row, and the draft email lists them once. Tests: Task 3 `JdbcOrderServiceTest`, Task 9 `EmailDraftBuilderTest`.
5. **The manager enters bad settings,** such as `25:00` or `not-an-email`, or renames or deletes a menu item that someone already ordered. Bad settings get a 400 and nothing changes. Existing orders keep their saved text. Tests: Task 8 `AdminApiTest`, Task 9 `EmailDraftBuilderTest.deletedMenuItemStillListed`.

---

## Shared section: Bert, Thijs and Robin together (day 1)

### Task 1: Project skeleton that boots

**Owner:** Bert, Thijs and Robin together (shared files)

**Files:**
- Create: `pom.xml` (parent, packaging `pom`, modules `contracts orders admin app`, Spring Boot parent 3.5.x, `java.version` 21)
- Create: `contracts/pom.xml`, `orders/pom.xml`, `admin/pom.xml`, `app/pom.xml`
- Create: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*` (via `mvn wrapper:wrapper`)
- Create: `app/src/main/java/com/bugfarm/lunch/app/LunchApplication.java`, with `@SpringBootApplication(scanBasePackages = "com.bugfarm.lunch")`
- Create: `app/src/main/java/com/bugfarm/lunch/app/ClockConfig.java`
- Create: `app/src/main/resources/application.yml`
- Create: `.gitignore` (target/, node_modules/, dist/, .idea/, *.mv.db)
- Test: `app/src/test/java/com/bugfarm/lunch/app/LunchApplicationTest.java`

**Interfaces:**
- Produces: a `Clock` bean, `Clock.system(ZoneId.of(${lunch.time-zone}))`.
- Produces: `application.yml` keys:
  - `lunch.time-zone: ${LUNCH_TIME_ZONE:Europe/Brussels}`
  - `lunch.teams.security-token: ${LUNCH_TEAMS_SECURITY_TOKEN:}`
  - `lunch.admin.username: ${LUNCH_ADMIN_USERNAME:admin}`
  - `lunch.admin.password-hash: ${LUNCH_ADMIN_PASSWORD_HASH:}`
  - `lunch.ai.model: claude-haiku-5-5`
  - `lunch.ai.timeout: 3500ms`
  - `spring.datasource.url: jdbc:h2:file:./data/lunch`
  - `spring.flyway.locations: classpath:db/migration/orders,classpath:db/migration/admin`

- [ ] **Step 1: Write `LunchApplicationTest`.** `@SpringBootTest` with `contextLoads()`, plus `clockUsesConfiguredZone()`, which asserts `clock.getZone()` equals `ZoneId.of("Europe/Brussels")`.
- [ ] **Step 2: Run** `./mvnw -q test`. Expected: FAIL (no main class / no Clock bean).
- [ ] **Step 3: Create the poms, main class, `ClockConfig` and `application.yml` as listed.** `app` depends on `spring-boot-starter-web`, `-security`, `-jdbc`, `flyway-core`, `h2`, `postgresql` (runtime), and the three modules. `orders` and `admin` depend on `contracts`, `spring-boot-starter-web`, `-security`, `-jdbc` and `-validation`. For tests they also use `spring-boot-starter-test`, `spring-security-test`, `flyway-core` and `h2`. Each of them gets a test-only `src/test/java/com/bugfarm/lunch/<module>/TestApp.java` annotated `@SpringBootApplication`, so `@JdbcTest`, `@WebMvcTest` and `@SpringBootTest` find a configuration inside the module. Only `app` uses the Spring Boot Maven plugin.
- [ ] **Step 4: Run** `./mvnw -q test`. Expected: PASS.
- [ ] **Step 5: Commit** with `git add -A && git commit -m "chore: multi-module Spring Boot skeleton"`.

### Task 2: The contracts and the API description

**Owner:** Bert, Thijs and Robin together (shared files)

**Files:**
- Create in `contracts/src/main/java/com/bugfarm/lunch/contracts/`: `Order.java`, `OrderService.java`, `MenuItem.java`, `MenuCatalog.java`, `OrderingWindow.java`, `Intent.java`, `Interpretation.java`, `OrderInterpreter.java`
- Create: `contracts/openapi.yaml`
- Test: `contracts/src/test/java/com/bugfarm/lunch/contracts/InterpretationTest.java`

**Interfaces (Produces, used by every later task):**

```java
public record Order(UUID id, String teamsUserId, String displayName, String foodType,
                    String item, String notes, LocalDate orderDate, Instant placedAt) {}   // notes: "" when none

public interface OrderService {
    List<Order> ordersFor(LocalDate date);           // sorted by displayName
    boolean deleteOrder(UUID id);                    // false if not found
}

public record MenuItem(long id, String foodType, String name) {}

public interface MenuCatalog {
    List<String> foodTypes();                         // menu spelling, sorted
    List<MenuItem> itemsFor(String foodType);         // case-insensitive type; empty if unknown
    Optional<MenuItem> find(String foodType, String itemName);  // case-insensitive, trimmed
}

public interface OrderingWindow {
    boolean isOpen(Instant now);
    LocalTime cutOff();
}

public enum Intent { ORDER, CANCEL, MY_ORDER, MENU, HELP, UNCLEAR }

public record Interpretation(Intent intent, String foodType, String item, String notes, String reply) {
    public static Interpretation of(Intent intent) { … }           // other fields ""
    public static Interpretation order(String foodType, String item, String notes) { … }
    public static Interpretation unclear(String reply) { … }
}

public interface OrderInterpreter {
    Interpretation interpret(String messageText);     // never throws; failures → UNCLEAR
}
```

`openapi.yaml` (OpenAPI 3.0) describes exactly these endpoints. All of them are under `/api/admin` and need the session cookie, except `login`:

| Method + path | Request | Response |
| --- | --- | --- |
| `POST /login` | form fields `username`, `password` | 200 / 401 |
| `POST /logout` | – | 200 |
| `GET /me` | – | `{username}` / 401 |
| `GET /orders/today` | – | `{date: "2026-11-20", open: bool, cutOff: "10:00", orders: [{id, displayName, foodType, item, notes, placedAt}]}` |
| `DELETE /orders/{id}` | – | 204 / 404 |
| `GET /menu` | – | `[{id, name, items: [{id, name}]}]` |
| `POST /menu/food-types` | `{name}` | 201 `{id, name, items: []}` / 409 duplicate |
| `PUT /menu/food-types/{id}` | `{name}` | 200 / 404 / 409 |
| `DELETE /menu/food-types/{id}` | – | 204 (its items go too) |
| `POST /menu/food-types/{id}/items` | `{name}` | 201 `{id, name}` / 404 / 409 |
| `PUT /menu/items/{id}` | `{name}` | 200 / 404 / 409 |
| `DELETE /menu/items/{id}` | – | 204 / 404 |
| `GET /settings` | – | `{cutOff: "HH:mm", shopEmail}` |
| `PUT /settings` | `{cutOff, shopEmail}` | 200 / 400 `{error}` |
| `GET /email-draft` | – | `{to, subject, body, final: bool}` |

Unsafe methods send the `X-XSRF-TOKEN` header. Its value is read from the `XSRF-TOKEN` cookie.

- [ ] **Step 1: Write `InterpretationTest`.** Assert that `Interpretation.order("Sandwich","Tuna","")` has intent `ORDER`. Assert that `Interpretation.unclear("x")` has intent `UNCLEAR`, reply `"x"`, and `item` equal to `""`.
- [ ] **Step 2: Run** `./mvnw -q -pl contracts test`. Expected: FAIL (types missing).
- [ ] **Step 3: Create the types above and `openapi.yaml`.**
- [ ] **Step 4: Run** `./mvnw -q -pl contracts test`. Expected: PASS. Then check `openapi.yaml` with `npx @redocly/cli lint contracts/openapi.yaml`. Expected: no errors.
- [ ] **Step 5: Commit** with `git commit -am "feat: shared contracts and admin API description"`, then push. Everyone branches from `develop` here.

---

## Bert's section: Teams and orders (`orders/`)

Package: `com.bugfarm.lunch.orders`. For tests, use the in-memory fakes in `orders/src/test/java/com/bugfarm/lunch/orders/fakes/`:
- `FakeMenuCatalog`: Sandwich with *Ham & cheese*, *Tuna* and *Chicken curry*.
- `FakeOrderingWindow`: open/closed switch, cut-off 10:00.
- `FakeOrderInterpreter`: returns a preset `Interpretation`, and records the text it received.

### Task 3: Storing orders

**Owner:** Bert. Changes only `orders/`.

**Files:**
- Create: `orders/src/main/resources/db/migration/orders/V100__orders.sql`
- Create: `orders/src/main/java/com/bugfarm/lunch/orders/JdbcOrderService.java`
- Test: `orders/src/test/java/com/bugfarm/lunch/orders/JdbcOrderServiceTest.java` (`@JdbcTest` on H2 with Flyway)

**Interfaces:**
- Consumes: `Order` and `OrderService` (Task 2).
- Produces: `JdbcOrderService implements OrderService`, with these extra methods:
  - `Order save(String teamsUserId, String displayName, String foodType, String item, String notes, LocalDate date, Instant placedAt)` inserts or replaces.
  - `Optional<Order> findFor(String teamsUserId, LocalDate date)`
  - `boolean cancel(String teamsUserId, LocalDate date)`

Table `orders`:
- columns `id UUID PK`, `teams_user_id VARCHAR(64)`, `display_name VARCHAR(200)`, `food_type VARCHAR(100)`, `item VARCHAR(200)`, `notes VARCHAR(500) NOT NULL DEFAULT ''`, `order_date DATE`, `placed_at TIMESTAMP WITH TIME ZONE`
- `UNIQUE (teams_user_id, order_date)`

- [ ] **Step 1: Write the tests:**
  - `saveThenFindReturnsOrder`
  - `secondSaveSameDayReplacesFirst`: after two saves there is 1 row, holding the second item.
  - `cancelRemovesOnlyThatDay`
  - `ordersForSortedByDisplayName`
  - `deleteOrderUnknownIdReturnsFalse`
  - `cancelThenOrderAgainLeavesOneRow`
- [ ] **Step 2: Run** `./mvnw -q -pl orders -am test -Dtest=JdbcOrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: FAIL.
- [ ] **Step 3: Write the migration and `JdbcOrderService`.** Use Spring `JdbcClient`. The upsert is a delete-then-insert in one `@Transactional` method, which keeps it portable between H2 and PostgreSQL.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -am "feat(orders): order storage"`.

### Task 4: Checking that a request really comes from Teams

**Owner:** Bert. Changes only `orders/`.

**Files:**
- Create: `orders/src/main/java/com/bugfarm/lunch/orders/teams/HmacVerifier.java`
- Create: `orders/src/main/java/com/bugfarm/lunch/orders/teams/TeamsSecurityConfig.java`
- Test: `orders/src/test/java/com/bugfarm/lunch/orders/teams/HmacVerifierTest.java`

**Interfaces:**
- Produces: `HmacVerifier(String base64SecurityToken)` with `boolean verify(byte[] body, String authorizationHeader)`. The header looks like `HMAC <base64>`, where `<base64>` is the Base64 of HMAC-SHA256 over the raw body. The key is the Base64-decoded token. Compare with `MessageDigest.isEqual`.
- Produces: a `SecurityFilterChain` bean with `@Order(1)` for `securityMatcher("/api/teams/**")`. It permits everything and disables CSRF. The HMAC check happens in the controller (Task 6).

- [ ] **Step 1: Write the tests.** Build the expected header in the test with `javax.crypto.Mac` and a known token, e.g. `Base64("test-secret-key-0123456789abcdef")`.
  - `validSignaturePasses`
  - `tamperedBodyFails`
  - `missingHeaderFails`
  - `headerWithoutHmacPrefixFails`
  - `emptyTokenAlwaysFails`
- [ ] **Step 2: Run** `./mvnw -q -pl orders -am test -Dtest=HmacVerifierTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: FAIL.
- [ ] **Step 3: Implement `HmacVerifier` and `TeamsSecurityConfig`.**
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -am "feat(orders): Teams HMAC verification"`.

### Task 5: Reading the strict format

**Owner:** Bert. Changes only `orders/`.

**Files:**
- Create: `orders/src/main/java/com/bugfarm/lunch/orders/parse/MessageText.java`
- Create: `orders/src/main/java/com/bugfarm/lunch/orders/parse/CommandParser.java`
- Test: `orders/src/test/java/com/bugfarm/lunch/orders/parse/MessageTextTest.java`, `CommandParserTest.java`

**Interfaces:**
- Produces: `static String MessageText.clean(String teamsHtmlText)`. It removes `<at>…</at>` including its content, strips all other tags, decodes `&nbsp; &amp; &lt; &gt; &quot; &#39;`, collapses whitespace and trims.
- Produces: `Optional<Interpretation> CommandParser.parse(String cleanedText)`.
  - Case-insensitive exact matches: `menu` → MENU, `my order` → MY_ORDER, `cancel` → CANCEL, `help` → HELP.
  - `^([^:,]+):([^,]+)(?:,(.*))?$` → ORDER, with each part trimmed and notes `""` when absent.
  - Anything else → `Optional.empty()`, which sends the text to the AI.

- [ ] **Step 1: Write the tests.**
  - `MessageTextTest`:
    - `"<at>SandwichBot</at>&nbsp;sandwich: tuna"` → `"sandwich: tuna"`
    - `"<p><at>SandwichBot</at> Ham &amp; cheese</p>"` → `"Ham & cheese"`
    - `"  <at>Bot</at>  menu \n"` → `"menu"`
  - `CommandParserTest`:
    - `"Sandwich: Ham & cheese, no butter"` → `order("Sandwich","Ham & cheese","no butter")`
    - `"sandwich:tuna"` → notes `""`
    - `"MY ORDER"` → MY_ORDER
    - `"cancel"` → CANCEL
    - `"can I get a ham cheese pls"` → empty
    - `"sandwich:"` → empty
- [ ] **Step 2: Run** `./mvnw -q -pl orders -am test -Dtest='MessageTextTest,CommandParserTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: FAIL.
- [ ] **Step 3: Implement both.**
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -am "feat(orders): strict command parsing"`.

### Task 6: Handling a message and replying to Teams

**Owner:** Bert. Changes only `orders/`.

**Files:**
- Create: `orders/src/main/java/com/bugfarm/lunch/orders/OrderHandler.java`
- Create: `orders/src/main/java/com/bugfarm/lunch/orders/teams/TeamsWebhookController.java`
- Test: `orders/src/test/java/com/bugfarm/lunch/orders/OrderHandlerTest.java`, `orders/src/test/java/com/bugfarm/lunch/orders/teams/TeamsWebhookControllerTest.java` (`@WebMvcTest`)

**Interfaces:**
- Consumes: `CommandParser`, `MessageText` (Task 5), `JdbcOrderService` (Task 3), `HmacVerifier` (Task 4), `MenuCatalog`, `OrderingWindow`, `OrderInterpreter`, `Clock`.
- Produces: `String OrderHandler.handle(String teamsUserId, String displayName, String cleanedText)`. It calls `parser.parse(text).orElseGet(() -> interpreter.interpret(text))`, then:
  - **HELP** → usage text. It lists the four commands and the format, and ends with `Free-text messages are read by an AI service, so please don't include other people's details.`
  - **MENU** → `Today's menu:` followed by one line per food type: `{Type}: {item1}, {item2}, …`.
  - **MY_ORDER** → the confirmation format without `Got it, ` (i.e. `{displayName}: …`), or `You have no order yet today.`
  - **UNCLEAR** → `interpretation.reply()`, and nothing is saved.
  - **ORDER / CANCEL while the window is closed** → `Ordering is closed for today.`
  - **CANCEL** → `Your order for today is cancelled.`, or `You have no order to cancel.`
  - **ORDER** → `menu.find(type, item)`:
    - present → save, using the `MenuItem`'s spelling (food type and name), then reply with the confirmation.
    - unknown food type → `We don't have {type}. We do: {types}.`
    - unknown item → `We don't have {item}. On the {Type} menu: {items}.`
- Produces: `POST /api/teams/webhook`, which takes the raw `byte[]` body.
  - Verify the HMAC; failure → 401 with no body.
  - Parse the JSON with Jackson: `text`, `from.aadObjectId`, `from.name`.
  - Reply 200 with `{"type":"message","text": <reply>}`.

- [ ] **Step 1: Write the `OrderHandlerTest` tests:**
  - `strictOrderSavesWithMenuSpelling`: `"sandwich: HAM & CHEESE, no butter"` saves `Sandwich`/`Ham & cheese` and replies `Got it, Jan: Sandwich, Ham & cheese (no butter)`.
  - `strictOrderSkipsAi`: the fake interpreter receives no call.
  - `freeTextGoesToAiAndIsValidated`: the AI returns `order("sandwich","tuna","")` → saved as `Tuna`.
  - `aiItemNotOnMenuIsNotSaved`: the AI returns `order("Sandwich","Salami","")` → reply starts `We don't have Salami.` and no row is saved.
  - `strictItemNotOnMenuListsMenu`
  - `aiUnclearRepliesWithItsText`
  - `closedWindowRefusesStrictOrder`
  - `closedWindowRefusesAiOrder`
  - `menuAndMyOrderWorkWhenClosed`
  - `aiNeverReceivesDisplayName`: the text recorded by the fake interpreter does not contain `Jan`.
- [ ] **Step 2: Write the `TeamsWebhookControllerTest` tests:**
  - `badSignatureReturns401`
  - `validRequestRepliesWithMessageJson`: the body includes `<at>SandwichBot</at>&nbsp;menu`; assert `$.type == "message"` and `$.text` starts with `Today's menu:`.
- [ ] **Step 3: Run** `./mvnw -q -pl orders -am test`. Expected: FAIL in the new tests.
- [ ] **Step 4: Implement `OrderHandler` and the controller.** Until Thijs's code is merged, the real `OrderInterpreter` comes only from the test fakes.
- [ ] **Step 5: Run** `./mvnw -q -pl orders -am test`. Expected: all PASS.
- [ ] **Step 6: Commit** with `git commit -am "feat(orders): Teams webhook and order handling"`.

---

## Thijs's section: Admin backend, draft email and AI (`admin/`, `app/`)

Package: `com.bugfarm.lunch.admin`.

### Task 7: Menu, settings, and when ordering is open

**Owner:** Thijs. Changes only `admin/`.

**Files:**
- Create: `admin/src/main/resources/db/migration/admin/V200__menu.sql`, `V201__settings.sql`
- Create: `admin/src/main/java/com/bugfarm/lunch/admin/menu/MenuRepository.java`, `JdbcMenuCatalog.java`
- Create: `admin/src/main/java/com/bugfarm/lunch/admin/settings/SettingsRepository.java`, `Settings.java`, `SettingsOrderingWindow.java`
- Test: `admin/src/test/java/com/bugfarm/lunch/admin/menu/JdbcMenuCatalogTest.java`, `admin/src/test/java/com/bugfarm/lunch/admin/settings/SettingsOrderingWindowTest.java`

**Interfaces:**
- Consumes: `MenuCatalog`, `MenuItem`, `OrderingWindow` (Task 2), and `Clock`.
- Tables:
  - `food_type(id BIGINT IDENTITY PK, name VARCHAR(100) UNIQUE)`
  - `menu_item(id BIGINT IDENTITY PK, food_type_id BIGINT REFERENCES food_type ON DELETE CASCADE, name VARCHAR(200), UNIQUE(food_type_id, name))`
  - `settings(id INT PK CHECK (id = 1), cut_off TIME, shop_email VARCHAR(320))`, seeded with `(1, '10:00', '')`
  - V200 also seeds food type `Sandwich` with no items.
- Produces: `record Settings(LocalTime cutOff, String shopEmail)`, plus `SettingsRepository.get()` and `SettingsRepository.update(Settings)`.
- Produces: `MenuRepository`, with `List<FoodTypeView> all()`, `createFoodType(name)`, `renameFoodType(id, name)`, `deleteFoodType(id)`, `addItem(foodTypeId, name)`, `renameItem(id, name)` and `deleteItem(id)`.
  - `FoodTypeView(long id, String name, List<ItemView> items)`, `ItemView(long id, String name)`.
  - Duplicates (case-insensitive) throw `DuplicateNameException`; unknown ids throw `NotFoundException`.
- Produces: `JdbcMenuCatalog implements MenuCatalog` and `SettingsOrderingWindow implements OrderingWindow`. `isOpen` returns true when the date in the clock's zone is Mon–Fri and the local time is before `cutOff`.

- [ ] **Step 1: Write the tests.**
  - `JdbcMenuCatalogTest`:
    - `findIsCaseInsensitiveAndTrimmed`: `("  sandwich ", "HAM & CHEESE")` finds `Ham & cheese`.
    - `unknownTypeGivesEmptyItems`
    - `duplicateItemNameRejectedIgnoringCase`
  - `SettingsOrderingWindowTest`, using `Clock.fixed` with zone `Europe/Brussels`:
    - `openAt0959_59OnWednesday`
    - `closedAt1000OnWednesday`
    - `closedOnSaturdayMorning`
    - `usesConfiguredZoneNotUtc`: 08:30 UTC is 09:30 Brussels in winter, so it is open; 09:30 UTC is 10:30 Brussels, so it is closed.
    - `followsUpdatedCutOff`
- [ ] **Step 2: Run** `./mvnw -q -pl admin -am test`. Expected: FAIL.
- [ ] **Step 3: Implement the migrations, repositories, `JdbcMenuCatalog` and `SettingsOrderingWindow`.**
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -am "feat(admin): menu, settings, ordering window"`.

### Task 8: Admin login and REST API

**Owner:** Thijs. Changes only `admin/`.

**Files:**
- Create: `admin/src/main/java/com/bugfarm/lunch/admin/web/AdminSecurityConfig.java`
- Create: `admin/src/main/java/com/bugfarm/lunch/admin/web/OrdersController.java`, `MenuController.java`, `SettingsController.java`, `MeController.java`, `ApiErrors.java` (`@RestControllerAdvice`)
- Test: `admin/src/test/java/com/bugfarm/lunch/admin/web/AdminApiTest.java` (`@SpringBootTest` + `MockMvc`; a test config provides a fake `OrderService` and a fixed `Clock`)

**Interfaces:**
- Consumes: `OrderService` (Task 2, implemented by Bert), `MenuRepository`, `SettingsRepository`, `OrderingWindow` (Task 7).
- Produces: every endpoint in `contracts/openapi.yaml` except `/email-draft`, with the exact JSON shapes listed there.
- `AdminSecurityConfig` is the catch-all `SecurityFilterChain` (`@Order(2)`):
  - `/api/admin/**` is authenticated; everything else (the static frontend) is permitted.
  - `formLogin` uses `loginProcessingUrl("/api/admin/login")`. Success returns 200 and failure returns 401, with no redirects.
  - `logout` is at `/api/admin/logout` and returns 200. An unauthenticated request returns 401, not a redirect.
  - CSRF uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
  - One user, `InMemoryUserDetailsManager`, from `lunch.admin.username` / `lunch.admin.password-hash`, with `BCryptPasswordEncoder`.
- Validation:
  - `cutOff` must match `^([01]\d|2[0-3]):[0-5]\d$`.
  - `shopEmail` must be empty or a valid email (`jakarta.validation` `@Email`).
  - Anything invalid → 400 `{error: "<field>: <reason>"}`.
  - `DuplicateNameException` → 409; `NotFoundException` → 404.

- [ ] **Step 1: Write the `AdminApiTest` tests:**
  - `unauthenticatedGets401`
  - `loginWithWrongPasswordGets401`
  - `loginThenMeReturnsUsername`
  - `todayOrdersShowsOpenAndCutOff`
  - `deleteUnknownOrderGives404`
  - `menuCrudRoundTrip`
  - `duplicateFoodTypeGives409`
  - `settingsRejects2500With400AndKeepsOldValue`
  - `settingsRejectsBadEmail`
  - `postWithoutCsrfGets403`
- [ ] **Step 2: Run** `./mvnw -q -pl admin -am test -Dtest=AdminApiTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: FAIL.
- [ ] **Step 3: Implement the security config, the controllers and `ApiErrors`.**
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -am "feat(admin): login and admin REST API"`.

### Task 9: Draft email

**Owner:** Thijs. Changes only `admin/`.

**Files:**
- Create: `admin/src/main/java/com/bugfarm/lunch/admin/email/EmailDraft.java`, `EmailDraftBuilder.java`, `EmailDraftController.java`
- Test: `admin/src/test/java/com/bugfarm/lunch/admin/email/EmailDraftBuilderTest.java`

**Interfaces:**
- Consumes: `Order`, `OrderService`, `OrderingWindow`, `SettingsRepository`, `Clock`.
- Produces:
  - `record EmailDraft(String to, String subject, String body, boolean finalDraft)`. The JSON field `final` maps to `finalDraft`.
  - `EmailDraft EmailDraftBuilder.build(List<Order> orders, LocalDate date, String shopEmail, boolean finalDraft)`, a pure function.
  - `GET /api/admin/email-draft`.
- Fixed format, English locale:
  - subject: `Order Lunch, {EEE d MMM}`, e.g. `Order Lunch, Fri 20 Nov`.
  - body:
    ```
    Order for Fri 20 Nov

    3 × Ham & cheese
    2 × Tuna

    Per person:
    Jan Peeters: Ham & cheese (no butter)
    Lisa Maes: Tuna
    ```
  - Summary lines are sorted by count descending, then by name.
  - When the day has more than one food type, each summary line ends with ` ({FoodType})`.
  - Per-person lines are sorted by name, and show ` ({notes})` only when notes are non-empty.
  - No orders → body `Order for {date}\n\nNo orders today.`

- [ ] **Step 1: Write the tests:**
  - `subjectAndSummaryMatchSpecExample`: uses the five orders implied above and asserts the whole body string.
  - `foodTypeShownOnlyWhenSeveral`
  - `noOrdersBody`
  - `deletedMenuItemStillListed`: an order whose item is no longer on the menu still appears exactly as saved.
  - `toIsShopEmail`
- [ ] **Step 2: Run** `./mvnw -q -pl admin -am test -Dtest=EmailDraftBuilderTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: FAIL.
- [ ] **Step 3: Implement the builder and the controller.** The controller sets `finalDraft = !orderingWindow.isOpen(clock.instant())`.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -am "feat(admin): draft order email"`.

### Task 10: AI fallback with Claude Haiku 5.5

**Owner:** Thijs. Changes only `admin/`.

**Files:**
- Modify: `admin/pom.xml`. Add `com.anthropic:anthropic-java:3.3.0`.
- Create in `admin/src/main/java/com/bugfarm/lunch/admin/ai/`:
  - `AiResult.java`
  - `ClaudeCaller.java`
  - `AnthropicClaudeCaller.java`
  - `InterpreterPrompt.java`
  - `ClaudeOrderInterpreter.java`
  - `AiConfig.java`
- Test: `admin/src/test/java/com/bugfarm/lunch/admin/ai/ClaudeOrderInterpreterTest.java`, `InterpreterPromptTest.java`, `AnthropicClaudeCallerLiveTest.java` (`@Tag("live")`, skipped unless `ANTHROPIC_API_KEY` is set)

**Interfaces:**
- Consumes: `OrderInterpreter`, `Interpretation`, `Intent`, `MenuCatalog`.
- Produces:
  - `record AiResult(Intent intent, String foodType, String item, String notes, String reply)`. Its fields carry `@JsonPropertyDescription` so the derived schema explains them.
  - `interface ClaudeCaller { Optional<AiResult> call(String systemPrompt, String userText); }`. Empty means a refusal, timeout or error.
  - `static String InterpreterPrompt.system(Map<String, List<String>> menu)`. The prompt:
    - says Claude reads one lunch-order message from an office Teams channel;
    - lists the menu;
    - lists the intents;
    - says to map near-spellings to the menu's exact item name;
    - says to use UNCLEAR with a short friendly reply (max 2 sentences, naming close menu options) when the item is missing, ambiguous, or the message is not about lunch;
    - says to put extras like "no butter" in notes;
    - says never to invent items.
  - `ClaudeOrderInterpreter implements OrderInterpreter`, a `@Component`. It builds the menu map from `MenuCatalog` on each call and calls `ClaudeCaller`. It maps `AiResult` to `Interpretation`; empty or exception → `unclear("Sorry, I couldn't read that. Try e.g. sandwich: ham & cheese, no butter.")`.
  - `AnthropicClaudeCaller` implements the call with the SDK:
    - client: `AnthropicOkHttpClient.builder().fromEnv().timeout(Duration.ofMillis(3500)).maxRetries(0).build()`;
    - request: `MessageCreateParams.builder().model("claude-haiku-5-5").maxTokens(1024L).system(systemPrompt).addUserMessage(userText).outputConfig(AiResult.class).build()`, which gives `StructuredMessageCreateParams<AiResult>`;
    - response: read the typed `.text()` from the first text block; a `refusal` stop reason → empty; any exception → log at WARN without the message text, then empty.
    - If the compiler rejects a builder name, look it up with `jar tf` / `javap` on the SDK jar. Don't guess further.
  - `AiConfig`: when `ANTHROPIC_API_KEY` is blank, register a `ClaudeCaller` that always returns empty, so local runs without a key still work and reply with the fallback.

- [ ] **Step 1: Write the tests.**
  - `InterpreterPromptTest`:
    - `listsEveryMenuItemExactly`
    - `containsNoPersonalFields`: the prompt built from the menu contains only menu text and the instructions.
  - `ClaudeOrderInterpreterTest`, with a fake `ClaudeCaller`:
    - `mapsOrderResult`
    - `emptyResultGivesFallbackText`
    - `callerExceptionGivesFallbackText`
    - `passesOnlyMessageTextAsUserTurn`: the fake records `userText` equal to the input.
    - `unclearReplyPassedThrough`
  - `AnthropicClaudeCallerLiveTest.realOrderIsUnderstood`: `"can I get a ham cheese no butter pls"` with a Sandwich menu → ORDER, `Ham & cheese`, notes containing `butter`, in under 3.5 s.
- [ ] **Step 2: Run** `./mvnw -q -pl admin -am test -Dtest='ClaudeOrderInterpreterTest,InterpreterPromptTest' -Dsurefire.failIfNoSpecifiedTests=false`. Expected: FAIL.
- [ ] **Step 3: Implement the files above.**
- [ ] **Step 4: Run the same command.** Expected: PASS. Then, with a key set, run `./mvnw -q -pl admin -am test -Dgroups=live -Dtest=AnthropicClaudeCallerLiveTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -am "feat(admin): AI fallback with Claude Haiku 5.5"`.

---

## Robin's section: Admin screen (`frontend/`)

### Task 11: Frontend skeleton, API client, mocks and login

**Owner:** Robin. Changes only `frontend/`.

**Files:**
- Create: `frontend/` via `npm create vite@latest frontend -- --template react-ts`; add `vitest`, `@testing-library/react`, `@testing-library/user-event`, `jsdom` and `msw`
- Create: `frontend/src/api.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/browser.ts`, `frontend/src/LoginPage.tsx`, `frontend/src/App.tsx`
- Modify: `frontend/vite.config.ts`. Proxy `/api` to `http://localhost:8080`, and use the vitest `jsdom` environment.
- Test: `frontend/src/LoginPage.test.tsx`, `frontend/src/api.test.ts`

**Interfaces:**
- Consumes: `contracts/openapi.yaml` (Task 2).
- Produces: `api.ts` with typed functions mirroring the API table:
  - `login(username, password): Promise<boolean>`, `logout()`, `me(): Promise<string | null>`
  - `todayOrders(): Promise<TodayOrders>`, `deleteOrder(id)`
  - `getMenu(): Promise<FoodType[]>`, `createFoodType(name)`, `renameFoodType(id, name)`, `deleteFoodType(id)`, `addItem(foodTypeId, name)`, `renameItem(id, name)`, `deleteItem(id)`
  - `getSettings(): Promise<Settings>`, `saveSettings(s): Promise<{ok: true} | {ok: false, error: string}>`
  - `emailDraft(): Promise<EmailDraft>`

  Every call uses `credentials: 'same-origin'`, and unsafe methods add the `X-XSRF-TOKEN` header from the cookie. When `VITE_MOCK=1`, MSW serves `handlers.ts` with data that matches the API description.

- [ ] **Step 1: Write the tests.**
  - `api.test.ts`:
    - `postSendsXsrfHeaderFromCookie`
    - `loginReturnsFalseOn401`
  - `LoginPage.test.tsx`:
    - `showsErrorOnWrongPassword`
    - `callsOnLoggedInAfterSuccess`
- [ ] **Step 2: Run** `npm --prefix frontend test -- --run`. Expected: FAIL.
- [ ] **Step 3: Implement `api.ts`, the mocks, `LoginPage`, and `App`.** `App` calls `me()`. Logged out → login page; logged in → tabs `Today's orders | Menu | Settings`.
- [ ] **Step 4: Run** `npm --prefix frontend test -- --run`. Expected: PASS. `VITE_MOCK=1 npm --prefix frontend run dev` shows the login page.
- [ ] **Step 5: Commit** with `git add frontend && git commit -m "feat(frontend): skeleton, API client, login"`.

### Task 12: Today's orders and the draft email

**Owner:** Robin. Changes only `frontend/`.

**Files:**
- Create: `frontend/src/OrdersPage.tsx`, `frontend/src/EmailDraftView.tsx`
- Test: `frontend/src/OrdersPage.test.tsx`, `frontend/src/EmailDraftView.test.tsx`

**Interfaces:**
- Consumes: `todayOrders`, `deleteOrder`, `emailDraft` (Task 11).
- Produces:
  - `OrdersPage`:
    - header `Lunch orders · {date}` with `Ordering open until {cutOff}` or `Ordering closed`;
    - a table of name, food type, item, notes, and a remove button that asks `Remove {name}'s order?` first;
    - `{n} orders`;
    - a `Draft email` button that opens `EmailDraftView`.
  - `EmailDraftView`:
    - To, Subject and Body, each with a `Copy` button using `navigator.clipboard.writeText`, which shows `Copied` for 2 s;
    - when `final` is false, the note `Ordering is still open — orders may still change.`

- [ ] **Step 1: Write the tests.**
  - `OrdersPage.test.tsx`:
    - `listsOrdersFromApi`
    - `removeAsksConfirmationThenCallsDelete`
    - `showsClosedState`
  - `EmailDraftView.test.tsx`:
    - `copyBodyWritesBodyToClipboard`
    - `showsStillOpenNoteWhenNotFinal`
- [ ] **Step 2: Run** `npm --prefix frontend test -- --run`. Expected: FAIL.
- [ ] **Step 3: Implement both components.**
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -am "feat(frontend): today's orders and draft email"`.

### Task 13: Menu and settings

**Owner:** Robin. Changes only `frontend/`.

**Files:**
- Create: `frontend/src/MenuPage.tsx`, `frontend/src/SettingsPage.tsx`
- Test: `frontend/src/MenuPage.test.tsx`, `frontend/src/SettingsPage.test.tsx`

**Interfaces:**
- Consumes: the menu and settings functions in `api.ts` (Task 11).
- Produces:
  - `MenuPage`:
    - one section per food type, each with its items, rename/remove buttons and an add-item field;
    - an add-food-type field;
    - a 409 shows `That name already exists.`;
    - removing a food type asks `Remove {name} and all its items?` first.
  - `SettingsPage`:
    - a cut-off time input and a shop email input, with Save;
    - a 400 shows the server's `error` text next to the form.

- [ ] **Step 1: Write the tests.**
  - `MenuPage.test.tsx`:
    - `addItemCallsApiAndShowsIt`
    - `duplicateShowsMessage`
    - `removeFoodTypeAsksConfirmation`
  - `SettingsPage.test.tsx`:
    - `savesValues`
    - `showsServerErrorOn400`
- [ ] **Step 2: Run** `npm --prefix frontend test -- --run`. Expected: FAIL.
- [ ] **Step 3: Implement both pages.**
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -am "feat(frontend): menu and settings"`.

---

## Thijs's section, continued: integration (after all branches are merged)

### Task 14: One deployable, and a real test in Teams

**Owner:** Thijs, for the `app/` changes. Bert, Thijs and Robin do the merge, `README.md` (a shared file) and the Teams test together in one joint session.

**Files:**
- Modify: `app/pom.xml`. Add `frontend-maven-plugin` to install Node and run `npm ci` and `npm run build` in `../frontend`, then `maven-resources-plugin` to copy `frontend/dist` into `target/classes/static`.
- Create: `app/src/main/java/com/bugfarm/lunch/app/SpaForwardController.java`. It forwards non-`/api` paths without a dot to `/index.html`.
- Create: `README.md`, which covers the environment variables, how to generate a BCrypt hash, local run, the tunnel, and creating the webhook.
- Test: `app/src/test/java/com/bugfarm/lunch/app/WiringTest.java`

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Write the `WiringTest` tests** (`@SpringBootTest`):
  - `realImplementationsAreWired`: `OrderService` is `JdbcOrderService`, `MenuCatalog` is `JdbcMenuCatalog`, `OrderingWindow` is `SettingsOrderingWindow`, and `OrderInterpreter` is `ClaudeOrderInterpreter`.
  - `webhookAndAdminChainsBothActive`: a POST to `/api/teams/webhook` without a signature gives 401, not 403. GET `/api/admin/me` gives 401.
- [ ] **Step 2: Run** `./mvnw -q test`. Expected: FAIL until the branches are merged.
- [ ] **Step 3: Merge the three branches into `develop`, then add the build plugins and `SpaForwardController`.**
- [ ] **Step 4: Run** `./mvnw -q verify`. Expected: PASS, and `app/target/*.jar` contains `static/index.html` (`jar tf app/target/app-*.jar | grep static/index.html`).
- [ ] **Step 5: Run a manual check in Teams:**
  1. Start the app: `java -jar app/target/app-*.jar` with the env vars set.
  2. Start a tunnel: `ngrok http 8080`.
  3. In Lunch, go to **⋯ → Manage team → Apps → Create an outgoing webhook**. Use the name `SandwichBot` and the callback URL `https://<tunnel>/api/teams/webhook`. Copy the security token into `LUNCH_TEAMS_SECURITY_TOKEN` and restart the app.
  4. In the admin screen, add the items Ham & cheese and Tuna, and set the shop email.
  5. In Sandwishes, check:
     - `@SandwichBot menu` lists both items;
     - `@SandwichBot sandwich: tuna` confirms;
     - `@SandwichBot can I get a ham cheese no butter pls` confirms *Ham & cheese (no butter)*;
     - `@SandwichBot salami please` asks back and saves nothing;
     - `@SandwichBot cancel` cancels.
  6. Set the cut-off to one minute from now. After it passes, an order gets `Ordering is closed for today.` and the draft email shows no "still open" note.
- [ ] **Step 6: Commit** with `git commit -am "chore: bundle frontend into app, README"`.
