/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AccountDetailInfo} from '../api/rest-api';
import {ServiceWorkerMessageType} from '../services/service-worker-installer';
import {ServiceWorker} from './service-worker-class';

/**
 * `self` is for a worker what `window` is for the web app. It is called
 * the `ServiceWorkerGlobalScope`, see
 * https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope
 */
const ctx = self as {} as ServiceWorkerGlobalScope;

ctx.addEventListener('message', async event => {
  if (event.data?.type !== ServiceWorkerMessageType.TRIGGER_NOTIFICATIONS) {
    // Only this notification message type exists, but we do not throw error.
    return;
  }
  const account = event.data?.account as AccountDetailInfo | undefined;
  // Message always contains account, but we do not throw error.
  if (!account?._account_id) return;
  const latestAttentionChanges = await serviceWorker.getChangesToNotify(
    account
  );
  // TODO(milutin): Implement handling more than 1 change
  if (latestAttentionChanges && latestAttentionChanges.length > 0) {
    serviceWorker.showNotification(latestAttentionChanges[0], account);
  }
});

/** Singleton instance being referenced in `onmessage` function above. */
const serviceWorker = new ServiceWorker(ctx);
