# Subflow teardown (reference only)

Static look at Subflow 1.7.0 (`org.dpdns.alwaysup.subflow`, versionCode 13), pulled from Mehdi's Redmi on 2026-11-13.
No Subflow code, strings or assets are copied into Recur. The APKs live outside this repo, in `X:\Projects\recur-subflow-ref\apk`. They are not committed anywhere.

It could not be run on the S10. Subflow has `minSdk 32` (Android 12L), the S10 runs Android 12 (API 31), and `adb install-multiple` fails with `INSTALL_FAILED_OLDER_SDK`. Everything below comes from the manifest (`aapt2 dump`) and from class names and SQL strings in `classes.dex`. Nothing here is from decompiled method bodies.

## Stack

- Kotlin, Jetpack Compose with Material 3, Room, DataStore, WorkManager, Retrofit.
- Firebase: Analytics and Installations only, with no Firestore.
- Google sign-in through Credential Manager, plus guest login.
- Play Billing for the Pro tier, AdMob ads, and a home-screen widget.
- Backend at `subflow.alwaysup.dpdns.org`, with endpoints `auth/google`, `auth/guest`, `sync/`, presets and purchase reports.
- Exchange rates come from `open.er-api.com` (`latest/`).

## Data model

There is one Room table, `subscriptions`:

| column | notes |
|---|---|
| id, name, category | text |
| amount, currency | REAL, text |
| cycle | `daily`, `weekly`, `monthly`, `quarterly`, `yearly`. There is no "every N" multiplier. |
| first_bill_date, next_bill_date | Text dates. `next_bill_date` is stored and advanced, not derived from `first_bill_date`. |
| reminder_days_before | One integer: a single reminder lead per subscription. |
| is_active, is_deleted, updated_at | Soft delete and last-write-wins sync. |
| color_hex, icon_url | Logos come from the server's preset list (`PresetDto`). |
| notes | text |
| is_trial, trial_end_date, trial_converts, post_trial_amount, post_trial_cycle | Trials were added in a later migration, with column defaults. |
| trial_outcome, trial_keep_end_date | `TrialOutcome`: what happened when the trial ended (converted or cancelled). |

Other domain classes:

- `BillingCycle`, `ProTier` (FREE / PRO / LIFETIME), `UserProfile`
- `MonthlyBudget` and `CalculationSnapshot`: a budget target against the monthly total
- `SubFlowBackupContainer`: JSON backup and restore through a FileProvider

## Reminders

- **Renewals.** `RenewalNotificationWorker` is a periodic WorkManager job.
- **Trial's last day.** `TrialFinalDayReceiver` fires on `TRIAL_FINAL_DAY` using an exact alarm (`SCHEDULE_EXACT_ALARM`). It is re-armed after reboot (`RECEIVE_BOOT_COMPLETED`).

## What Recur does differently, and why

1. **Contracts are first-class.** Subflow has no end date or commitment, so a phone contract can't be modelled. This was the main use case Mehdi asked for. Recur adds `endDate`, `autoRenews` and a one-off `renewalCost`, and puts that cost in the month it lands.
2. **Next charge is derived, never stored.** Subflow stores `next_bill_date` and advances it. That drifts if the worker misses a run or the user edits `first_bill_date`. Recur computes every charge from `anchorDate` with month-end clamping, so it can't go stale.
3. **Several reminder leads.** Subflow has one lead per subscription. Recur keeps a list, for example 60/30/7/1 days before a contract ends, and adds a reminder on the day itself.
4. **Two monthly figures.** Recur shows actual cash out this month (yearly renewals and contract fees included) next to the smoothed monthly average. Subflow shows one normalised total.
5. **"Every N" cycles.** Recur supports any interval, such as fortnightly or every 3 months, through `cycleEvery`. Subflow can only use its fixed list.
6. **No account, no ads, no server for v1.** Data stays local, and JSON export/import moves it between web and Android. Sync later goes to SmartSpend's own Firestore, not a third-party backend.
7. **Mehdi's tester feedback (2026-09-04) is designed in:**
   - quick picks
   - a staged add flow (pick, then details, then save)
   - a bottom-right add button
   - visible edit controls, with no hidden swipe-to-delete
   - delete with undo
   - batch edit
   - onboarding
   - a live preview with the right price

Worth borrowing as ideas, not code:

- the trial outcome record (did I keep it?)
- a monthly budget target
- a home-screen widget
- multi-currency conversion

These are listed as later work in `HANDOFF.md`.
