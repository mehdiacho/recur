import { useEffect, useMemo, useRef, useState } from 'react';
import { addDays, parse, today as todayFn, type Ymd } from './core/dates';
import { toIcs, toSmartSpendTransaction } from './core/export';
import { CATEGORIES, defaultsFor, newId, normaliseStore, type Category, type CycleUnit, type Kind, type Status, type Subscription } from './core/model';
import { colorFor, monogram, PRESETS, type Preset } from './core/presets';
import { daysToEnd, dueReminders, eventsBetween, isLive, monthlyEquivalent, monthSpend, nextCharge, pruneNotified, type MoneyEvent, type Reminder } from './core/schedule';
import { cycleLabel, money, monthName, perLabel, relative, shortDate } from './format';
import { useStore } from './store';

type Tab = 'overview' | 'all' | 'settings';
type Sheet =
  | { kind: 'add' }
  | { kind: 'edit'; sub: Subscription }
  | { kind: 'bulk'; ids: string[] }
  | null;

const ONBOARDED = 'recur.onboarded';

export default function App() {
  const { store, setStore, upsert, softDelete } = useStore();
  const [tab, setTab] = useState<Tab>('overview');
  const [sheet, setSheet] = useState<Sheet>(null);
  const [toast, setToast] = useState<{ text: string; undo?: () => void } | null>(null);
  const [onboarding, setOnboarding] = useState(() => {
    try { return !localStorage.getItem(ONBOARDED); } catch { return false; }
  });
  const today = todayFn();
  const live = store.subscriptions.filter((s) => !s.deletedAt);
  const reminders = useMemo(() => dueReminders(live, today), [live, today]);

  // System notifications for reminders not shown yet, when the page is open.
  useEffect(() => {
    const fresh = reminders.filter((r) => !store.notified.includes(r.key));
    if (!fresh.length) return;
    if ('Notification' in window && Notification.permission === 'granted') {
      for (const r of fresh) new Notification(reminderTitle(r), { body: reminderBody(r, store.currency), tag: r.key });
      setStore((s) => ({ ...s, notified: pruneNotified([...s.notified, ...fresh.map((r) => r.key)], today) }));
    }
  }, [reminders, store.notified, store.currency, setStore, today]);

  const remove = (ids: string[]) => {
    softDelete(ids, true);
    setToast({ text: ids.length === 1 ? 'Deleted' : `Deleted ${ids.length}`, undo: () => softDelete(ids, false) });
  };

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 6000);
    return () => clearTimeout(t);
  }, [toast]);

  return (
    <div className="app">
      <main className="main">
        {tab === 'overview' && <Overview subs={live} currency={store.currency} today={today} reminders={reminders}
          onOpen={(s) => setSheet({ kind: 'edit', sub: s })} onAdd={() => setSheet({ kind: 'add' })} />}
        {tab === 'all' && <AllList subs={live} currency={store.currency} today={today}
          onOpen={(s) => setSheet({ kind: 'edit', sub: s })} onBulk={(ids) => setSheet({ kind: 'bulk', ids })}
          onDelete={remove} onAdd={() => setSheet({ kind: 'add' })} />}
        {tab === 'settings' && <Settings store={store} setStore={setStore} today={today} toast={(t) => setToast({ text: t })}
          onTour={() => setOnboarding(true)} />}
      </main>

      {tab !== 'settings' && (
        <button className="fab" onClick={() => setSheet({ kind: 'add' })} aria-label="Add a subscription">
          <span aria-hidden>+</span> Add
        </button>
      )}

      <nav className="tabs" aria-label="Sections">
        {(['overview', 'all', 'settings'] as Tab[]).map((t) => (
          <button key={t} className={tab === t ? 'on' : ''} onClick={() => setTab(t)} aria-current={tab === t}>
            {t === 'overview' ? 'Overview' : t === 'all' ? 'Subscriptions' : 'Settings'}
          </button>
        ))}
      </nav>

      {sheet && (
        <SheetFrame onClose={() => setSheet(null)}>
          {sheet.kind === 'add' && <Editor currency={store.currency} today={today}
            onSave={(s) => { upsert([s]); setSheet(null); setToast({ text: `${s.name} added` }); }} onCancel={() => setSheet(null)} />}
          {sheet.kind === 'edit' && <Editor currency={store.currency} today={today} initial={sheet.sub}
            onSave={(s) => { upsert([s]); setSheet(null); }} onCancel={() => setSheet(null)}
            onDelete={() => { remove([sheet.sub.id]); setSheet(null); }} />}
          {sheet.kind === 'bulk' && <BulkEditor subs={live.filter((s) => sheet.ids.includes(s.id))}
            onSave={(subs) => { upsert(subs); setSheet(null); setToast({ text: `Updated ${subs.length}` }); }}
            onCancel={() => setSheet(null)} />}
        </SheetFrame>
      )}

      {toast && (
        <div className="toast" role="status">
          <span>{toast.text}</span>
          {toast.undo && <button onClick={() => { toast.undo!(); setToast(null); }}>Undo</button>}
        </div>
      )}

      {onboarding && <Onboarding onDone={() => { setOnboarding(false); try { localStorage.setItem(ONBOARDED, '1'); } catch { /* ignore */ } }} />}
    </div>
  );
}

