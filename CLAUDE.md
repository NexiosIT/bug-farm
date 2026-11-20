# bug-farm: lunch ordering via Teams

Three developers build this app, each in their own part. Design:
`docs/superpowers/specs/2026-11-20-food-ordering-design.md`. Build plan:
`docs/superpowers/plans/2026-11-20-lunch-ordering.md`.

## Rule 1: know who is working before changing anything

A session starts with the developer saying who they are, e.g.
`I'm Robin, working on my part.` For the shared tasks it starts with
`We're all three working together on Task <n>.`

- If the session hasn't said who is working, ask first. Reading and
  explaining code is fine until then. Creating, editing, deleting or moving
  files, and running anything that changes files, is not.
- Only these names count: Bert, Thijs, Robin. Anyone else → ask who they are
  working for, and change nothing until it is one of the three.
- The answer holds for the whole session. If someone says mid-session that
  they are now someone else, confirm the switch before acting on it.

## Rule 2: each developer changes only their own folders

| Developer | Part | May change | Plan tasks |
| --- | --- | --- | --- |
| Bert | Teams and orders | `orders/` | 3, 4, 5, 6 |
| Thijs | Admin backend, draft email, AI | `admin/`, `app/` | 7, 8, 9, 10, 14 |
| Robin | React admin screen | `frontend/` | 11, 12, 13 |

**Shared files** belong to all three:
- `contracts/`
- the root `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`
- `.gitignore`
- `README.md`
- `CLAUDE.md`
- `docs/`

Change them only in a session that started with
`We're all three working together…`.

- Stay inside the working developer's folders for every change. That
  includes edits, new files, deletes and generated files, made directly or
  through shell commands, scripts, formatters or code generators.
- Reading any folder is always allowed. So is building and running the
  tests of the whole project.
- Only work on the plan tasks that belong to the working developer.

## Rule 3: when a change is needed in someone else's part, stop

If the task needs a change outside the working developer's folders, or in
a shared file outside a joint session:

1. Don't make the change, and don't work around it. That means no copying
   the other part's code into your own folder, no duplicate interfaces, no
   changing or disabling someone else's tests, and no editing through the
   shell instead.
2. Tell the developer plainly:
   - which file or folder needs to change;
   - whose part it is;
   - what the change is and why it's needed.
3. Ask them to raise it with that person (or all three, for shared files)
   in the **Lunch** team in Teams, and offer a short message they can
   paste. For example:
   > Hi Thijs, for Task 6 I need `MenuCatalog.itemsFor` to also return
   > item ids. Can you add that to `contracts/` (or shall we do it
   > together)?
4. Carry on with whatever in the task doesn't depend on that change, or
   stop there if nothing does.

## Working conventions

- Each developer works on their own branch: `bert-orders`, `thijs-admin`,
  `robin-frontend`. Merging to `main` happens together (Task 14).
- Tests come first, as the plan describes. Backend: `./mvnw -q test`.
  Frontend: `npm --prefix frontend test -- --run`.
- Secrets come only from environment variables (see the plan's Global
  Constraints). Never commit them.
