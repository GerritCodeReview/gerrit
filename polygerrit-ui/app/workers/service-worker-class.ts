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
import {
  getServiceWorkerState,
  putServiceWorkerState,
} from './service-worker-indexdb';
import {createDashboardUrl} from '../models/views/dashboard';
import {createChangeUrl} from '../models/views/change';
import {noAwait} from '../utils/async-util';

export class ServiceWorker {
  constructor(
    /* private but used in test */ public ctx: ServiceWorkerGlobalScope
  ) {}

  // private but used in test
  latestUpdateTimestampMs = Date.now();

  allowBrowserNotificationsPreference = false;

  /**
   * We cannot rely on a state in a service worker, because every time
   * service worker starts or stops, new instance is created. So every time
   * there is new instance we load state from indexdb.
   */
  async init() {
    await this.loadState();
    this.ctx.addEventListener('message', e => this.onMessage(e));
    this.ctx.addEventListener('notificationclick', e =>
      this.onNotificationClick(e)
    );
  }

  // private but used in test
  saveState() {
    return putServiceWorkerState({
      latestUpdateTimestampMs: this.latestUpdateTimestampMs,
      allowBrowserNotificationsPreference:
        this.allowBrowserNotificationsPreference,
    });
  }

  private async loadState() {
    const state = await getServiceWorkerState();
    if (state) {
      this.latestUpdateTimestampMs = state.latestUpdateTimestampMs;
      this.allowBrowserNotificationsPreference =
        state.allowBrowserNotificationsPreference;
    }
  }

  private onMessage(e: ExtendableMessageEvent) {
    if (e.data?.type === ServiceWorkerMessageType.TRIGGER_NOTIFICATIONS) {
      e.waitUntil(
        this.showLatestAttentionChangeNotification(
          e.data?.account as AccountDetailInfo | undefined
        )
      );
    } else if (
      e.data?.type === ServiceWorkerMessageType.USER_PREFERENCE_CHANGE
    ) {
      e.waitUntil(
        this.allowBrowserNotificationsPreferenceChanged(
          e.data?.allowBrowserNotificationsPreference as boolean
        )
      );
    }
  }

  private onNotificationClick(e: NotificationEvent) {
    e.notification.close();
    e.waitUntil(this.openWindow(e.notification.data.url));
  }

  async allowBrowserNotificationsPreferenceChanged(preference: boolean) {
    this.allowBrowserNotificationsPreference = preference;
    await this.saveState();
  }

  // private but used in test
  async showLatestAttentionChangeNotification(account?: AccountDetailInfo) {
    // Message always contains account, but we do not throw error.
    if (!account?._account_id) return;
    if (!this.allowBrowserNotificationsPreference) return;
    const latestAttentionChanges = await this.getChangesToNotify(account);
    const numOfChangesToNotifyAbout = latestAttentionChanges.length;
    if (numOfChangesToNotifyAbout === 1) {
      this.showNotificationForChange(latestAttentionChanges[0], account);
    } else if (numOfChangesToNotifyAbout > 1) {
      this.showNotificationForDashboard(numOfChangesToNotifyAbout);
    }
  }

  // Code based on code sample from
  // https://developer.mozilla.org/en-US/docs/Web/API/Clients/openWindow
  private async openWindow(url?: string) {
    if (!url) return;
    const clientsArr = await this.ctx.clients.matchAll({type: 'window'});
    try {
      let client = clientsArr.find(c => c.url === url);
      if (!client)
        client = (await this.ctx.clients.openWindow(url)) ?? undefined;
      await client?.focus();
    } catch (e) {
      console.error(`Cannot open window about notified change - ${e}`);
    }
  }

  private showNotificationForChange(
    change: ParsedChangeInfo,
    account: AccountDetailInfo
  ) {
    const body = getReason(undefined, account, change);
    const changeUrl = createChangeUrl({
      change,
      usp: 'service-worker-notification',
    });
    // We are adding origin because each notification can have different origin
    // User can have different service workers for different origins/hosts.
    // TODO(milutin): Check if this works properly with getBaseUrl()
    const data = {url: `${self.location.origin}${changeUrl}`};
    const icon = `${self.location.origin}/favicon.ico`;
    this.ctx.registration.showNotification(change.subject, {
      body,
      data,
      icon,
    });
    this.sendReport('notify about 1 change');
  }

  private showNotificationForDashboard(numOfChangesToNotifyAbout: number) {
    const title = `You are in the attention set for ${numOfChangesToNotifyAbout} changes.`;
    const dashboardUrl = createDashboardUrl({});
    const data = {url: `${self.location.origin}${dashboardUrl}`};
    const icon = `${self.location.origin}/favicon.ico`;
    this.ctx.registration.showNotification(title, {data, icon});
    this.sendReport(`notify about ${numOfChangesToNotifyAbout} changes`);
  }

  // private but used in test
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
    noAwait(this.sendReport('polling'));
    const changes = await this.getLatestAttentionSetChanges();
    const latestAttentionChanges = filterAttentionChangesAfter(
      changes,
      account,
      prevLatestUpdateTimestampMs
    );
    return latestAttentionChanges;
  }

  // private but used in test
  async getLatestAttentionSetChanges(): Promise<ParsedChangeInfo[]> {
    // TODO(milutin): Implement more generic query builder
    const response = await fetch(
      '/changes/?O=1000081&S=0&n=25&q=attention%3Aself'
    );
    const payload = await readResponsePayload(response);
    const changes = payload.parsed as unknown as ParsedChangeInfo[] | undefined;
    return changes ?? [];
  }

  /**
   * Send report event to 1 client (last focused one). The client will use
   * gr-reporting service to send event to metric event collectors.
   */
  async sendReport(eventName: string) {
    const clientsArr = await this.ctx.clients.matchAll({type: 'window'});
    const lastFocusedClient = clientsArr?.[0];
    if (!lastFocusedClient) return;

    lastFocusedClient.postMessage({
      type: ServiceWorkerMessageType.REPORTING,
      eventName,
    });
  }
}