export function reminderTitle(r: Reminder): string {
  const when = r.daysUntil === 0 ? 'today' : r.daysUntil === 1 ? 'tomorrow' : `in ${r.daysUntil} days`;
  if (r.type === 'contract-end') return `${r.name}: contract ends ${when}`;
  if (r.type === 'trial-end') return `${r.name}: trial ends ${when}`;
  return `${r.name} renews ${when}`;
}

function reminderBody(r: Reminder, currency: string): string {
  if (r.type === 'contract-end') return r.amount ? `Renewal cost ${money(r.amount, currency)}. Decide whether to renew, upgrade or switch.` : 'Decide whether to renew, upgrade or switch.';
  if (r.type === 'trial-end') return `First charge of ${money(r.amount, currency)}. Cancel before then if you don't want it.`;
  return `${money(r.amount, currency)} on ${shortDate(r.date)}.`;
}

/* ---------------- Overview ---------------- */

function Overview({ subs, currency, today, reminders, onOpen, onAdd }: {
  subs: Subscription[]; currency: string; today: Ymd; reminders: Reminder[];
  onOpen: (s: Subscription) => void; onAdd: () => void;
}) {
  const t = parse(today);
  const [ym, setYm] = useState({ y: t.y, m: t.m });
  const spend = monthSpend(subs, ym.y, ym.m);
  const shift = (d: number) => setYm(({ y, m }) => { const k = y * 12 + m - 1 + d; return { y: Math.floor(k / 12), m: (k % 12) + 1 }; });
  const isThisMonth = ym.y === t.y && ym.m === t.m;
  const upcoming = eventsBetween(subs, today, addDays(today, 30));
  const endings = subs.filter((s) => isLive(s) && s.endDate && (daysToEnd(s, today) ?? -1) >= 0 && (daysToEnd(s, today) ?? 999) <= 120)
    .sort((a, b) => (a.endDate! < b.endDate! ? -1 : 1));
  const byId = new Map(subs.map((s) => [s.id, s]));
  const byCat = new Map<Category, number>();
  for (const s of subs) byCat.set(s.category, (byCat.get(s.category) ?? 0) + monthlyEquivalent(s));
  const cats = [...byCat.entries()].filter(([, v]) => v > 0).sort((a, b) => b[1] - a[1]);
  const maxCat = cats[0]?.[1] ?? 1;

  if (!subs.length) {
    return (
      <section className="empty">
        <h1>Recur</h1>
        <p>Every subscription, contract and free trial in one place, with a reminder before each one renews or ends.</p>
        <button className="primary" onClick={onAdd}>Add your first one</button>
      </section>
    );
  }

  return (
    <>
      <header className="hero">
        <div className="month">
          <button onClick={() => shift(-1)} aria-label="Previous month">‹</button>
          <span>{monthName(ym.m)} {ym.y !== t.y ? ym.y : ''}</span>
          <button onClick={() => shift(1)} aria-label="Next month">›</button>
          {!isThisMonth && <button className="link" onClick={() => setYm({ y: t.y, m: t.m })}>This month</button>}
        </div>
        <p className="label">{isThisMonth ? 'Leaving your account this month' : `Leaving your account in ${monthName(ym.m)}`}</p>
        <p className="big">{money(spend.total, currency)}</p>
        <p className="sub">Monthly average {money(spend.average, currency)} · {money(spend.average * 12, currency)} a year</p>
      </header>

      {reminders.length > 0 && (
        <section className="card alerts" aria-label="Reminders">
          <h2>Needs attention</h2>
          {reminders.map((r) => (
            <button key={r.key} className="alert" onClick={() => byId.get(r.subId) && onOpen(byId.get(r.subId)!)}>
              <strong>{reminderTitle(r)}</strong>
              <span>{reminderBody(r, currency)}</span>
            </button>
          ))}
        </section>
      )}

      {endings.length > 0 && (
        <section className="card" aria-label="Contracts ending">
          <h2>Contracts and commitments ending</h2>
          {endings.map((s) => {
            const n = daysToEnd(s, today)!;
            return (
              <button key={s.id} className="row" onClick={() => onOpen(s)}>
                <Tile name={s.name} />
                <div className="grow">
                  <div className="name">{s.name}</div>
                  <div className="meta">Ends {shortDate(s.endDate!, today)} · {s.autoRenews ? 'rolls on at the same price' : 'stops'}{s.renewalCost ? ` · renewal ${money(s.renewalCost, currency)}` : ''}</div>
                </div>
                <div className={`countdown ${n <= 30 ? 'hot' : ''}`}><b>{n}</b><span>days</span></div>
              </button>
            );
          })}
        </section>
      )}

      <section className="card" aria-label="This month">
        <h2>{isThisMonth ? 'This month' : monthName(ym.m)}</h2>
        {spend.events.length === 0 && <p className="muted">Nothing due.</p>}
        {spend.events.map((e) => <EventRow key={`${e.subId}${e.date}${e.type}`} e={e} sub={byId.get(e.subId)} currency={currency} today={today} onOpen={onOpen} />)}
      </section>

      <section className="card" aria-label="Next 30 days">
        <h2>Next 30 days</h2>
        {upcoming.length === 0 && <p className="muted">Nothing in the next 30 days.</p>}
        {upcoming.map((e) => <EventRow key={`${e.subId}${e.date}${e.type}`} e={e} sub={byId.get(e.subId)} currency={currency} today={today} onOpen={onOpen} />)}
      </section>

      {cats.length > 0 && (
        <section className="card" aria-label="By category">
          <h2>Average per month, by category</h2>
          {cats.map(([c, v]) => (
            <div key={c} className="bar">
              <span className="cat">{c}</span>
              <span className="track"><span style={{ width: `${(v / maxCat) * 100}%` }} /></span>
              <span className="amt">{money(v, currency)}</span>
            </div>
          ))}
        </section>
      )}
    </>
  );
}

