/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {readResponsePayload} from '../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {
  ServiceWorkerMessageType,
  TRIGGER_NOTIFICATION_UPDATES_MS,
} from '../services/service-worker-installer';
import {ParsedChangeInfo} from '../types/types';
import {getRandomInt} from '../utils/math-util';

/**
 * `self` is for a worker what `window` is for the web app. It is called
 * the `ServiceWorkerGlobalScope`, see
 * https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope
 */
const ctx = self as {} as ServiceWorkerGlobalScope;

ctx.addEventListener('message', async event => {
  if (event.data?.type !== ServiceWorkerMessageType.TRIGGER_NOTIFICATIONS) {
    throw new Error(`Unsupported event type: ${event.data?.type}`);
  }
  const changes = await serviceWorker.getLatestAttentionSetChange();
  // TODO(milutin): Implement handling more than 1 change
  if (changes && changes.length > 0) {
    serviceWorker.showNotification(changes[0]);
  }
});

class ServiceWorker {
  latestUpdateTimestampMs?: number;

  showNotification(change: ParsedChangeInfo) {
    // TODO(milutin): Replace with getReason from attention-set-util.
    // For get Reason you will need AccountInfo.
    const body = 'Administrator replied on the change';
    // TODO(milutin): Implement event.action that
    // focus on firstWindowClient and open change there.
    // TODO(milutin): Add gerrit host icon
    ctx.registration.showNotification(change.subject, {body});
  }

  async getLatestAttentionSetChange(): Promise<ParsedChangeInfo[]> {
    // We throttle polling, since there can be many clients triggerring
    // always only one service worker.
    if (this.latestUpdateTimestampMs) {
      const durationFromLatestUpdateMS =
        Date.now() - this.latestUpdateTimestampMs - getRandomInt(0, 100);
      if (durationFromLatestUpdateMS < TRIGGER_NOTIFICATION_UPDATES_MS) {
        return [];
      }
    }
    this.latestUpdateTimestampMs = Date.now();
    // TODO(milutin): Implement more generic query builder
    const response = await fetch(
      '/changes/?O=1000081&S=0&n=25&q=attention%3Aself'
    );
    const payload = await readResponsePayload(response);
    const changes = payload.parsed as unknown as ParsedChangeInfo[] | undefined;
    // TODO(milutin): Filter changes you are already notified about.
    return changes ?? [];
  }
}

/** Singleton instance being referenced in `onmessage` function above. */
const serviceWorker = new ServiceWorker();
