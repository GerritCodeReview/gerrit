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
import {TRIGGER_NOTIFICATION_UPDATES_MS} from '../services/service-worker-installer';
import {generateChangeUrl} from '../utils/url-util';
import {GerritView} from '../services/router/router-model';

export class ServiceWorker {
  constructor(private ctx: ServiceWorkerGlobalScope) {}

  latestUpdateTimestampMs?: number;

  showNotification(change: ParsedChangeInfo, account: AccountDetailInfo) {
    const body = getReason(undefined, account, change);
    const changeUrl = generateChangeUrl({
      view: GerritView.CHANGE,
      changeNum: change._number,
      project: change.project,
      usp: 'service-worker-notification',
    });
    const data = {url: `${self.location.origin}${changeUrl}`};

    // TODO(milutin): Add gerrit host icon
    this.ctx.registration.showNotification(change.subject, {body, data});
  }

  async getChangesToNotify(account: AccountDetailInfo) {
    // We throttle polling, since there can be many clients triggerring
    // always only one service worker.
    if (this.latestUpdateTimestampMs) {
      const durationFromLatestUpdateMS =
        Date.now() - this.latestUpdateTimestampMs;
      if (durationFromLatestUpdateMS < TRIGGER_NOTIFICATION_UPDATES_MS) {
        return [];
      }
    }
    const changes = await this.getLatestAttentionSetChanges();
    const latestAttentionChanges = filterAttentionChangesAfter(
      changes,
      account,
      this.latestUpdateTimestampMs
    );
    this.latestUpdateTimestampMs = Date.now();
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
