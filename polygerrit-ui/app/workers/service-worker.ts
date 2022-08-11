/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AccountDetailInfo} from '../api/rest-api';
import {ServiceWorkerMessageType} from '../services/service-worker-installer';
import {getServiceWorkerState} from '../utils/indexdb-util';
import {ServiceWorker} from './service-worker-class';

// TODO(Milutin): Put all the business logic of setting things up into the class.

/**
 * `self` is for a worker what `window` is for the web app. It is called
 * the `ServiceWorkerGlobalScope`, see
 * https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope
 */
const ctx = self as {} as ServiceWorkerGlobalScope;

ctx.addEventListener('message', e => {
  if (e.data?.type !== ServiceWorkerMessageType.TRIGGER_NOTIFICATIONS) {
    // Only this notification message type exists, but we do not throw error.
    return;
  }
  const showNotification = async () => {
    const account = e.data?.account as AccountDetailInfo | undefined;
    // Message always contains account, but we do not throw error.
    if (!account?._account_id) return;
    /**
     * We cannot rely on a state in a service worker, because every time
     * service worker starts or stops, new instance is created. So every time
     * there is new instance we load state from indexdb.
     */
    serviceWorker.loadState(await getServiceWorkerState());
    const latestAttentionChanges = await serviceWorker.getChangesToNotify(
      account
    );
    // TODO(milutin): Implement handling more than 1 change
    if (latestAttentionChanges && latestAttentionChanges.length > 0) {
      serviceWorker.showNotification(latestAttentionChanges[0], account);
    }
  };
  e.waitUntil(showNotification());
});

// Code based on code sample from
// https://developer.mozilla.org/en-US/docs/Web/API/Clients/openWindow
ctx.addEventListener<'notificationclick'>('notificationclick', e => {
  e.notification.close();

  const openWindow = async () => {
    const clientsArr = await ctx.clients.matchAll({type: 'window'});
    try {
      const url = e.notification.data.url;
      let client = clientsArr.find(c => c.url === url);
      if (!client) client = (await ctx.clients.openWindow(url)) ?? undefined;
      await client?.focus();
    } catch (e) {
      console.error(`Cannot open window about notified change - ${e}`);
    }
  };

  e.waitUntil(openWindow());
});

/** Singleton instance being referenced in `onmessage` function above. */
const serviceWorker = new ServiceWorker(ctx);
