# Data model

One JSON document, identical on web and Android (`web/src/core/model.ts`, `android/.../core/Model.kt`).

```
Store { schema: 1, currency: "BWP", subscriptions: Subscription[], notified: string[] }
```

`Subscription`: `id` (uuid), `name`, `provider?`, `category`, `kind` (subscription | contract | trial | bill),
`amount`, `currency`, `cycleUnit` (day | week | month | year), `cycleEvery`, `anchorDate` (YYYY-MM-DD, any
real charge date), `endDate?`, `autoRenews`, `renewalCost?` (one-off on `endDate`), `remindDays[]`,
`remindCharges`, `status` (active | paused | cancelled), `cancelledOn?`, `paymentMethod?`, `url?`, `notes?`,
`createdAt`, `updatedAt`, `deletedAt?` (soft delete; epoch ms).

Derived, never stored: next charge, charges in a range, monthly equivalent, events
(`charge`, `trial-end`, `contract-end`) and due reminders.

Reminder key: `id|type|date|bucket`. `notified` holds keys already shown; keys for past dates are pruned.

Import merges by `id`, newer `updatedAt` wins.

## SmartSpend fit

Each subscription maps 1:1 to a Firestore doc at `users/{uid}/subscriptions/{id}` with the same fields.
Charges export as SmartSpend `transactions` documents (`web/src/core/export.ts`): one per event, type
`EXPENSE`, item category `Subscriptions`, plus `source: "recur"`, `subscriptionId` and
`eventKey` (`id|type|date`) so a later sync can skip charges it already logged.
