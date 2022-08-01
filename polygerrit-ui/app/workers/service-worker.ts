/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AccountDetailInfo} from '../api/rest-api';
import {readResponsePayload} from '../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {
  ServiceWorkerMessageType,
  TRIGGER_NOTIFICATION_UPDATES_MS,
} from '../services/service-worker-installer';
import {ParsedChangeInfo} from '../types/types';
import {getReason} from '../utils/attention-set-util';
import {filterAttentionChangesAfter} from '../utils/service-worker-util';

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

class ServiceWorker {
  latestUpdateTimestampMs?: number;

  showNotification(change: ParsedChangeInfo, account: AccountDetailInfo) {
    const body = getReason(undefined, account, change);
    // TODO(milutin): Implement event.action that
    // focus on firstWindowClient and open change there.
    // TODO(milutin): Add gerrit host icon
    ctx.registration.showNotification(change.subject, {body});
  }

  async getChangesToNotify(account: AccountDetailInfo) {
    const changes = await serviceWorker.getLatestAttentionSetChanges();
    return filterAttentionChangesAfter(
      changes,
      account,
      this.latestUpdateTimestampMs!
    );
  }

  async getLatestAttentionSetChanges(): Promise<ParsedChangeInfo[]> {
    // We throttle polling, since there can be many clients triggerring
    // always only one service worker.
    if (this.latestUpdateTimestampMs) {
      const durationFromLatestUpdateMS =
        Date.now() - this.latestUpdateTimestampMs;
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
    return changes ?? [];
  }
}

/** Singleton instance being referenced in `onmessage` function above. */
const serviceWorker = new ServiceWorker();
