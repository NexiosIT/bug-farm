# Default Sandwiches Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A freshly migrated database has five items under "Sandwich", so Teams can take orders straight away.

**Architecture:** One new Flyway migration in the admin module inserts the items under the food type named "Sandwich". Existing tests that added "Ham & cheese" themselves now use the seeded item instead.

**Tech Stack:** Flyway SQL migrations (run on H2 in admin tests and the local jar, and on PostgreSQL in Docker), Spring `JdbcClient`, JUnit 5 + AssertJ, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-11-20-default-sandwiches-design.md`

## Global Constraints

- The five items, exact spelling: `Ham & cheese`, `Cheese`, `Tuna`, `Chicken curry`, `Martino`.
- Never edit `V200__menu.sql` or `V201__settings.sql`. They are already applied in existing databases.
- The migration SQL must run on **both H2 (default mode) and PostgreSQL**. `ON CONFLICT` is PostgreSQL-only, so use `INSERT ... SELECT ... WHERE NOT EXISTS`.
- Work on a new branch `default-sandwiches` made from `develop`.
- `develop` has about 126 files that differ only in line endings. **Stage only the files this plan names.** Never use `git add -A` or `git add .`.
- Backend tests: `./mvnw -q test`.

## Review Focus

1. **"Ham & cheese" already on the menu** (an admin added it by hand before upgrading): the migration must succeed and must not create a duplicate. Pinned by `skipsItemsThatAlreadyExist` in Task 1.
2. **"Sandwich" deleted or renamed** before the upgrade: the migration must add nothing and must not fail. Pinned by `addsNothingWhenSandwichIsGone` in Task 1.
3. **The PostgreSQL migration test pins the schema version** at `"201"`: it must become `"202"`, or the Docker-backed test fails. Covered in Task 1, Step 5.
4. **Existing tests that add "Ham & cheese" themselves** would now hit `DuplicateNameException`: `JdbcMenuCatalogTest` and `PostgresMigrationTest`. Covered in Task 1, Steps 1 and 5.
5. **Sort order in Teams `menu` replies** follows `ORDER BY LOWER(name)`: the seeded items come back as Cheese, Chicken curry, Ham & cheese, Martino, Tuna. Pinned by `freshDatabaseHasFiveDefaultSandwiches` in Task 1.

---

### Task 1: Seed five default sandwiches

**Files:**
- Create: `admin/src/main/resources/db/migration/admin/V202__default_sandwiches.sql`
- Create: `admin/src/test/java/com/bugfarm/lunch/admin/menu/DefaultSandwichesMigrationTest.java`
- Modify: `admin/src/test/java/com/bugfarm/lunch/admin/menu/JdbcMenuCatalogTest.java` (the `@BeforeEach`, plus one new test)
- Modify: `app/src/test/java/com/bugfarm/lunch/app/PostgresMigrationTest.java:48` (version) and `:56` (the `addItem` line)
- Commit as well: `docs/superpowers/specs/2026-11-20-default-sandwiches-design.md` and this plan

**Interfaces:**
- Consumes: the existing `MenuRepository.all()`, `MenuRepository.addItem(long, String)`, and `JdbcMenuCatalog.itemsFor(String)` / `find(String, String)` / `foodTypes()`. None of them change.
- Produces: schema version `202`. No new Java API.

- [ ] **Step 0: Branch**

```bash
git switch -c default-sandwiches develop
```

- [ ] **Step 1: Change `JdbcMenuCatalogTest` to rely on the seed, and add the fresh-database test**

Replace the `@BeforeEach addHamAndCheese()` with a `@BeforeEach findSandwich()` that only looks up `sandwichId` (no `addItem`). `findIsCaseInsensitiveAndTrimmed` and `duplicateItemNameRejectedIgnoringCase` then run against the seeded "Ham & cheese". Add:

```java
@Test
void freshDatabaseHasFiveDefaultSandwiches() {
    assertThat(catalog.foodTypes()).containsExactly("Sandwich");
    assertThat(catalog.itemsFor("Sandwich")).extracting(MenuItem::name)
            .containsExactly("Cheese", "Chicken curry", "Ham & cheese", "Martino", "Tuna");
}
```

- [ ] **Step 2: Write `DefaultSandwichesMigrationTest` (the upgrade cases)**

A plain JUnit test, not a Spring test. Each test uses its own in-memory H2 database (`jdbc:h2:mem:<unique name>;DB_CLOSE_DELAY=-1`), migrates with Flyway (`classpath:db/migration/admin`) to `target("201")`, changes the data with `JdbcClient`, then runs `migrate()` again without a target.

```java
@Test
void skipsItemsThatAlreadyExist() {
    // at 201: insert menu_item 'Ham & cheese' under Sandwich
    // after full migrate:
    assertThat(names("Sandwich"))
            .containsExactlyInAnyOrder("Cheese", "Chicken curry", "Ham & cheese", "Martino", "Tuna");
}

