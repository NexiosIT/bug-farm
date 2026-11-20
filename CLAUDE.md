# bug-farm: lunch ordering via Teams

Three developers build this app, each in their own part. Design:
`docs/superpowers/specs/2026-11-20-food-ordering-design.md`. Build plan:
`docs/superpowers/plans/2026-11-20-lunch-ordering.md`.

## Working conventions

- Each developer works on their own branch, made from `develop`: `bert-orders`, `thijs-admin`,
  `robin-frontend`. Merging to `develop` happens together (Task 14).
- Tests come first, as the plan describes. Backend: `./mvnw -q test`.
  Frontend: `npm --prefix frontend test -- --run`.
- Secrets come only from environment variables (see the plan's Global
  Constraints). Never commit them.
