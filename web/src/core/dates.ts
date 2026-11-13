// Civil dates as 'YYYY-MM-DD' strings. No time zones: a renewal is a day on the
// calendar, not an instant. Android's core/Dates.kt mirrors this file line for line.

export type Ymd = string;

export interface Civil { y: number; m: number; d: number } // m is 1..12

export function parse(s: Ymd): Civil {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (!m) throw new Error(`Bad date: ${s}`);
  return { y: +m[1], m: +m[2], d: +m[3] };
}

export function format({ y, m, d }: Civil): Ymd {
  return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

export function isLeap(y: number): boolean {
  return (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
}

export function daysInMonth(y: number, m: number): number {
  return [31, isLeap(y) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m - 1];
}

// Days since 1970-01-01 (Howard Hinnant's days_from_civil).
export function toEpochDay(s: Ymd): number {
  let { y, m, d } = parse(s);
  y -= m <= 2 ? 1 : 0;
  const era = Math.floor(y / 400);
  const yoe = y - era * 400;
  const doy = Math.floor((153 * (m + (m > 2 ? -3 : 9)) + 2) / 5) + d - 1;
  const doe = yoe * 365 + Math.floor(yoe / 4) - Math.floor(yoe / 100) + doy;
  return era * 146097 + doe - 719468;
}

export function fromEpochDay(z: number): Ymd {
  z += 719468;
  const era = Math.floor(z / 146097);
  const doe = z - era * 146097;
  const yoe = Math.floor((doe - Math.floor(doe / 1460) + Math.floor(doe / 36524) - Math.floor(doe / 146096)) / 365);
  const doy = doe - (365 * yoe + Math.floor(yoe / 4) - Math.floor(yoe / 100));
  const mp = Math.floor((5 * doy + 2) / 153);
  const d = doy - Math.floor((153 * mp + 2) / 5) + 1;
  const m = mp + (mp < 10 ? 3 : -9);
  return format({ y: yoe + era * 400 + (m <= 2 ? 1 : 0), m, d });
}

export function addDays(s: Ymd, n: number): Ymd {
  return fromEpochDay(toEpochDay(s) + n);
}

export function daysBetween(a: Ymd, b: Ymd): number {
  return toEpochDay(b) - toEpochDay(a);
}

// Adds months keeping the anchor day, clamped to the month's length:
// Jan 31 + 1 month = Feb 28 (or 29), + 2 months = Mar 31.
export function addMonths(anchor: Ymd, months: number): Ymd {
  const { y, m, d } = parse(anchor);
  const total = y * 12 + (m - 1) + months;
  const ny = Math.floor(total / 12);
  const nm = total - ny * 12 + 1;
  return format({ y: ny, m: nm, d: Math.min(d, daysInMonth(ny, nm)) });
}

export function today(): Ymd {
  const t = new Date();
  return format({ y: t.getFullYear(), m: t.getMonth() + 1, d: t.getDate() });
}

export function monthStart(y: number, m: number): Ymd {
  return format({ y, m, d: 1 });
}

export function monthEnd(y: number, m: number): Ymd {
  return format({ y, m, d: daysInMonth(y, m) });
}
