# Recur

Subscription, contract and free-trial tracker with renewal and expiry reminders. It is the subscriptions module of SmartSpend (`X:\Projects\smartspend`). Read `docs/HANDOFF.md` first: it covers the current state, decisions and next steps.

## Layout

- `fixtures/parity.json`: hand-worked expectations that **both** cores must pass. If you change the schedule logic, change it in both and update the fixture by hand. Never regenerate it from code.
- `web/`: Vite + React + TypeScript. Core logic is in `web/src/core/` and is pure, with no React.
  - `npm test` runs vitest against the fixture.
  - `npm run build` uses base path `/projects/recur/`.
- `android/`: Kotlin + Compose. `app.greenforrest.recur`. Core logic is a port of `web/src/core`, and JUnit reads the same fixture.
- `docs/`
  - `data-model.md`: the schema, and how it fits SmartSpend
  - `subflow-teardown.md`: reference notes on the app this replaces
  - `HANDOFF.md`

## Rules

- Dates are civil `YYYY-MM-DD` strings. Never use `Date` or `ZonedDateTime` arithmetic for schedule maths. Use `core/dates` (`Dates.kt` on Android).
- The next charge is always derived from `anchorDate`. Never store it.
- Store the schema in the same shape on web and Android, so the JSON export from one imports into the other. Bump `SCHEMA_VERSION` and handle the change in `normaliseStore` (and its Kotlin twin) whenever a field changes.
- No AI attribution in commits or PRs. Commit as `mehdiacho <mehdiacho@gmail.com>`.
- Website ships through the portfolio repo:

  ```
  node scripts/add-project.mjs recur X:\Projects\recur\web\dist --repo mehdiacho/recur --build "npm run build"
  ```

  Run it from `X:\Projects\portfolio`, then open a PR there. Mehdi merges it.
- Android dev device is the S10 (wireless adb, Android 12 / API 31). Don't touch its photos. The Redmi is Mehdi's daily phone, so leave it alone.
