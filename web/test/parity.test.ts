import { describe, expect, it } from 'vitest';
import fixture from '../../fixtures/parity.json';
import { normaliseSubscription } from '../src/core/model';
import { chargesBetween, dueReminders, monthlyEquivalent, monthSpend, nextCharge } from '../src/core/schedule';
import { addMonths, fromEpochDay, toEpochDay } from '../src/core/dates';
import { toIcs } from '../src/core/export';

const subs = fixture.subscriptions.map((s) => normaliseSubscription(s as never, 'BWP'));
const byId = (id: string) => subs.find((s) => s.id === id)!;

describe('dates', () => {
  it('round-trips epoch days', () => {
    for (const d of ['1970-01-01', '2000-02-29', '2024-02-29', '2026-11-13', '2100-03-01']) {
      expect(fromEpochDay(toEpochDay(d))).toBe(d);
    }
    expect(toEpochDay('1970-01-02')).toBe(1);
  });
  it('clamps month ends to the anchor day', () => {
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28');
    expect(addMonths('2026-01-31', 2)).toBe('2026-03-31');
    expect(addMonths('2026-12-15', 1)).toBe('2027-01-15');
    expect(addMonths('2026-03-15', -3)).toBe('2025-12-15');
  });
});

describe('parity fixture', () => {
  it.each(fixture.chargesBetween)('chargesBetween $id $from..$to', (c) => {
    expect(chargesBetween(byId(c.id), c.from, c.to)).toEqual(c.expect);
  });
  it.each(fixture.nextCharge)('nextCharge $id from $from', (c) => {
    expect(nextCharge(byId(c.id), c.from)).toBe(c.expect);
  });
  it.each(fixture.monthlyEquivalent)('monthlyEquivalent $id', (c) => {
    expect(monthlyEquivalent(byId(c.id))).toBeCloseTo(c.expect, 6);
  });
  it.each(fixture.monthSpend)('monthSpend $year-$month', (c) => {
    const r = monthSpend(subs, c.year, c.month);
    expect(r.total).toBe(c.total);
    expect(r.average).toBe(c.average);
  });
  it.each(fixture.dueReminders)('dueReminders $today', (c) => {
    expect(dueReminders(subs, c.today).map((r) => r.key).sort()).toEqual([...c.expectKeys].sort());
  });
});

describe('ics', () => {
  it('emits the contract end with alarms', () => {
    const ics = toIcs(subs, '2026-11-13', 60);
    expect(ics).toContain('SUMMARY:Phone contract: contract ends');
    expect(ics).toContain('TRIGGER:-P30D');
    expect(ics).not.toContain('Phone contract renews');
  });
});
