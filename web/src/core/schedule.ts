// Charges, monthly spend and reminders. Pure functions over civil dates.
// Android's core/Schedule.kt is a port of this file; fixtures/parity.json keeps them equal.
import { addDays, addMonths, daysBetween, monthEnd, monthStart, toEpochDay, type Ymd } from './dates';
import type { Subscription } from './model';

/** The n-th charge date (n >= 0), anchored so month ends clamp without drifting. */
export function nthCharge(s: Subscription, n: number): Ymd {
  const k = n * s.cycleEvery;
  switch (s.cycleUnit) {
    case 'day': return addDays(s.anchorDate, k);
    case 'week': return addDays(s.anchorDate, 7 * k);
    case 'month': return addMonths(s.anchorDate, k);
    case 'year': return addMonths(s.anchorDate, 12 * k);
  }
}

function approxCycleDays(s: Subscription): number {
  const per = { day: 1, week: 7, month: 30.436875, year: 365.2425 }[s.cycleUnit];
  return per * s.cycleEvery;
}

/** True if a charge on this date actually happens (not ended, not cancelled). */
export function chargeAllowed(s: Subscription, date: Ymd): boolean {
  if (s.deletedAt || s.status === 'paused') return false;
  if (s.cancelledOn && date >= s.cancelledOn) return false;
  if (s.status === 'cancelled' && !s.cancelledOn) return false;
  if (s.endDate && !s.autoRenews && date >= s.endDate) return false;
  return true;
}

/** Charge dates in [from, to], inclusive. */
export function chargesBetween(s: Subscription, from: Ymd, to: Ymd): Ymd[] {
  if (to < from || to < s.anchorDate) return [];
  let n = Math.max(0, Math.floor(daysBetween(s.anchorDate, from) / approxCycleDays(s)) - 2);
  while (n > 0 && nthCharge(s, n) >= from) n--;
  const out: Ymd[] = [];
  for (let guard = 0; guard < 2000; guard++, n++) {
    const d = nthCharge(s, n);
    if (d > to) break;
    if (d >= from && chargeAllowed(s, d)) out.push(d);
  }
  return out;
}

/** Next charge on or after `from`, within ten years, or null. */
export function nextCharge(s: Subscription, from: Ymd): Ymd | null {
  return chargesBetween(s, from, addDays(from, 3653))[0] ?? null;
}

export function isLive(s: Subscription): boolean {
  return !s.deletedAt && s.status === 'active';
}

/** Average cost per month, ignoring one-off renewal costs. */
export function monthlyEquivalent(s: Subscription): number {
  if (!isLive(s)) return 0;
  const perMonth = { day: 365.2425 / 12, week: 365.2425 / 7 / 12, month: 1, year: 1 / 12 }[s.cycleUnit];
  return (s.amount * perMonth) / s.cycleEvery;
}

export type EventType = 'charge' | 'trial-end' | 'contract-end';

export interface MoneyEvent {
  subId: string;
  name: string;
  type: EventType;
  date: Ymd;
  amount: number;
}

/** Every money event in [from, to]: charges, plus renewal costs landing on a contract end. */
export function eventsBetween(subs: Subscription[], from: Ymd, to: Ymd): MoneyEvent[] {
  const out: MoneyEvent[] = [];
  for (const s of subs) {
    if (s.deletedAt) continue;
    const charges = chargesBetween(s, from, to);
    for (const d of charges) {
      const type: EventType = s.kind === 'trial' && d === s.anchorDate ? 'trial-end' : 'charge';
      out.push({ subId: s.id, name: s.name, type, date: d, amount: s.amount });
    }
    if (s.endDate && s.endDate >= from && s.endDate <= to && s.status !== 'paused'
        && !(s.cancelledOn && s.endDate >= s.cancelledOn) && s.status !== 'cancelled') {
      out.push({ subId: s.id, name: s.name, type: 'contract-end', date: s.endDate, amount: s.renewalCost ?? 0 });
    }
  }
  return out.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.name.localeCompare(b.name)));
}

export interface MonthSpend {
  /** What actually leaves the account that month: charges plus renewal costs. */
  total: number;
  /** Sum of monthly equivalents: the smoothed figure. */
  average: number;
  events: MoneyEvent[];
}

export function monthSpend(subs: Subscription[], y: number, m: number): MonthSpend {
  const events = eventsBetween(subs, monthStart(y, m), monthEnd(y, m));
  return {
    total: round2(events.reduce((t, e) => t + e.amount, 0)),
    average: round2(subs.reduce((t, s) => t + monthlyEquivalent(s), 0)),
    events,
  };
}

export interface Reminder {
  key: string;
  subId: string;
  name: string;
  type: EventType;
  date: Ymd;
  daysUntil: number;
  amount: number;
}

/**
 * Reminders due today. A reminder belongs to the smallest lead (from remindDays plus 0)
 * that is >= days left, so a day the device skipped still gets its reminder late rather
 * than never. Callers show each key once.
 */
export function dueReminders(subs: Subscription[], today: Ymd): Reminder[] {
  const out: Reminder[] = [];
  for (const s of subs) {
    if (!isLive(s)) continue;
    const leads = [...new Set([0, ...s.remindDays])].sort((a, b) => a - b);
    const maxLead = leads[leads.length - 1];
    const candidates: MoneyEvent[] = [];
    const window = eventsBetween([s], today, addDays(today, maxLead));
    for (const e of window) {
      if (e.type === 'charge' && !s.remindCharges) continue;
      candidates.push(e);
    }
    for (const e of candidates) {
      const daysUntil = daysBetween(today, e.date);
      const bucket = leads.find((l) => l >= daysUntil);
      if (bucket === undefined) continue;
      out.push({
        key: `${s.id}|${e.type}|${e.date}|${bucket}`,
        subId: s.id, name: s.name, type: e.type, date: e.date, daysUntil, amount: e.amount,
      });
    }
  }
  return out.sort((a, b) => a.daysUntil - b.daysUntil);
}

/** Drops reminder keys for events already in the past. */
export function pruneNotified(keys: string[], today: Ymd): string[] {
  return keys.filter((k) => (k.split('|')[2] ?? '') >= today);
}

export function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

/** Days until the contract ends, or null. Negative once it has ended. */
export function daysToEnd(s: Subscription, today: Ymd): number | null {
  return s.endDate ? toEpochDay(s.endDate) - toEpochDay(today) : null;
}
