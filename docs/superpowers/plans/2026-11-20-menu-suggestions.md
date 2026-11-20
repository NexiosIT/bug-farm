# Menu Suggestions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Claude fallback with numbered menu suggestions: an unrecognised message gets the three most likely menu items, and the person orders one by answering `1`, `2` or `3`.

**Architecture:** In `orders/`, a pure `MenuSuggester` scores menu items against the typed words, and an in-memory `OpenChoices` store (keyed on the Teams `aadObjectId`, `Clock`-based expiry) remembers what each person was offered. `OrderHandler` checks number answers first, then the strict parser, then offers suggestions. Afterwards the Claude code, the `OrderInterpreter` contract and the Anthropic dependency are deleted.

**Tech Stack:** Java 21, Spring Boot 3.5, JUnit 5 + AssertJ, `@JdbcTest` with the existing `orders` test fakes.

**Spec:** `docs/superpowers/specs/2026-11-20-menu-suggestions-design.md` (read it with this plan).

## Global Constraints

- Build/test: `./mvnw -q test` from the repo root, with `JAVA_HOME` on JDK 21 (`C:\Program Files\Microsoft\jdk-21.0.13.102-hotspot`). Faster for one module: `./mvnw -q -pl orders -am test`.
- Base package `com.bugfarm.lunch`; new classes in `com.bugfarm.lunch.orders.suggest`.
- `Clock` is the only time source; "today" is `LocalDate.now(clock)`.
- Reply texts are sentence case, no emoji, no exclamation marks. Teams line breaks are `"\n\n"` (as `menuText()` does today).
- Fixed copy (exact strings):
  - `Did you mean:` header, options as `{n}. {FoodType}, {Item}`, footer `Reply with @SandwichBot 1, 2 or 3.` (two options: `Reply with @SandwichBot 1 or 2.`; one: `Reply with @SandwichBot 1.`) — header, each option and footer joined with `"\n\n"`.
  - `I have no open question for you. Mention me with e.g. sandwich: ham & cheese.`
  - `Please answer 1, 2 or 3.` (two options: `Please answer 1 or 2.`; one: `Please answer 1.`)
  - `That item is no longer on the menu.`
  - `There is nothing on the menu yet.`
  - Closed: `Ordering is closed for today.` · Confirmation: `Got it, {displayName}: {FoodType}, {Item}` plus ` ({notes})` (unchanged).
- Open options expire after **15 minutes** and are only valid on the day they were offered.
- Notes: text after the first comma of the unrecognised message, trimmed; max 500 characters (existing rule, enforced when saving).
- Never save an item that is not on the menu at the moment of saving.

## Review Focus

1. **Someone else answers with a number.** Jan is offered options; Piet sends `2`. Piet gets the "no open question" reply and nothing is saved for anyone; Jan's options stay open. Test: Task 3 `numberFromAnotherPersonDoesNotUseTheirOptions`.
2. **The cut-off passes between the question and the answer.** Options offered at 09:59, `1` at 10:00 → `Ordering is closed for today.`, nothing saved, options cleared. Test: Task 3 `numberAfterCutOffIsRefusedAndClearsOptions`.
3. **The item is removed from the menu before the answer.** → `That item is no longer on the menu.`, nothing saved. Test: Task 3 `pickedItemRemovedFromMenuIsNotSaved`.
4. **A number that doesn't fit.** `4` (or `0`) with three open options → `Please answer 1, 2 or 3.`, options stay open, nothing saved. Test: Task 3 `numberOutOfRangeKeepsOptions`.
5. **Stale options.** Offered yesterday, or 15 minutes ago → treated as no open question. Test: Task 2 `expiresAfterFifteenMinutes`, `expiresAtMidnight`.

---

### Task 1: `MenuSuggester` — score menu items against typed text

**Files:**
- Create: `orders/src/main/java/com/bugfarm/lunch/orders/suggest/MenuSuggester.java`
- Test: `orders/src/test/java/com/bugfarm/lunch/orders/suggest/MenuSuggesterTest.java`

