# Recur handoff

Recur tracks everything that renews: subscriptions, prepaid data bundles, free trials, yearly bills and
anything with an end date. It reminds before each charge or end and folds one-off renewal costs into the
month they land. It is the subscriptions module of SmartSpend.

## State (2026-11-13)

- **Web** (`web/`): React + Vite, localStorage key `recur.store.v1`. Built for `/projects/recur/`.
  19 Vitest tests against `fixtures/parity.json`, all passing. Not yet on mehdiacho.tech.
- **Android** (`android/`): Compose, `app.greenforrest.recur` (`.debug` suffix in debug). Data in
  `filesDir/recur.json`, same JSON as the web export. WorkManager job every 6 h posts due reminders;
  opening the app also posts them. 7 JUnit parity tests pass. Debug build installed and launched on the
  S10 (API 31) on 2026-11-13.
- **Repo**: local commits only. GitHub repo `mehdiacho/recur` (private) not created yet: waiting for
  Mehdi's written OK in the project thread.

## Decisions

- Botswana has no real phone contracts: people buy airtime and data bundles. Phone presets are therefore
  plain recurring items, not `contract`. The `contract` kind stays for leases and fixed-term plans.
- Next charge is derived from `anchorDate`, never stored. Months clamp to month end.
- "Total" is cash actually leaving that month; "average" is the smoothed monthly equivalent.
- Presets carry no prices (Mehdi's Subflow feedback, 2026-09-04).
- Subflow was studied for reference only (`docs/subflow-teardown.md`); no code copied.

## Build

- Web: `cd web && npm i && npm test && npm run build`
- Android (Git Bash): `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot" ./gradlew.bat --console=plain :app:testDebugUnitTest :app:assembleDebug`

## Next

1. Create the private repo and push (needs Mehdi's OK).
2. Ship the web build via the portfolio: `node scripts/add-project.mjs recur X:\Projects\recur\web\dist --repo mehdiacho/recur --build "npm run build"` in `X:\Projects\portfolio`, then a PR.
3. Optional: a "bundle" flow with a short expiry (e.g. 30-day data bundle, remind 1 day before).
4. SmartSpend sync: Firestore project `smartspend-mehdiacho`; see `docs/data-model.md`.
