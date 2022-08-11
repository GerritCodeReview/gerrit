/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export interface GerritServiceWorkerState {
  latestUpdateTimestampMs: number;
}

const SERVICE_WORKER_DB = 'service-worker-db-1';
const SERVICE_WORKER_STORE = 'states';
const DEFAULT_ID = 1;

function getServiceWorkerDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(SERVICE_WORKER_DB);
    request.onsuccess = () => resolve(request.result);
    request.onerror = reject;
    request.onblocked = reject;
    // event is fired when an attempt was made to open a database with a version
    // higher than its current version
    // https://developer.mozilla.org/en-US/docs/Web/API/IDBOpenDBRequest/upgradeneeded_event
    // it's mainly used to create object stores
    // https://web.dev/indexeddb/#creating-object-stores
    request.onupgradeneeded = () => {
      const db = request.result;
      if (db.objectStoreNames.contains(SERVICE_WORKER_STORE)) return;
      const states = db.createObjectStore(SERVICE_WORKER_STORE, {
        keyPath: 'id',
      });
      states.createIndex('states_id_unique', 'id', {unique: true});
    };
  });
}

export async function putServiceWorkerState(state: GerritServiceWorkerState) {
  const db = await getServiceWorkerDB();
  const tx = db.transaction(SERVICE_WORKER_STORE, 'readwrite');
  const store = tx.objectStore(SERVICE_WORKER_STORE);
  store.put({...state, id: DEFAULT_ID});

  return new Promise<void>(resolve => {
    tx.oncomplete = () => resolve();
  });
}

export async function getServiceWorkerState(): Promise<GerritServiceWorkerState> {
  const db = await getServiceWorkerDB();
  const tx = db.transaction(SERVICE_WORKER_STORE, 'readwrite');
  const store = tx.objectStore(SERVICE_WORKER_STORE);

  return new Promise((resolve, reject) => {
    const request = store.get(DEFAULT_ID);
    request.onsuccess = () => {
      const db = request.result;
      resolve(db);
    };
    request.onerror = reject;
  });
}
