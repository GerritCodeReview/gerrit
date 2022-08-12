/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Timestamp} from '../api/rest-api';
import '../test/common-test-setup-karma';
import {
  createAccountDetailWithId,
  createParsedChange,
} from '../test/test-data-generators';
import {mockPromise} from '../test/test-utils';
import {ParsedChangeInfo} from '../types/types';
import {parseDate} from '../utils/date-util';
import {ServiceWorker} from './service-worker-class';

suite('service worker class tests', () => {
  let serviceWorker: ServiceWorker;

  setup(() => {
    const moctCtx = {
      registration: {
        showNotification: () => {},
      },
    } as {} as ServiceWorkerGlobalScope;
    serviceWorker = new ServiceWorker(moctCtx);
  });

  test('notify about attention in t2 when time is t3', async () => {
    const t1 = parseDate('2016-01-12 20:20:00' as Timestamp).getTime();
    const t2 = '2016-01-12 20:30:00' as Timestamp;
    const t3 = parseDate('2016-01-12 20:40:00' as Timestamp).getTime();
    serviceWorker.latestUpdateTimestampMs = t1;
    const account = createAccountDetailWithId();
    const change: ParsedChangeInfo = {
      ...createParsedChange(),
      attention_set: {
        [`${account._account_id}`]: {
          account,
          last_update: t2,
        },
      },
    };
    sinon.useFakeTimers(t3);
    sinon
      .stub(serviceWorker, 'getLatestAttentionSetChanges')
      .returns(Promise.resolve([change]));
    const changes = await serviceWorker.getChangesToNotify(account);
    assert.equal(changes[0], change);
  });

  test('check race condition', async () => {
    const promise = mockPromise<ParsedChangeInfo[]>();
    sinon.stub(serviceWorker, 'saveState').returns(Promise.resolve());
    const getLatestAttentionSetChangesStub = sinon
      .stub(serviceWorker, 'getLatestAttentionSetChanges')
      .returns(promise);
    const account = createAccountDetailWithId();
    const t1 = parseDate('2016-01-12 20:20:00' as Timestamp).getTime();
    const t2 = '2016-01-12 20:30:00' as Timestamp;
    serviceWorker.latestUpdateTimestampMs = t1;
    const change: ParsedChangeInfo = {
      ...createParsedChange(),
      attention_set: {
        [`${account._account_id}`]: {
          account,
          last_update: t2,
        },
      },
    };
    serviceWorker.getChangesToNotify(account);
    serviceWorker.getChangesToNotify(account);
    promise.resolve([change]);
    await serviceWorker.getChangesToNotify(account);
    assert.isTrue(getLatestAttentionSetChangesStub.calledOnce);
  });
});