**Interfaces:**
- Produces: `@Component public class MenuSuggester` with `public List<MenuItem> suggest(String typed, List<MenuItem> menu)` — at most 3 items, best first; ties keep the order of `menu`; never null; empty only when `menu` is empty. (`MenuItem` is `contracts` `record MenuItem(long id, String foodType, String name)`.)

- [ ] **Step 1: Write the failing tests.** Test menu (in this order, ids 1..14): Salad: Caesar, Greek · Sandwich: Brie & honey, Chicken curry, Egg salad, Ham & cheese, Martino, Mozzarella & tomato, Smos, Tuna · Soup: Soup of the day, Tomato · Wrap: Chicken caesar, Falafel. Helper `first(String typed)` returns `suggest(typed, MENU).get(0)` as `"FoodType, Name"`.

```java
@Test void exactItemWordWins()        { assertThat(first("Brie")).isEqualTo("Sandwich, Brie & honey"); }
@Test void fillerWordsAreIgnored()    { assertThat(first("smos aub")).isEqualTo("Sandwich, Smos"); }
@Test void andMatchesAmpersand()      { assertThat(first("ham and cheese")).isEqualTo("Sandwich, Ham & cheese");
                                        assertThat(first("HAM & CHEESE")).isEqualTo("Sandwich, Ham & cheese"); }
@Test void prefixOfThreeLetters()     { assertThat(first("moz")).isEqualTo("Sandwich, Mozzarella & tomato"); }
@Test void foodTypeHelps()            { assertThat(first("wrap falafel")).isEqualTo("Wrap, Falafel");
                                        assertThat(first("chicken wrap")).isEqualTo("Wrap, Chicken caesar"); }
@Test void swappedLettersAreATypo()   { assertThat(first("brei")).isEqualTo("Sandwich, Brie & honey"); }
@Test void returnsAtMostThree()       { assertThat(suggester.suggest("chicken", MENU)).hasSize(3); }
@Test void fewerItemsThanThree()      { assertThat(suggester.suggest("tuna", MENU.subList(0, 2))).hasSize(2); }
@Test void nonsenseKeepsMenuOrder()   { assertThat(suggester.suggest("hello", MENU)).extracting(MenuItem::id).containsExactly(1L, 2L, 3L); }
@Test void emptyMenuGivesNothing()    { assertThat(suggester.suggest("tuna", List.of())).isEmpty(); }
@Test void nullOrBlankTextIsNonsense(){ assertThat(suggester.suggest(null, MENU)).extracting(MenuItem::id).containsExactly(1L, 2L, 3L);
                                        assertThat(suggester.suggest("  ", MENU)).hasSize(3); }
```