@Test
void addsNothingWhenSandwichIsGone() {
    // at 201: UPDATE food_type SET name = 'Broodjes' WHERE name = 'Sandwich'
    // after full migrate (must not throw):
    assertThat(count("SELECT COUNT(*) FROM menu_item")).isZero();
}
```

`names(type)` and `count(sql)` are private helpers that query with `JdbcClient`. Use `org.h2.jdbcx.JdbcDataSource` as the data source. `flyway-core` and `h2` are already test dependencies of `admin`.

- [ ] **Step 3: Run the admin tests and check that they fail**

Run: `./mvnw -q test -pl admin -am -Dtest='JdbcMenuCatalogTest,DefaultSandwichesMigrationTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. `freshDatabaseHasFiveDefaultSandwiches`, `findIsCaseInsensitiveAndTrimmed` and both migration tests fail because there are no items yet.

- [ ] **Step 4: Write `V202__default_sandwiches.sql`**

Insert the five names under the food type with `name = 'Sandwich'`, skipping any name that type already has. Use a derived table of `SELECT '<name>' AS name ... UNION ALL ...` joined to `food_type`, with `WHERE NOT EXISTS (SELECT 1 FROM menu_item m WHERE m.food_type_id = t.id AND m.name = v.name)`. When no `Sandwich` row exists, this inserts nothing.

- [ ] **Step 5: Update `PostgresMigrationTest`**

- `allMigrationsApplyOnPostgres`: expected version `"201"` becomes `"202"`.
- `menuAndSettingsWorkOnPostgres`: delete the `menuRepository.addItem(sandwichId, "Ham & cheese");` line, and the `sandwichId` lookup if it's then unused. The `find(" SANDWICH ", "ham & CHEESE")` assertion now checks the seeded item.

- [ ] **Step 6: Run the full backend suite and check that it passes**

Run: `./mvnw -q test`
Expected: BUILD SUCCESS with no failures. `PostgresMigrationTest` runs only when Docker is up. Check that it ran and didn't skip: Docker Desktop is running on this machine.

- [ ] **Step 7: Commit**

```bash
git add admin/src/main/resources/db/migration/admin/V202__default_sandwiches.sql \
        admin/src/test/java/com/bugfarm/lunch/admin/menu/DefaultSandwichesMigrationTest.java \
        admin/src/test/java/com/bugfarm/lunch/admin/menu/JdbcMenuCatalogTest.java \
        app/src/test/java/com/bugfarm/lunch/app/PostgresMigrationTest.java \
        docs/superpowers/specs/2026-11-20-default-sandwiches-design.md \
        docs/superpowers/plans/2026-11-20-default-sandwiches.md
git commit -m "feat(admin): seed five default sandwiches on the menu"
```

Check with `git show --stat HEAD` that exactly these six files are in the commit.
