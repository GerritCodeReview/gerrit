/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import {waitUntil} from '../../test/test-utils';
import {
  SyntaxWorkerMessage,
  SyntaxWorkerResult,
} from '../../types/syntax-worker-api';
import {HighlightService} from './highlight-service';

class FakeWorker implements Worker {
  messages: SyntaxWorkerMessage[] = [];

  constructor(private readonly autoRespond = true) {}

  postMessage(message: SyntaxWorkerMessage) {
    this.messages.push(message);
    if (this.autoRespond) this.sendResult({ranges: []});
  }

  sendResult(result: SyntaxWorkerResult) {
    if (this.onmessage)
      this.onmessage({data: result} as MessageEvent<SyntaxWorkerResult>);
  }

  onmessage: ((e: MessageEvent<SyntaxWorkerResult>) => any) | null = null;

  onmessageerror = null;

  onerror = null;

  terminate(): void {
    throw new Error('Method not implemented.');
  }

  addEventListener(): void {
    throw new Error('Method not implemented.');
  }

  removeEventListener(): void {
    throw new Error('Method not implemented.');
  }

  dispatchEvent(): boolean {
    throw new Error('Method not implemented.');
  }
}

export class MockHighlightService extends HighlightService {
  idle = this.poolIdle as Set<FakeWorker>;

  busy = this.poolBusy as Set<FakeWorker>;

  override createWorker(): Worker {
    return new FakeWorker();
  }

  countAllMessages() {
    let count = 0;
    for (const worker of [...this.idle, ...this.busy]) {
      count += worker.messages.length;
    }
    return count;
  }

  sendToAll(result: SyntaxWorkerResult) {
    for (const worker of this.busy) {
      worker.sendResult(result);
    }
  }
}

export class MockHighlightServiceManual extends MockHighlightService {
  override createWorker(): Worker {
    return new FakeWorker(false);
  }
}

suite('highlight-service tests', () => {
  let service: MockHighlightServiceManual;

  setup(() => {
    service = new MockHighlightServiceManual();
  });

  test('initial state', () => {
    assert.equal(service.poolBusy.size, 3);
    assert.equal(service.poolIdle.size, 0);
    assert.equal(service.queueForResult.size, 0);
    assert.equal(service.queueForWorker.length, 0);
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
    assert.equal(service.queueForWorker.length, 0);

    service.sendToAll({ranges: []});
    assert.equal(service.countAllMessages(), 4);
    const ranges = await p;
    assert.equal(ranges.length, 0);

    await waitUntil(() => service.queueForResult.size === 0);
    assert.equal(service.poolBusy.size, 0);
    assert.equal(service.poolIdle.size, 3);
    assert.equal(service.queueForResult.size, 0);
    assert.equal(service.queueForWorker.length, 0);
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
    assert.equal(service.queueForWorker.length, 2);

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
    assert.equal(service.queueForWorker.length, 0);

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
    assert.equal(service.queueForWorker.length, 0);
  });
});
