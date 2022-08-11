/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ParsedChangeInfo} from '../types/types';
import {getReason} from '../utils/attention-set-util';
import {readResponsePayload} from '../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {filterAttentionChangesAfter} from '../utils/service-worker-util';
import {AccountDetailInfo} from '../api/rest-api';
import {
  ServiceWorkerMessageType,
  TRIGGER_NOTIFICATION_UPDATES_MS,
} from '../services/service-worker-installer';
import {GerritView} from '../services/router/router-model';
import {generateUrl} from '../utils/router-util';
import {
  GerritServiceWorkerState,
  getServiceWorkerState,
  putServiceWorkerState,
} from './service-worker-indexdb';

export class ServiceWorker {
  constructor(private ctx: ServiceWorkerGlobalScope) {}

  latestUpdateTimestampMs = Date.now();

  /**
   * We cannot rely on a state in a service worker, because every time
   * service worker starts or stops, new instance is created. So every time
   * there is new instance we load state from indexdb.
   */
  async init() {
    this.loadState(await getServiceWorkerState());
    this.ctx.addEventListener('message', e => this.onMessage(e));
    this.ctx.addEventListener('notificationclick', e =>
      this.onNotificationClick(e)
    );
  }

  saveState() {
    return putServiceWorkerState({
      latestUpdateTimestampMs: this.latestUpdateTimestampMs,
    });
  }

  loadState(state?: GerritServiceWorkerState) {
    if (!state) return;
    this.latestUpdateTimestampMs = state.latestUpdateTimestampMs;
  }

  // Code based on code sample from
  // https://developer.mozilla.org/en-US/docs/Web/API/Clients/openWindow
  onMessage(e: ExtendableMessageEvent) {
    if (e.data?.type !== ServiceWorkerMessageType.TRIGGER_NOTIFICATIONS) {
      // Only this notification message type exists, but we do not throw error.
      return;
    }
    const showNotification = async () => {
      const account = e.data?.account as AccountDetailInfo | undefined;
      // Message always contains account, but we do not throw error.
      if (!account?._account_id) return;
      const latestAttentionChanges = await this.getChangesToNotify(account);
      // TODO(milutin): Implement handling more than 1 change
      if (latestAttentionChanges && latestAttentionChanges.length > 0) {
        this.showNotification(latestAttentionChanges[0], account);
      }
    };
    e.waitUntil(showNotification());
  }

  onNotificationClick(e: NotificationEvent) {
    e.notification.close();

    const openWindow = async () => {
      const clientsArr = await this.ctx.clients.matchAll({type: 'window'});
      try {
        const url = e.notification.data.url;
        let client = clientsArr.find(c => c.url === url);
        if (!client)
          client = (await this.ctx.clients.openWindow(url)) ?? undefined;
        await client?.focus();
      } catch (e) {
        console.error(`Cannot open window about notified change - ${e}`);
      }
    };

    e.waitUntil(openWindow());
  }

  showNotification(change: ParsedChangeInfo, account: AccountDetailInfo) {
    const body = getReason(undefined, account, change);
    const changeUrl = generateUrl({
      view: GerritView.CHANGE,
      changeNum: change._number,
      project: change.project,
      usp: 'service-worker-notification',
    });
    // We are adding origin because each notification can have different origin
    // User can have different service workers for different origins/hosts.
    // TODO(milutin): Check if this works properly with getBaseUrl()
    const data = {url: `${self.location.origin}${changeUrl}`};

    // TODO(milutin): Add gerrit host icon
    this.ctx.registration.showNotification(change.subject, {body, data});
  }

  async getChangesToNotify(account: AccountDetailInfo) {
    // We throttle polling, since there can be many clients triggerring
    // always only one service worker.
    const durationFromLatestUpdateMS =
      Date.now() - this.latestUpdateTimestampMs;
    if (durationFromLatestUpdateMS < TRIGGER_NOTIFICATION_UPDATES_MS) {
      return [];
    }
    const prevLatestUpdateTimestampMs = this.latestUpdateTimestampMs;
    this.latestUpdateTimestampMs = Date.now();
    await this.saveState();
    const changes = await this.getLatestAttentionSetChanges();
    const latestAttentionChanges = filterAttentionChangesAfter(
      changes,
      account,
      prevLatestUpdateTimestampMs
    );
    return latestAttentionChanges;
  }

  async getLatestAttentionSetChanges(): Promise<ParsedChangeInfo[]> {
    // TODO(milutin): Implement more generic query builder
    const response = await fetch(
      '/changes/?O=1000081&S=0&n=25&q=attention%3Aself'
    );
    const payload = await readResponsePayload(response);
    const changes = payload.parsed as unknown as ParsedChangeInfo[] | undefined;
    return changes ?? [];
  }
}
