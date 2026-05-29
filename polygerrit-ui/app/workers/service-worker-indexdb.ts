/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export interface GerritServiceWorkerState {
  latestUpdateTimestampMs: number;
  allowBrowserNotificationsPreference: boolean;
}

const SERVICE_WORKER_DB = 'service-worker-db-1';
// Object store - kind of table that holds objects
// https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore
const SERVICE_WORKER_STORE = 'states';
// Service Worker State needs just 1 entry in object store which is rewritten
// every time state is saved. This entry has SERVICE_WORKER_STATE_ID.
const SERVICE_WORKER_STATE_ID = 1;

function getServiceWorkerDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(SERVICE_WORKER_DB);
    request.onsuccess = () => resolve(request.result);
    request.onerror = reject;
    request.onblocked = reject;
    // Event is fired when an attempt was made to open a database with a version
    // higher than its current version.
    // https://developer.mozilla.org/en-US/docs/Web/API/IDBOpenDBRequest/upgradeneeded_event
    // It's mainly used to create object stores.
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
  store.put({...state, id: SERVICE_WORKER_STATE_ID});

  return new Promise<void>(resolve => {
    tx.oncomplete = () => resolve();
  });
}

export async function getServiceWorkerState(): Promise<GerritServiceWorkerState> {
  const db = await getServiceWorkerDB();
  const tx = db.transaction(SERVICE_WORKER_STORE, 'readonly');
  const store = tx.objectStore(SERVICE_WORKER_STORE);

  return new Promise((resolve, reject) => {
    const request = store.get(SERVICE_WORKER_STATE_ID);
    request.onsuccess = () => resolve(request.result);
    request.onerror = reject;
  });
}