function EventRow({ e, sub, currency, today, onOpen }: { e: MoneyEvent; sub?: Subscription; currency: string; today: Ymd; onOpen: (s: Subscription) => void }) {
  const label = e.type === 'contract-end' ? 'Contract ends' : e.type === 'trial-end' ? 'Trial ends, first charge' : 'Renews';
  const past = e.date < today;
  return (
    <button className={`row ${past ? 'past' : ''}`} onClick={() => sub && onOpen(sub)}>
      <Tile name={e.name} />
      <div className="grow">
        <div className="name">{e.name}</div>
        <div className="meta">{label} {shortDate(e.date, today)} · {relative(e.date, today)}</div>
      </div>
      <div className="amount">{e.amount ? money(e.amount, currency) : '—'}</div>
    </button>
  );
}

function Tile({ name }: { name: string }) {
  return <span className="tile" style={{ background: colorFor(name) }} aria-hidden>{monogram(name)}</span>;
}

/* ---------------- All subscriptions ---------------- */

function AllList({ subs, currency, today, onOpen, onBulk, onDelete, onAdd }: {
  subs: Subscription[]; currency: string; today: Ymd;
  onOpen: (s: Subscription) => void; onBulk: (ids: string[]) => void; onDelete: (ids: string[]) => void; onAdd: () => void;
}) {
  const [selecting, setSelecting] = useState(false);
  const [sel, setSel] = useState<string[]>([]);
  const [q, setQ] = useState('');
  const rows = subs
    .filter((s) => !q || `${s.name} ${s.provider ?? ''} ${s.category}`.toLowerCase().includes(q.toLowerCase()))
    .map((s) => ({ s, next: nextCharge(s, today) }))
    .sort((a, b) => statusRank(a.s.status) - statusRank(b.s.status) || (a.next ?? '9999') .localeCompare(b.next ?? '9999'));
  const toggle = (id: string) => setSel((x) => (x.includes(id) ? x.filter((y) => y !== id) : [...x, id]));

  if (!subs.length) {
    return <section className="empty"><p>No subscriptions yet.</p><button className="primary" onClick={onAdd}>Add one</button></section>;
  }

  return (
    <>
      <header className="listhead">
        <h1>Subscriptions</h1>
        <button className="link" onClick={() => { setSelecting(!selecting); setSel([]); }}>{selecting ? 'Done' : 'Select'}</button>
      </header>
      <input className="search" placeholder="Search" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search subscriptions" />
      <section className="card flush">
        {rows.map(({ s, next }) => (
          <div key={s.id} className={`row ${s.status !== 'active' ? 'past' : ''}`}>
            {selecting && <input type="checkbox" checked={sel.includes(s.id)} onChange={() => toggle(s.id)} aria-label={`Select ${s.name}`} />}
            <button className="rowbtn" onClick={() => (selecting ? toggle(s.id) : onOpen(s))}>
              <Tile name={s.name} />
              <div className="grow">
                <div className="name">{s.name}{s.kind !== 'subscription' && <span className="chip">{s.kind}</span>}{s.status !== 'active' && <span className="chip">{s.status}</span>}</div>
                <div className="meta">{next ? `Next ${shortDate(next, today)}, ${relative(next, today)}` : s.endDate && s.endDate <= today ? `Ended ${shortDate(s.endDate, today)}` : 'No upcoming charge'}</div>
              </div>
              <div className="amount">{money(s.amount, currency)}<small>{perLabel(s.cycleUnit, s.cycleEvery)}</small></div>
            </button>
          </div>
        ))}
      </section>
      {selecting && (
        <div className="bulkbar">
          <span>{sel.length} selected</span>
          <button className="link" onClick={() => setSel(sel.length === rows.length ? [] : rows.map((r) => r.s.id))}>{sel.length === rows.length ? 'None' : 'All'}</button>
          <button disabled={!sel.length} onClick={() => onBulk(sel)}>Edit together</button>
          <button disabled={!sel.length} className="danger" onClick={() => { onDelete(sel); setSel([]); }}>Delete</button>
        </div>
      )}
    </>
  );
}

