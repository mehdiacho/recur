// Quick picks for the add flow. No prices: local prices vary and a wrong default is worse
// than an empty field (Mehdi's Subflow feedback, 2026-09-04). Colours are for the
// monogram tile only; no brand logos are bundled.
import type { Category, CycleUnit, Kind } from './model';

export interface Preset {
  name: string;
  category: Category;
  kind?: Kind;
  cycleUnit?: CycleUnit;
  color: string;
  url?: string;
}

export const PRESETS: Preset[] = [
  { name: 'Netflix', category: 'streaming', color: '#e50914', url: 'https://www.netflix.com/account' },
  { name: 'Showmax', category: 'streaming', color: '#1a1aff' },
  { name: 'DStv', category: 'streaming', color: '#0072ce' },
  { name: 'Disney+', category: 'streaming', color: '#113ccf' },
  { name: 'Prime Video', category: 'streaming', color: '#00a8e1' },
  { name: 'YouTube Premium', category: 'streaming', color: '#ff0033' },
  { name: 'Spotify', category: 'music', color: '#1db954' },
  { name: 'Apple Music', category: 'music', color: '#fa2d48' },
  { name: 'Mascom', category: 'phone', kind: 'contract', color: '#e30613' },
  { name: 'Orange', category: 'phone', kind: 'contract', color: '#ff7900' },
  { name: 'BTC / beMobile', category: 'phone', kind: 'contract', color: '#00a0df' },
  { name: 'Home internet', category: 'internet', color: '#5b6cff' },
  { name: 'ChatGPT Plus', category: 'software', color: '#10a37f' },
  { name: 'Claude', category: 'software', color: '#d97757' },
  { name: 'Microsoft 365', category: 'software', color: '#d83b01', cycleUnit: 'year' },
  { name: 'Adobe', category: 'software', color: '#fa0f00' },
  { name: 'GitHub', category: 'software', color: '#6e5494' },
  { name: 'iCloud+', category: 'cloud', color: '#3693f3' },
  { name: 'Google One', category: 'cloud', color: '#4285f4' },
  { name: 'Xbox Game Pass', category: 'gaming', color: '#107c10' },
  { name: 'PlayStation Plus', category: 'gaming', color: '#003791' },
  { name: 'Gym', category: 'fitness', color: '#ff4f81' },
  { name: 'Insurance', category: 'insurance', kind: 'bill', color: '#7a5af8' },
  { name: 'Domain name', category: 'software', cycleUnit: 'year', color: '#0ea5a4' },
];

export function colorFor(name: string): string {
  const p = PRESETS.find((x) => x.name.toLowerCase() === name.trim().toLowerCase());
  if (p) return p.color;
  let h = 0;
  for (const c of name) h = (h * 31 + c.charCodeAt(0)) % 360;
  return `hsl(${h} 55% 45%)`;
}

export function monogram(name: string): string {
  const words = name.replace(/[^\p{L}\p{N} ]/gu, ' ').trim().split(/\s+/);
  return ((words[0]?.[0] ?? '?') + (words[1]?.[0] ?? words[0]?.[1] ?? '')).toUpperCase();
}
