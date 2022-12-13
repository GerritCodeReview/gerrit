/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {Timestamp} from '../api/rest-api';
import '../test/common-test-setup';
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
    serviceWorker.allowBrowserNotificationsPreference = true;
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

  test('when 2 or more changes, link to dashboard', async () => {
    const t1 = parseDate('2016-01-12 20:20:00' as Timestamp).getTime();
    const t2 = '2016-01-12 20:30:00' as Timestamp;
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
    sinon
      .stub(serviceWorker, 'getLatestAttentionSetChanges')
      .returns(Promise.resolve([change, change]));
    sinon.stub(serviceWorker, 'saveState').returns(Promise.resolve());
    const showNotificationMock = sinon.stub(
      serviceWorker.ctx.registration,
      'showNotification'
    );

    await serviceWorker.showLatestAttentionChangeNotification(account);

    assert.isTrue(showNotificationMock.calledOnce);
    assert.isTrue(
      showNotificationMock.calledWithMatch(
        'You are in the attention set for 2 new changes.'
      )
    );
  });

  test('show notification for 1 change', async () => {
    const t1 = parseDate('2016-01-12 20:20:00' as Timestamp).getTime();
    const t2 = '2016-01-12 20:30:00' as Timestamp;
    serviceWorker.latestUpdateTimestampMs = t1;
    const account = createAccountDetailWithId();
    const subject = 'New change';
    const reason = 'Reason';
    const change = {
      ...createParsedChange(),
      subject,
      attention_set: {
        [`${account._account_id}`]: {
          account,
          last_update: t2,
          reason,
        },
      },
    };
    const showNotificationMock = sinon.stub(
      serviceWorker.ctx.registration,
      'showNotification'
    );
    sinon
      .stub(serviceWorker, 'getLatestAttentionSetChanges')
      .returns(Promise.resolve([change]));
    sinon.stub(serviceWorker, 'saveState').returns(Promise.resolve());

    await serviceWorker.showLatestAttentionChangeNotification(account);

    assert.isTrue(showNotificationMock.calledOnce);
    assert.isTrue(
      showNotificationMock.calledWithMatch(subject, {
        body: reason,
        data: {
          url: 'http://localhost:9876/c/test-project/+/42?usp=service-worker-notification',
        },
      })
    );
    assert.equal(showNotificationMock.firstCall.args?.[1]?.['body'], reason);
    assert.isTrue(
      showNotificationMock.firstCall.args?.[1]?.['data']?.['url'].endsWith(
        'c/test-project/+/42?usp=service-worker-notification'
      )
    );
  });
});