function statusRank(s: Status) { return s === 'active' ? 0 : s === 'paused' ? 1 : 2; }

/* ---------------- Add and edit ---------------- */

function SheetFrame({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const on = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    addEventListener('keydown', on);
    ref.current?.querySelector<HTMLElement>('input,button')?.focus();
    return () => removeEventListener('keydown', on);
  }, [onClose]);
  return (
    <div className="scrim" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="sheet" role="dialog" aria-modal ref={ref}>{children}</div>
    </div>
  );
}

const KINDS: { k: Kind; label: string; hint: string }[] = [
  { k: 'subscription', label: 'Subscription', hint: 'Renews until you cancel' },
  { k: 'contract', label: 'Contract', hint: 'Has an end date, like a lease or a fixed-term plan' },
  { k: 'trial', label: 'Free trial', hint: 'Free until a date, then it charges' },
  { k: 'bill', label: 'Bill', hint: 'Insurance, rent, anything regular' },
];

function Editor({ initial, currency, today, onSave, onCancel, onDelete }: {
  initial?: Subscription; currency: string; today: Ymd;
  onSave: (s: Subscription) => void; onCancel: () => void; onDelete?: () => void;
}) {
  const [step, setStep] = useState<'pick' | 'details'>(initial ? 'details' : 'pick');
  const [q, setQ] = useState('');
  const [f, setF] = useState(() => initial ? toForm(initial) : blankForm(today));
  const set = <K extends keyof Form>(k: K, v: Form[K]) => setF((x) => ({ ...x, [k]: v }));

  const pick = (p?: Preset) => {
    const kind = p?.kind ?? 'subscription';
    setF({ ...blankForm(today), name: p?.name ?? q, category: p?.category ?? 'other', kind,
      cycleUnit: p?.cycleUnit ?? 'month', url: p?.url ?? '', ...formDefaults(kind) });
    setStep('details');
  };

  if (step === 'pick') {
    const list = PRESETS.filter((p) => p.name.toLowerCase().includes(q.toLowerCase()));
    return (
      <>
        <div className="sheethead"><h2>Add</h2><button className="link" onClick={onCancel}>Cancel</button></div>
        <p className="step">Step 1 of 2 · Pick one, or type your own</p>
        <input className="search" placeholder="Netflix, Mascom bundle, gym…" value={q} onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && q.trim() && pick(list.length === 1 ? list[0] : undefined)} autoFocus />
        <div className="grid">
          {q.trim() && !list.some((p) => p.name.toLowerCase() === q.trim().toLowerCase()) && (
            <button className="pick custom" onClick={() => pick()}><Tile name={q} /><span>Add “{q.trim()}”</span></button>
          )}
          {list.map((p) => <button key={p.name} className="pick" onClick={() => pick(p)}><Tile name={p.name} /><span>{p.name}</span></button>)}
          {!q && <button className="pick custom" onClick={() => pick()}><span className="tile plus">+</span><span>Something else</span></button>}
        </div>
      </>
    );
  }

  const valid = f.name.trim() && f.amount !== '' && +f.amount >= 0 && f.anchorDate && (f.kind !== 'contract' || f.endDate);
  const preview = valid ? fromForm(f, initial, currency) : null;
  const next = preview ? nextCharge(preview, today) : null;

  return (
    <form onSubmit={(e) => { e.preventDefault(); if (preview) onSave(preview); }}>
      <div className="sheethead">
        {!initial ? <button type="button" className="link" onClick={() => setStep('pick')}>‹ Back</button> : <span />}
        <h2>{initial ? 'Edit' : 'Step 2 of 2'}</h2>
        <button type="button" className="link" onClick={onCancel}>Cancel</button>
      </div>

      <div className="segmented" role="radiogroup" aria-label="Type">
        {KINDS.map(({ k, label }) => (
          <button type="button" key={k} role="radio" aria-checked={f.kind === k} className={f.kind === k ? 'on' : ''}
            onClick={() => setF((x) => ({ ...x, kind: k, ...(initial ? {} : formDefaults(k)) }))}>{label}</button>
        ))}
      </div>
      <p className="hint">{KINDS.find((x) => x.k === f.kind)!.hint}</p>

      <label>Name<input value={f.name} onChange={(e) => set('name', e.target.value)} required /></label>
      <div className="two">
        <label>{f.kind === 'trial' ? 'Price after the trial' : 'Price'} ({currency})
          <input inputMode="decimal" value={f.amount} onChange={(e) => set('amount', e.target.value.replace(',', '.'))} placeholder="0.00" required />
        </label>
        <label>Billed
          <div className="inline">
            <span>every</span>
            <input className="tiny" type="number" min={1} value={f.cycleEvery} onChange={(e) => set('cycleEvery', e.target.value)} aria-label="Cycle count" />
            <select value={f.cycleUnit} onChange={(e) => set('cycleUnit', e.target.value as CycleUnit)} aria-label="Cycle unit">
              <option value="day">day{+f.cycleEvery > 1 ? 's' : ''}</option><option value="week">week{+f.cycleEvery > 1 ? 's' : ''}</option>
              <option value="month">month{+f.cycleEvery > 1 ? 's' : ''}</option><option value="year">year{+f.cycleEvery > 1 ? 's' : ''}</option>
            </select>
          </div>
        </label>
      </div>
      <label>{f.kind === 'trial' ? 'Trial ends (first charge)' : f.kind === 'contract' ? 'First bill date' : 'A billing date (any past or next one)'}
        <input type="date" value={f.anchorDate} onChange={(e) => set('anchorDate', e.target.value)} required />
      </label>

      {(f.kind === 'contract' || f.endDate) && (
        <fieldset>
          <legend>Contract end</legend>
          <label>Ends on<input type="date" value={f.endDate} onChange={(e) => set('endDate', e.target.value)} required={f.kind === 'contract'} /></label>
          <label className="check"><input type="checkbox" checked={f.autoRenews} onChange={(e) => set('autoRenews', e.target.checked)} />
            Keeps charging after it ends (out of contract)</label>
          <label>Renewal or upgrade cost on that day ({currency}, optional)
            <input inputMode="decimal" value={f.renewalCost} onChange={(e) => set('renewalCost', e.target.value.replace(',', '.'))} placeholder="0.00" />
          </label>
        </fieldset>
      )}
      {f.kind !== 'contract' && !f.endDate && (
        <button type="button" className="link small" onClick={() => set('endDate', addDays(today, 365))}>+ Add an end date</button>
      )}

      <fieldset>
        <legend>Reminders</legend>
        <label>Days before
          <input value={f.remindDays} onChange={(e) => set('remindDays', e.target.value)} placeholder="3, 1" />
        </label>
        <label className="check"><input type="checkbox" checked={f.remindCharges} onChange={(e) => set('remindCharges', e.target.checked)} />
          Remind before every charge</label>
        <p className="hint">{f.kind === 'contract' ? 'You always get reminded before the contract ends.' : f.kind === 'trial' ? 'You always get reminded before the trial ends.' : ''} You also get one on the day.</p>
      </fieldset>

      <details>
        <summary>More</summary>
        <label>Category<select value={f.category} onChange={(e) => set('category', e.target.value as Category)}>
          {CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select></label>
        <label>Provider<input value={f.provider} onChange={(e) => set('provider', e.target.value)} /></label>
        <label>Paid with<input value={f.paymentMethod} onChange={(e) => set('paymentMethod', e.target.value)} placeholder="FNB card, airtime…" /></label>
        <label>Manage link<input type="url" value={f.url} onChange={(e) => set('url', e.target.value)} placeholder="https://" /></label>
        <label>Notes<textarea value={f.notes} onChange={(e) => set('notes', e.target.value)} rows={2} /></label>
        {initial && (
          <label>Status<select value={f.status} onChange={(e) => set('status', e.target.value as Status)}>
            <option value="active">Active</option><option value="paused">Paused</option><option value="cancelled">Cancelled</option></select></label>
        )}
      </details>

      {preview && (
        <p className="preview">
          {money(preview.amount, currency)} {cycleLabel(preview.cycleUnit, preview.cycleEvery)}
          {' · about '}{money(monthlyEquivalent({ ...preview, status: 'active' }), currency)} a month
          {next && <> · next {shortDate(next, today)}</>}
        </p>
      )}

      <div className="actions">
        {onDelete && <button type="button" className="danger" onClick={onDelete}>Delete</button>}
        <button className="primary" disabled={!valid}>Save</button>
      </div>
    </form>
  );
}

