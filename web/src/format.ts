import { daysBetween, parse, type Ymd } from './core/dates';
import type { CycleUnit } from './core/model';

export function money(n: number, currency: string): string {
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency, maximumFractionDigits: 2 }).format(n);
  } catch {
    return `${currency} ${n.toFixed(2)}`;
  }
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const LONG = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

export function shortDate(d: Ymd, today?: Ymd): string {
  const { y, m, d: day } = parse(d);
  const sameYear = today && parse(today).y === y;
  return `${day} ${MONTHS[m - 1]}${sameYear ? '' : ` ${y}`}`;
}

export function monthName(m: number): string {
  return LONG[m - 1];
}

export function relative(d: Ymd, today: Ymd): string {
  const n = daysBetween(today, d);
  if (n === 0) return 'today';
  if (n === 1) return 'tomorrow';
  if (n === -1) return 'yesterday';
  if (n < 0) return `${-n} days ago`;
  if (n < 14) return `in ${n} days`;
  if (n < 60) return `in ${Math.round(n / 7)} weeks`;
  return `in ${Math.round(n / 30.4)} months`;
}

export function cycleLabel(unit: CycleUnit, every: number): string {
  if (every === 1) return { day: 'daily', week: 'weekly', month: 'monthly', year: 'yearly' }[unit];
  if (unit === 'week' && every === 2) return 'every 2 weeks';
  return `every ${every} ${unit}s`;
}

export function perLabel(unit: CycleUnit, every: number): string {
  return every === 1 ? `/${{ day: 'day', week: 'wk', month: 'mo', year: 'yr' }[unit]}` : ` ${cycleLabel(unit, every)}`;
}
