// Outward formats: SmartSpend transactions and iCalendar reminders.
import { addDays, type Ymd } from './dates';
import type { Subscription } from './model';
import { eventsBetween, type MoneyEvent } from './schedule';

/**
 * A charge as a SmartSpend `transactions` document (see smartspend/src/models/TransactionModel.js).
 * `source` and `subscriptionId` are extra fields SmartSpend ignores today; they let a later
 * sync avoid logging the same charge twice.
 */
export function toSmartSpendTransaction(s: Subscription, e: MoneyEvent, userId: string) {
  const title = e.type === 'contract-end' ? `${s.name} renewal` : s.name;
  const [y, m, d] = e.date.split('-').map(Number);
  const local = new Date(y, m - 1, d, 12, 0, 0);
  return {
    userId,
    title,
    amount: e.amount,
    date: local.toISOString(),
    type: 'EXPENSE',
    items: [{
      id: `${s.id}-${e.date}-${e.type}`,
      name: title,
      price: e.amount,
      quantity: 1,
      shop: s.provider ?? '',
      brand: '',
      category: 'Subscriptions',
      date: local.toISOString(),
    }],
    isSettled: false,
    debtorId: null,
    dateTimestamp: local.getTime(),
    source: 'recur',
    subscriptionId: s.id,
    eventKey: `${s.id}|${e.type}|${e.date}`,
  };
}

function icsDate(d: Ymd): string {
  return d.replaceAll('-', '');
}

function esc(t: string): string {
  return t.replace(/[\\;,]/g, (c) => `\\${c}`).replace(/\n/g, '\\n');
}

/** An .ics file with every event in the next `days` days, each with alarms at the reminder leads. */
export function toIcs(subs: Subscription[], from: Ymd, days = 365, currency = 'BWP'): string {
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//mehdiacho//Recur//EN', 'CALSCALE:GREGORIAN'];
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+/, '');
  for (const e of eventsBetween(subs, from, addDays(from, days))) {
    const s = subs.find((x) => x.id === e.subId)!;
    if (e.type === 'charge' && !s.remindCharges) continue;
    const label = e.type === 'contract-end' ? `${s.name}: contract ends`
      : e.type === 'trial-end' ? `${s.name}: trial ends, first charge` : `${s.name} renews`;
    lines.push('BEGIN:VEVENT', `UID:${s.id}-${e.type}-${e.date}@recur`, `DTSTAMP:${stamp}`,
      `DTSTART;VALUE=DATE:${icsDate(e.date)}`, `DTEND;VALUE=DATE:${icsDate(addDays(e.date, 1))}`,
      `SUMMARY:${esc(label)}`, `DESCRIPTION:${esc(`${currency} ${e.amount.toFixed(2)}`)}`);
    for (const lead of s.remindDays.filter((l) => l > 0)) {
      lines.push('BEGIN:VALARM', 'ACTION:DISPLAY', `DESCRIPTION:${esc(label)}`, `TRIGGER:-P${lead}D`, 'END:VALARM');
    }
    lines.push('END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  return lines.join('\r\n') + '\r\n';
}