interface Form {
  name: string; kind: Kind; amount: string; cycleUnit: CycleUnit; cycleEvery: string; anchorDate: string;
  endDate: string; autoRenews: boolean; renewalCost: string; remindDays: string; remindCharges: boolean;
  category: Category; provider: string; paymentMethod: string; url: string; notes: string; status: Status;
}

function formDefaults(kind: Kind) {
  const d = defaultsFor(kind);
  return { remindDays: d.remindDays.join(', '), remindCharges: d.remindCharges, autoRenews: d.autoRenews };
}

function blankForm(today: Ymd): Form {
  return { name: '', kind: 'subscription', amount: '', cycleUnit: 'month', cycleEvery: '1', anchorDate: today, endDate: '',
    renewalCost: '', category: 'other', provider: '', paymentMethod: '', url: '', notes: '', status: 'active', ...formDefaults('subscription') };
}

function toForm(s: Subscription): Form {
  return { name: s.name, kind: s.kind, amount: String(s.amount), cycleUnit: s.cycleUnit, cycleEvery: String(s.cycleEvery),
    anchorDate: s.anchorDate, endDate: s.endDate ?? '', autoRenews: s.autoRenews, renewalCost: s.renewalCost == null ? '' : String(s.renewalCost),
    remindDays: s.remindDays.join(', '), remindCharges: s.remindCharges, category: s.category, provider: s.provider ?? '',
    paymentMethod: s.paymentMethod ?? '', url: s.url ?? '', notes: s.notes ?? '', status: s.status };
}