- [ ] **Step 2: Run** `./mvnw -q -pl orders -am test -Dtest=MenuSuggesterTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: compilation FAIL (`MenuSuggester` missing).

- [ ] **Step 3: Implement `suggest`.** Scoring (weights fixed here so the tests above hold):
  - `words(s)`: lower-case (`Locale.ROOT`), `&` → ` and `, every non-letter/digit → space, split on whitespace, drop empty words and the word `and`.
  - `wordScore(typed, target)`: `3` if equal; `2` if `typed.length() >= 3 && target.startsWith(typed)`; `2` if the optimal-string-alignment distance (Levenshtein plus adjacent transposition, so `brei`/`brie` = 1) is `<= 1` for typed length 4–6 or `<= 2` for length >= 7; else `0`.
  - Item score = sum over typed words of `max(2 × best wordScore against the item-name words, 1 × best wordScore against the food-type words)`.
  - Stable sort by score descending, take 3.

- [ ] **Step 4: Run** the same command. Expected: PASS (11 tests).

- [ ] **Step 5: Commit** `git add orders/src/main/java/com/bugfarm/lunch/orders/suggest/MenuSuggester.java orders/src/test/java/com/bugfarm/lunch/orders/suggest/MenuSuggesterTest.java && git commit -m "feat(orders): score menu items against free text"`

### Task 2: `OpenChoices` — remember what each person was offered

**Files:**
- Create: `orders/src/main/java/com/bugfarm/lunch/orders/suggest/OpenChoices.java`, `orders/src/main/java/com/bugfarm/lunch/orders/suggest/OpenChoice.java`
- Test: `orders/src/test/java/com/bugfarm/lunch/orders/suggest/OpenChoicesTest.java`

**Interfaces:**
- Produces: `public record OpenChoice(List<MenuItem> options, String notes, Instant offeredAt)` (copies `options` with `List.copyOf`; `notes` null → `""`).
- Produces: `@Component public class OpenChoices` with constructor `OpenChoices(Clock clock)` and
  - `void offer(String teamsUserId, List<MenuItem> options, String notes)` — replaces any earlier offer for that person;
  - `Optional<OpenChoice> find(String teamsUserId)` — empty when none, or when `offeredAt` is 15 minutes or more ago, or on an earlier date than `LocalDate.now(clock)` (expired entries are removed on read);
  - `void clear(String teamsUserId)`.
  Thread-safe (`ConcurrentHashMap`). `Duration TTL = Duration.ofMinutes(15)` as a constant.

- [ ] **Step 1: Write the failing tests** with a small mutable test clock (a `Clock` subclass in the test file whose instant you can set; zone `Europe/Brussels`, start `2026-11-20T08:30:00Z`).

```java
@Test void offerThenFind()               // offer("jan", [tuna], "no butter") → find("jan") has options [tuna], notes "no butter"
@Test void otherPersonHasNothing()       // offer("jan", ...) → find("piet") is empty
@Test void newOfferReplacesOld()         // offer twice → find returns the second options
@Test void clearRemoves()                // offer, clear("jan") → find empty
@Test void expiresAfterFifteenMinutes()  // +14m59s → present; +15m → empty
@Test void expiresAtMidnight()           // offer at 2026-11-20T22:55:00Z (23:55 Brussels), clock to 2026-11-20T23:05:00Z (00:05 next day) → empty
```

- [ ] **Step 2: Run** `./mvnw -q -pl orders -am test -Dtest=OpenChoicesTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: compilation FAIL.

- [ ] **Step 3: Implement** `OpenChoice` and `OpenChoices` per the interface above.

- [ ] **Step 4: Run** the same command. Expected: PASS (6 tests).

- [ ] **Step 5: Commit** `git add orders/src/main/java/com/bugfarm/lunch/orders/suggest/OpenChoice*.java orders/src/test/java/com/bugfarm/lunch/orders/suggest/OpenChoicesTest.java && git commit -m "feat(orders): remember offered menu options per person"`

### Task 3: `OrderHandler` — number answers and suggestions instead of the AI

**Files:**
- Modify: `orders/src/main/java/com/bugfarm/lunch/orders/OrderHandler.java`
- Modify: `orders/src/main/java/com/bugfarm/lunch/orders/parse/CommandParser.java` (class comment only: "Anything else gives Optional.empty(); the handler then offers menu suggestions.")
- Modify: `orders/src/test/java/com/bugfarm/lunch/orders/OrderHandlerTest.java`
- Modify: `orders/src/test/java/com/bugfarm/lunch/orders/fakes/FakeMenuCatalog.java` (mutable for tests: `void removeItem(String name)`, `void clear()`, `void reset()` back to the three sandwiches)
- Modify: `orders/src/test/java/com/bugfarm/lunch/orders/fakes/FakesConfig.java` (drop the `FakeOrderInterpreter` bean and the "AI interpreter" mention)
- Delete: `orders/src/test/java/com/bugfarm/lunch/orders/fakes/FakeOrderInterpreter.java`

**Interfaces:**
- Consumes: `MenuSuggester.suggest(String, List<MenuItem>)` (Task 1); `OpenChoices.offer/find/clear`, `OpenChoice` (Task 2).
- Produces: constructor `OrderHandler(CommandParser parser, MenuSuggester suggester, OpenChoices choices, JdbcOrderService orders, MenuCatalog menu, OrderingWindow window, Clock clock)`; `handle(String teamsUserId, String displayName, String cleanedText)` unchanged. No longer depends on `OrderInterpreter`. The `case UNCLEAR` switch branch stays until Task 4 (the parser never returns it).

