// The Recur data model. Shape and field names are the contract with SmartSpend
// (docs/data-model.md) and with Android's core/Model.kt. Change all three together.
import type { Ymd } from './dates';

export const SCHEMA_VERSION = 1;

export type Kind = 'subscription' | 'contract' | 'trial' | 'bill';
export type CycleUnit = 'day' | 'week' | 'month' | 'year';
export type Status = 'active' | 'paused' | 'cancelled';

export const CATEGORIES = [
  'streaming', 'music', 'phone', 'internet', 'software', 'cloud', 'gaming',
  'news', 'fitness', 'insurance', 'utilities', 'education', 'other',
] as const;
export type Category = (typeof CATEGORIES)[number];

export interface Subscription {
  id: string;
  name: string;
  provider?: string | null;
  category: Category;
  kind: Kind;
  /** Charged each cycle, in major units (pula, not thebe), like SmartSpend `amount`. */
  amount: number;
  currency: string;
  cycleUnit: CycleUnit;
  cycleEvery: number;
  /** First paid charge. Every charge falls on anchor + n cycles (month ends clamp).
   *  For a trial this is the day the trial ends and the first charge lands. */
  anchorDate: Ymd;
  /** Contract or commitment end. No charge on or after it unless autoRenews. */
  endDate?: Ymd | null;
  /** After endDate: keep charging at the same price (true) or stop (false). */
  autoRenews: boolean;
  /** One-off cost that lands on endDate: upgrade fee, annual renewal fee. Counted in that month. */
  renewalCost?: number | null;
  /** Days before an event to remind. 0 (the day itself) is always added. */
  remindDays: number[];
  /** Remind before every charge. Off by default for contracts, where only the end matters. */
  remindCharges: boolean;
  status: Status;
  /** Charges stop from this day. Set when status becomes cancelled. */
  cancelledOn?: Ymd | null;
  paymentMethod?: string | null;
  url?: string | null;
  notes?: string | null;
  createdAt: number;
  updatedAt: number;
  /** Soft delete, per the SmartSpend roadmap. Hidden everywhere, kept for undo and sync. */
  deletedAt?: number | null;
}

export interface Store {
  schema: number;
  currency: string;
  subscriptions: Subscription[];
  /** Reminder keys already shown, so a reminder fires once. Pruned of past dates. */
  notified: string[];
}

export function defaultsFor(kind: Kind): Pick<Subscription, 'remindDays' | 'remindCharges' | 'autoRenews'> {
  switch (kind) {
    case 'contract': return { remindDays: [60, 30, 7, 1], remindCharges: false, autoRenews: true };
    case 'trial': return { remindDays: [3, 1], remindCharges: false, autoRenews: true };
    case 'bill': return { remindDays: [3], remindCharges: true, autoRenews: true };
    default: return { remindDays: [3], remindCharges: true, autoRenews: true };
  }
}

export function newId(): string {
  return (globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`);
}

export function emptyStore(currency = 'BWP'): Store {
  return { schema: SCHEMA_VERSION, currency, subscriptions: [], notified: [] };
}

/** Reads any stored or imported JSON into a valid Store, filling defaults. */
export function normaliseStore(raw: unknown): Store {
  const r = (raw ?? {}) as Partial<Store>;
  const currency = typeof r.currency === 'string' ? r.currency : 'BWP';
  const subs = Array.isArray(r.subscriptions) ? r.subscriptions : [];
  return {
    schema: SCHEMA_VERSION,
    currency,
    subscriptions: subs.map((s) => normaliseSubscription(s, currency)),
    notified: Array.isArray(r.notified) ? r.notified.filter((k) => typeof k === 'string') : [],
  };
}

export function normaliseSubscription(raw: Partial<Subscription>, currency: string): Subscription {
  const kind: Kind = (['subscription', 'contract', 'trial', 'bill'] as const).includes(raw.kind as Kind)
    ? (raw.kind as Kind) : 'subscription';
  const d = defaultsFor(kind);
  const now = Date.now();
  return {
    id: raw.id || newId(),
    name: (raw.name ?? '').toString().trim() || 'Untitled',
    provider: raw.provider ?? null,
    category: (CATEGORIES as readonly string[]).includes(raw.category as string) ? (raw.category as Category) : 'other',
    kind,
    amount: Number.isFinite(+raw.amount!) ? +raw.amount! : 0,
    currency: raw.currency || currency,
    cycleUnit: (['day', 'week', 'month', 'year'] as const).includes(raw.cycleUnit as CycleUnit) ? (raw.cycleUnit as CycleUnit) : 'month',
    cycleEvery: Math.max(1, Math.floor(+raw.cycleEvery! || 1)),
    anchorDate: raw.anchorDate || new Date().toISOString().slice(0, 10),
    endDate: raw.endDate || null,
    autoRenews: raw.autoRenews ?? d.autoRenews,
    renewalCost: raw.renewalCost == null || raw.renewalCost === ('' as unknown) ? null : +raw.renewalCost,
    remindDays: Array.isArray(raw.remindDays)
      ? [...new Set(raw.remindDays.map((n) => Math.max(0, Math.floor(+n))).filter(Number.isFinite))].sort((a, b) => b - a)
      : d.remindDays,
    remindCharges: raw.remindCharges ?? d.remindCharges,
    status: (['active', 'paused', 'cancelled'] as const).includes(raw.status as Status) ? (raw.status as Status) : 'active',
    cancelledOn: raw.cancelledOn || null,
    paymentMethod: raw.paymentMethod ?? null,
    url: raw.url ?? null,
    notes: raw.notes ?? null,
    createdAt: raw.createdAt ?? now,
    updatedAt: raw.updatedAt ?? now,
    deletedAt: raw.deletedAt ?? null,
  };
}