function fromForm(f: Form, initial: Subscription | undefined, currency: string): Subscription {
  const now = Date.now();
  const status = f.status;
  return {
    id: initial?.id ?? newId(),
    name: f.name.trim(), provider: f.provider.trim() || null, category: f.category, kind: f.kind,
    amount: +f.amount || 0, currency: initial?.currency ?? currency,
    cycleUnit: f.cycleUnit, cycleEvery: Math.max(1, Math.floor(+f.cycleEvery || 1)), anchorDate: f.anchorDate,
    endDate: f.endDate || null, autoRenews: f.autoRenews, renewalCost: f.renewalCost === '' ? null : +f.renewalCost,
    remindDays: parseDays(f.remindDays), remindCharges: f.remindCharges, status,
    cancelledOn: status === 'cancelled' ? (initial?.cancelledOn ?? todayFn()) : null,
    paymentMethod: f.paymentMethod.trim() || null, url: f.url.trim() || null, notes: f.notes.trim() || null,
    createdAt: initial?.createdAt ?? now, updatedAt: now, deletedAt: null,
  };
}

function parseDays(s: string): number[] {
  return [...new Set(s.split(/[^\d]+/).filter(Boolean).map(Number).filter((n) => n >= 0 && n <= 400))].sort((a, b) => b - a);
}

