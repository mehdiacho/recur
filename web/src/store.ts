// Local-first storage. Everything lives in this browser's localStorage until sync lands
// (docs/HANDOFF.md, "Sync"). Export/import moves data between the web app and Android.
import { useCallback, useEffect, useState } from 'react';
import { emptyStore, normaliseStore, type Store, type Subscription } from './core/model';

const KEY = 'recur.store.v1';

function load(): Store {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? normaliseStore(JSON.parse(raw)) : emptyStore();
  } catch {
    return emptyStore();
  }
}

function save(s: Store) {
  try { localStorage.setItem(KEY, JSON.stringify(s)); } catch { /* private mode: keep in memory */ }
}

export function useStore() {
  const [store, setStore] = useState<Store>(load);
  useEffect(() => save(store), [store]);

  // Another tab edited the data.
  useEffect(() => {
    const on = (e: StorageEvent) => { if (e.key === KEY) setStore(load()); };
    addEventListener('storage', on);
    return () => removeEventListener('storage', on);
  }, []);

  const upsert = useCallback((subs: Subscription[]) => {
    setStore((s) => {
      const map = new Map(s.subscriptions.map((x) => [x.id, x]));
      for (const x of subs) map.set(x.id, { ...x, updatedAt: Date.now() });
      return { ...s, subscriptions: [...map.values()] };
    });
  }, []);

  const softDelete = useCallback((ids: string[], deleted: boolean) => {
    setStore((s) => ({
      ...s,
      subscriptions: s.subscriptions.map((x) => ids.includes(x.id)
        ? { ...x, deletedAt: deleted ? Date.now() : null, updatedAt: Date.now() } : x),
    }));
  }, []);

  return { store, setStore, upsert, softDelete };
}