- [ ] **Step 1: Update the tests.** `@Import` adds `MenuSuggester.class, OpenChoices.class`; `@BeforeEach` calls `menu.reset()` and `window.setOpen(true)`. Delete the AI tests (`strictOrderSkipsAi`, `freeTextGoesToAiAndIsValidated`, `aiItemNotOnMenuIsNotSaved`, `aiUnclearRepliesWithItsText`, `closedWindowRefusesAiOrder`, `aiCancelIsHandledLikeCommand`, `aiNeverReceivesDisplayName`); rename `helpEndsWithAiNotice` to `helpExplainsNumberedSuggestions` asserting the help text contains `1, 2 or 3` and no longer contains `AI`. Add (fake menu: Sandwich — Ham & cheese, Tuna, Chicken curry; user `aad-jan`/`Jan`):

```java
@Test void unrecognisedTextOffersThreeNumberedOptions() {
    assertThat(handler.handle("aad-jan", "Jan", "tuna pls")).isEqualTo(String.join("\n\n",
        "Did you mean:", "1. Sandwich, Tuna", "2. Sandwich, Ham & cheese", "3. Sandwich, Chicken curry",
        "Reply with @SandwichBot 1, 2 or 3."));
    assertThat(saved()).isEmpty();
}
@Test void numberOrdersTheOfferedItemWithNotes()      // "tuna, no butter" then "1" → "Got it, Jan: Sandwich, Tuna (no butter)", one saved order
@Test void hashNumberAndSpacesAreAccepted()           // "tuna" then " #1 " → "Got it, Jan: Sandwich, Tuna"
@Test void numberWithoutOpenQuestion()                // "2" → "I have no open question for you. Mention me with e.g. sandwich: ham & cheese."
@Test void numberFromAnotherPersonDoesNotUseTheirOptions() // jan "tuna"; piet "1" → no-open-question reply; nothing saved; jan "1" still orders Tuna
@Test void numberOutOfRangeKeepsOptions()             // "tuna" then "4" → "Please answer 1, 2 or 3."; then "1" orders Tuna
@Test void answeredOptionsAreCleared()                // "tuna", "1", then "2" → no-open-question reply; still exactly one order
@Test void recognisedCommandClearsOptions()           // "tuna", "menu", then "1" → no-open-question reply
@Test void numberAfterCutOffIsRefusedAndClearsOptions() // "tuna"; window.setOpen(false); "1" → "Ordering is closed for today."; window.setOpen(true); "1" → no-open-question reply; nothing saved
@Test void pickedItemRemovedFromMenuIsNotSaved()      // "tuna"; menu.removeItem("Tuna"); "1" → "That item is no longer on the menu."; nothing saved
@Test void unrecognisedTextWhenClosedOffersNothing()  // window closed; "tuna pls" → "Ordering is closed for today."; then open; "1" → no-open-question reply
@Test void emptyMenuSaysSo()                          // menu.clear(); "tuna pls" → "There is nothing on the menu yet."
@Test void fewerOptionsChangeTheFooter()              // menu.removeItem("Chicken curry"); "tuna" → ends with "Reply with @SandwichBot 1 or 2."; "3" → "Please answer 1 or 2."
```

- [ ] **Step 2: Run** `./mvnw -q -pl orders -am test -Dtest=OrderHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`. Expected: FAIL (compile errors on the new constructor/fake methods, or assertion failures).

- [ ] **Step 3: Implement.** In `handle`, in this order:
  1. If the trimmed text matches `^#?\s*(\d)$`: `choices.find(user)` empty → no-open-question reply. Number outside `1..options.size()` → the "Please answer …" reply, options kept. Otherwise `choices.clear(user)`; closed → `CLOSED`; `menu.find(option.foodType(), option.name())` empty → `That item is no longer on the menu.`; else save through the existing `order(...)` path with `Interpretation.order(option.foodType(), option.name(), openChoice.notes())`.
  2. `parser.parse(text)` present → `choices.clear(user)` and the existing switch.
  3. Otherwise: `choices.clear(user)`; closed → `CLOSED`; full menu = `menu.foodTypes()` flat-mapped through `menu.itemsFor(type)`; empty → `There is nothing on the menu yet.`; else `suggester.suggest(textBeforeFirstComma, fullMenu)`, `choices.offer(user, options, textAfterFirstComma.trim())`, reply per Global Constraints.
  Remove `interpret(...)`, the `OrderInterpreter` field, `AI_FALLBACK` (keep `case UNCLEAR -> meaning.reply()` until Task 4). Help text: replace the AI line with `Anything else: I suggest the three closest menu items and you answer with 1, 2 or 3.`