/* ---------------- Bulk edit ---------------- */

function BulkEditor({ subs, onSave, onCancel }: { subs: Subscription[]; onSave: (s: Subscription[]) => void; onCancel: () => void }) {
  const [category, setCategory] = useState<'' | Category>('');
  const [status, setStatus] = useState<'' | Status>('');
  const [paymentMethod, setPaymentMethod] = useState('');
  const [remindDays, setRemindDays] = useState('');
  const [price, setPrice] = useState<Record<string, string>>(() => Object.fromEntries(subs.map((s) => [s.id, String(s.amount)])));
  const save = () => onSave(subs.map((s) => ({
    ...s,
    amount: price[s.id] === '' || isNaN(+price[s.id]) ? s.amount : +price[s.id],
    ...(category && { category }),
    ...(status && { status, cancelledOn: status === 'cancelled' ? (s.cancelledOn ?? todayFn()) : null }),
    ...(paymentMethod.trim() && { paymentMethod: paymentMethod.trim() }),
    ...(remindDays.trim() && { remindDays: parseDays(remindDays) }),
  })));
  return (
    <>
      <div className="sheethead"><h2>Edit {subs.length} together</h2><button className="link" onClick={onCancel}>Cancel</button></div>
      <p className="hint">Blank fields stay as they are.</p>
      <div className="two">
        <label>Category<select value={category} onChange={(e) => setCategory(e.target.value as Category)}>
          <option value="">Unchanged</option>{CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select></label>
        <label>Status<select value={status} onChange={(e) => setStatus(e.target.value as Status)}>
          <option value="">Unchanged</option><option value="active">Active</option><option value="paused">Paused</option><option value="cancelled">Cancelled</option></select></label>
      </div>
      <label>Paid with<input value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)} placeholder="Unchanged" /></label>
      <label>Reminder days<input value={remindDays} onChange={(e) => setRemindDays(e.target.value)} placeholder="Unchanged" /></label>
      <fieldset>
        <legend>Prices</legend>
        {subs.map((s) => (
          <label key={s.id} className="priceline"><span><Tile name={s.name} /> {s.name}</span>
            <input inputMode="decimal" value={price[s.id]} onChange={(e) => setPrice((p) => ({ ...p, [s.id]: e.target.value.replace(',', '.') }))} aria-label={`${s.name} price`} />
          </label>
        ))}
      </fieldset>
      <div className="actions"><button className="primary" onClick={save}>Save all</button></div>
    </>
  );
}

/* ---------------- Settings ---------------- */

function download(name: string, body: string, type: string) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([body], { type }));
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

