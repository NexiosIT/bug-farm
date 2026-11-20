# Default sandwiches on the menu

Short design (small change). Approved 2026-11-20.

## Intent

A fresh install has the food type "Sandwich" but no items, so Teams can't take a single
order until an admin adds items by hand. Ship five sandwiches out of the box so the app
is usable straight away.

## Goals

- A new database has five items under "Sandwich": Ham & cheese, Cheese, Tuna,
  Chicken curry, Martino.
- They are ordinary menu items: admins can rename or remove them on the Menu screen.

## Non-goals

- Extra menu data (prices, descriptions, ...). Raised earlier, set aside for now.
- Defaults for other food types.

## Anti-goals

- Bringing back defaults an admin deleted.
- Failing a migration on a database where some of these items already exist.

## Constraints

- The menu tables and their migrations belong to the admin module (Thijs's part).
- `V200__menu.sql` is already applied in existing databases, so it must not be edited.

## Approaches

1. **New Flyway migration (picked).** `V202__default_sandwiches.sql` in
   `admin/src/main/resources/db/migration/admin/`. It runs once per database, so
   deleted defaults stay deleted.
2. **Java seeding at startup (ruled out).** Runs on every start, so it would bring back
   items an admin removed unless extra tracking were added.

## Design

- The migration inserts the five items under the food type named "Sandwich", looked up
  by name. It inserts nothing when that type no longer exists (deleted or renamed).
- Items that already exist are skipped (`ON CONFLICT DO NOTHING` on the
  `(food_type_id, name)` unique key), so the migration never fails on existing data.
- Tests:
  - `JdbcMenuCatalogTest` and `PostgresMigrationTest` currently add "Ham & cheese"
    themselves, which would now be a duplicate. Update them to use the seeded item.
  - Add a test that a freshly migrated database lists the five sandwiches.

## Left to the builder

- The exact SQL, and which existing test class gets the new "five sandwiches" test.
- The case-insensitive duplicate check in `MenuRepository` is code-only. Exact-case
  conflicts are enough for the migration, because the database is fresh or curated by hand.
