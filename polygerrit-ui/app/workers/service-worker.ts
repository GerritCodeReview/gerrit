/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {readResponsePayload} from '../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {ParsedChangeInfo} from '../types/types';

/**
 * `self` is for a worker what `window` is for the web app. It is called
 * the `ServiceWorkerGlobalScope`, see
 * https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope
 */
const ctx = self as {} as ServiceWorkerGlobalScope;

// TODO(milutin): Move to onmessage, that webapp will trigger every 5 minutes.
// "Push" is used for testing purposes, since it is easy to trigger
// from dev tools.
ctx.addEventListener('push', async () => {
  const changes = await serviceWorker.getLatestAttentionSetChange();
  // TODO(milutin): Implement handling more than 1 change
  if (changes.length > 0) {
    serviceWorker.showNotification(changes[0]);
  }
});

class ServiceWorker {
  showNotification(change: ParsedChangeInfo) {
    // TODO(milutin): Replace with getReason from attention-set-util.
    // For get Reason you will need AccountInfo.
    const body = 'Administrator replied on the change';
    // TODO(milutin): Implement event.action that
    // focus on firstWindowClient and open change there.
    // TODO(milutin): Add gerrit host icon
    ctx.registration.showNotification(change.subject, {body});
  }

  async getLatestAttentionSetChange() {
    // TODO(milutin): Implement more generic query builder
    const response = await fetch(
      '/changes/?O=1000081&S=0&n=25&q=attention%3Aself'
    );
    const payload = await readResponsePayload(response);
    const changes = payload.parsed as unknown as ParsedChangeInfo[];
    // TODO(milutin): Filter changes you already notified about.
    return changes;
  }
}

/** Singleton instance being referenced in `onmessage` function above. */
const serviceWorker = new ServiceWorker();
