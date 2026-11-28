/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../../test/common-test-setup';
import {isRequest} from '../../types/syntax-worker-api';
import {waitUntil} from '../../test/test-utils';
import {grReportingMock} from '../gr-reporting/gr-reporting_mock';
import {MockHighlightServiceManual} from './highlight-service-mock';

suite('highlight-service tests', () => {
  let service: MockHighlightServiceManual;

  setup(() => {
    service = new MockHighlightServiceManual(grReportingMock);
  });

  test('initial state', () => {
    assert.equal(service.poolBusy.size, 3);
    assert.equal(service.poolIdle.size, 0);
    assert.equal(service.queueForResult.size, 0);
    assert.equal(service.queueForWorker.size, 0);
  });

  test('initialized workers move to idle pool', () => {
    service.sendToAll({});
    assert.equal(service.countAllMessages(), 3);

    assert.equal(service.poolBusy.size, 0);
    assert.equal(service.poolIdle.size, 3);
  });

  test('highlight 1', async () => {
    service.sendToAll({});
    assert.equal(service.countAllMessages(), 3);

    const p = service.highlight('asdf', 'qwer');
    assert.equal(service.poolBusy.size, 1);
    assert.equal(service.poolIdle.size, 2);
    await waitUntil(() => service.queueForResult.size > 0);
    assert.equal(service.queueForResult.size, 1);
    assert.equal(service.queueForWorker.size, 0);

    service.sendToAll({ranges: []});
    assert.equal(service.countAllMessages(), 4);
    const ranges = await p;
    assert.equal(ranges.length, 0);

    await waitUntil(() => service.queueForResult.size === 0);
    assert.equal(service.poolBusy.size, 0);
    assert.equal(service.poolIdle.size, 3);
    assert.equal(service.queueForResult.size, 0);
    assert.equal(service.queueForWorker.size, 0);
  });

  test('only requests plain fallback when the caller asks for it', async () => {
    service.sendToAll({});
    const strict = service.highlight('unknown', 'diff');
    const fallback = service.highlight('unknown', 'comment', {
      ignoreUnknownLanguage: true,
    });
    await waitUntil(() => service.queueForResult.size === 2);
    const requests = Array.from(
      service.busy,
      worker => worker.messages[worker.messages.length - 1]
    ).filter(isRequest);
    assert.deepEqual(
      requests.map(r => [r.code, !!r.ignoreUnknownLanguage]),
      [
        ['diff', false],
        ['comment', true],
      ]
    );
    service.sendToAll({ranges: []});
    await Promise.all([strict, fallback]);
  });

  test('releases a reserved worker if the request is aborted before dispatch', async () => {
    service.sendToAll({});
    const controller = new AbortController();
    const canceled = service.highlight('js', 'old', {
      signal: controller.signal,
    });
    controller.abort();
    assert.deepEqual(await canceled, []);
    assert.equal(service.poolIdle.size, 3);
    assert.equal(service.countAllMessages(), 3);
  });

  test('finalize resolves waiting and dispatched requests', async () => {
    service.sendToAll({});
    const pending = ['one', 'two', 'three', 'four'].map(code =>
      service.highlight('js', code)
    );
    await waitUntil(() => service.queueForResult.size === 3);
    service.finalize();
    assert.deepEqual(await Promise.all(pending), [[], [], [], []]);
    assert.equal(service.queueForWorker.size, 0);
  });

  test('highlight 5', async () => {
    service.sendToAll({});
    assert.equal(service.countAllMessages(), 3);

    const p1 = service.highlight('asdf1', 'qwer1');
    const p2 = service.highlight('asdf2', 'qwer2');
    const p3 = service.highlight('asdf3', 'qwer3');
    const p4 = service.highlight('asdf4', 'qwer4');
    const p5 = service.highlight('asdf5', 'qwer5');

    assert.equal(service.poolBusy.size, 3);
    assert.equal(service.poolIdle.size, 0);
    await waitUntil(() => service.queueForResult.size > 0);
    assert.equal(service.queueForResult.size, 3);
    assert.equal(service.queueForWorker.size, 2);

    service.sendToAll({ranges: []});
    assert.equal(service.countAllMessages(), 6);
    const ranges1 = await p1;
    const ranges2 = await p2;
    const ranges3 = await p3;
    assert.equal(ranges1.length, 0);
    assert.equal(ranges2.length, 0);
    assert.equal(ranges3.length, 0);

    await waitUntil(() => service.queueForResult.size === 2);
    assert.equal(service.poolBusy.size, 2);
    assert.equal(service.poolIdle.size, 1);
    assert.equal(service.queueForResult.size, 2);
    assert.equal(service.queueForWorker.size, 0);

    service.sendToAll({ranges: []});
    assert.equal(service.countAllMessages(), 8);
    const ranges4 = await p4;
    const ranges5 = await p5;
    assert.equal(ranges4.length, 0);
    assert.equal(ranges5.length, 0);

    await waitUntil(() => service.queueForResult.size === 0);
    assert.equal(service.poolBusy.size, 0);
    assert.equal(service.poolIdle.size, 3);
    assert.equal(service.queueForResult.size, 0);
    assert.equal(service.queueForWorker.size, 0);
  });
});