- [ ] **Step 4: Run** `./mvnw -q -pl orders -am test`. Expected: PASS (whole `orders` module).

- [ ] **Step 5: Run** `./mvnw -q test` (whole project — `admin` still implements `OrderInterpreter`, `app` still wires it). Expected: PASS.

- [ ] **Step 6: Commit** `git add -A orders && git commit -m "feat(orders): offer numbered menu suggestions instead of the AI"`

### Task 4: Remove Claude everywhere

**Files:**
- Delete: `admin/src/main/java/com/bugfarm/lunch/admin/ai/` (all 6 files), `admin/src/test/java/com/bugfarm/lunch/admin/ai/` (all 3 files), `contracts/src/main/java/com/bugfarm/lunch/contracts/OrderInterpreter.java`
- Modify: `admin/pom.xml` (remove the `com.anthropic:anthropic-java` dependency) · `app/src/main/resources/application.yml` (remove the `lunch.ai` block) · `compose.yaml` (remove `ANTHROPIC_API_KEY`) · `contracts/.../Intent.java` (remove `UNCLEAR`) · `contracts/.../Interpretation.java` (remove `reply` and `unclear(...)`) · `contracts/src/test/.../InterpretationTest.java` (drop `unclearCarriesReplyAndBlankItem` and the `reply()` assertions) · `orders/.../OrderHandler.java` (drop the `UNCLEAR` branch) · `app/src/test/.../WiringTest.java` (drop the `OrderInterpreter` field, its imports and the `ClaudeOrderInterpreter` assertion; keep the other three) · `README.md` · `docs/superpowers/specs/2026-11-20-food-ordering-design.md` (one line under the AI section: "Replaced on 2026-11-20 by numbered menu suggestions; see `2026-11-20-menu-suggestions-design.md`.")
- Search also: any other reference `git grep -n -i -E "anthropic|OrderInterpreter|UNCLEAR|ANTHROPIC_API_KEY|claude-haiku"` outside `docs/`.

**Interfaces:**
- Consumes: Task 3's `OrderHandler` (no `OrderInterpreter`).
- Produces: `record Interpretation(Intent intent, String foodType, String item, String notes)` with `of(Intent)` and `order(String, String, String)`; `enum Intent { ORDER, CANCEL, MY_ORDER, MENU, HELP }`.

- [ ] **Step 1: Update the tests first:** `InterpretationTest` and `WiringTest` as listed. **Run** `./mvnw -q test`. Expected: FAIL (tests reference the 4-field record / removed assertion no longer compiles against the old contract, or vice versa).

- [ ] **Step 2: Delete and edit** the main files listed above.

- [ ] **Step 3: README.md:**
  - "What you need": drop the Anthropic API key.
  - Settings table: drop the `ANTHROPIC_API_KEY` row.
  - Build and test: drop the live Claude test lines.
  - Run locally: drop `$env:ANTHROPIC_API_KEY`.
  - Connect Teams step 6: replace the free-text example with `@SandwichBot brie, no butter` then `@SandwichBot 1`.
  - "Using the bot" table: the `anything else` row becomes "the three closest menu items, numbered; answer with `1`, `2` or `3` within 15 minutes"; add a row for `1` / `2` / `3`. Replace the paragraph about sending free text to Anthropic with: "Messages stay in the app; nothing is sent to an outside service."

- [ ] **Step 4: Run** `./mvnw -q verify` (full build including the frontend bundle). Expected: PASS. **Run** `git grep -n -i -E "anthropic|OrderInterpreter|UNCLEAR|ANTHROPIC_API_KEY|claude-haiku" -- ':!docs'`. Expected: no output.

- [ ] **Step 5: Commit** `git add -A && git commit -m "refactor: remove the Claude fallback and the Anthropic dependency"`