function Settings({ store, setStore, today, toast, onTour }: {
  store: ReturnType<typeof useStore>['store']; setStore: ReturnType<typeof useStore>['setStore'];
  today: Ymd; toast: (t: string) => void; onTour: () => void;
}) {
  const [perm, setPerm] = useState(() => ('Notification' in window ? Notification.permission : 'unsupported'));
  const file = useRef<HTMLInputElement>(null);
  const live = store.subscriptions.filter((s) => !s.deletedAt);
  const deleted = store.subscriptions.filter((s) => s.deletedAt);
  const t = parse(today);

  return (
    <>
      <header className="listhead"><h1>Settings</h1></header>
      <section className="card">
        <label>Currency
          <select value={store.currency} onChange={(e) => setStore((s) => ({ ...s, currency: e.target.value }))}>
            {['BWP', 'ZAR', 'USD', 'EUR', 'GBP', 'NAD', 'ZMW'].map((c) => <option key={c}>{c}</option>)}
          </select>
        </label>
      </section>

      <section className="card">
        <h2>Reminders</h2>
        <p className="muted">This website can only remind you while it is open. For reminders that always arrive, use the Android app, or add the dates to your calendar.</p>
        {perm === 'default' && <button onClick={async () => setPerm(await Notification.requestPermission())}>Allow notifications</button>}
        {perm === 'granted' && <p className="muted">Notifications are on for this browser.</p>}
        {perm === 'denied' && <p className="muted">Notifications are blocked in this browser's site settings.</p>}
        <button onClick={() => download('recur.ics', toIcs(live, today, 365, store.currency), 'text/calendar')}>Add to calendar (.ics, next 12 months)</button>
      </section>

      <section className="card">
        <h2>Your data</h2>
        <p className="muted">Stored only in this browser. Export it to move it to the Android app, or as a backup.</p>
        <div className="buttons">
          <button onClick={() => download(`recur-${today}.json`, JSON.stringify(store, null, 2), 'application/json')}>Export</button>
          <button onClick={() => file.current?.click()}>Import</button>
          <input ref={file} type="file" accept="application/json,.json" hidden onChange={async (e) => {
            const f = e.target.files?.[0]; if (!f) return;
            try {
              const incoming = normaliseStore(JSON.parse(await f.text()));
              setStore((s) => {
                const map = new Map(s.subscriptions.map((x) => [x.id, x]));
                for (const x of incoming.subscriptions) { const cur = map.get(x.id); if (!cur || x.updatedAt >= cur.updatedAt) map.set(x.id, x); }
                return { ...s, subscriptions: [...map.values()] };
              });
              toast(`Imported ${incoming.subscriptions.length}`);
            } catch { toast('That file is not a Recur export'); }
            e.target.value = '';
          }} />
          <button onClick={() => {
            const ev = monthSpend(live, t.y, t.m).events;
            const txns = ev.filter((x) => x.amount > 0).map((x) => toSmartSpendTransaction(live.find((s) => s.id === x.subId)!, x, '<uid>'));
            download(`recur-smartspend-${t.y}-${String(t.m).padStart(2, '0')}.json`, JSON.stringify(txns, null, 2), 'application/json');
          }}>SmartSpend: this month as transactions</button>
        </div>
        {deleted.length > 0 && (
          <>
            <h3>Recently deleted</h3>
            {deleted.map((s) => (
              <div key={s.id} className="row"><Tile name={s.name} /><div className="grow name">{s.name}</div>
                <button className="link" onClick={() => setStore((x) => ({ ...x, subscriptions: x.subscriptions.map((y) => y.id === s.id ? { ...y, deletedAt: null, updatedAt: Date.now() } : y) }))}>Restore</button>
              </div>
            ))}
          </>
        )}
      </section>

      <section className="card">
        <h2>About</h2>
        <p className="muted">Recur is the subscriptions part of SmartSpend. The Android app shows the same numbers and sends reminders in the background.</p>
        <button className="link" onClick={onTour}>Show the tour again</button>
      </section>
    </>
  );
}

/* ---------------- Onboarding ---------------- */

const TOUR = [
  { t: 'Add what you pay for', b: 'Tap Add, pick a service or type your own, then set the price and a billing date. Contracts and free trials get an end date too.' },
  { t: 'See what leaves your account', b: 'Overview shows what you pay this month, including yearly renewals and contract fees, next to your monthly average.' },
  { t: 'Get reminded in time', b: 'You get a reminder before each renewal you choose, and always before a contract or trial ends.' },
  { t: 'Change many at once', b: 'In Subscriptions, tap Select to edit prices, categories or reminders for several at once, or delete them. Deleting can be undone.' },
];

function Onboarding({ onDone }: { onDone: () => void }) {
  const [i, setI] = useState(0);
  const last = i === TOUR.length - 1;
  return (
    <div className="scrim">
      <div className="sheet tour" role="dialog" aria-modal aria-labelledby="tour-t">
        <p className="step">{i + 1} of {TOUR.length}</p>
        <h2 id="tour-t">{TOUR[i].t}</h2>
        <p>{TOUR[i].b}</p>
        <div className="actions">
          <button className="link" onClick={onDone}>Skip</button>
          <button className="primary" onClick={() => (last ? onDone() : setI(i + 1))} autoFocus>{last ? 'Start' : 'Next'}</button>
        </div>
      </div>
    </div>
  );
}
