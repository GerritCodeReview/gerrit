/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
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

  terminate(): void {}

  addEventListener(): void {}

  removeEventListener(): void {}

  dispatchEvent(): boolean {
    return true;
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
