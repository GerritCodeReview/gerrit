/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  HljsHighlight,
  HljsInit,
  HljsWorkerResult,
  SyntaxLayerRange,
} from '../../types/worker-api';
import {hljsWorkerUrl} from '../../utils/worker-util';
import {Finalizable} from '../registry';

const hljsUrl = `${window.STATIC_RESOURCE_PATH}/bower_components/highlightjs/highlight.min.js`;

/**
 * Service for syntax highlighting. Maintains some HighlightJS workers doing
 * their job in the background.
 */
export class HighlightService implements Finalizable {
  private poolIdle: Set<Worker> = new Set();

  private poolBusy: Set<Worker> = new Set();

  private queueForWorker: Array<() => void> = [];

  private queueForResult: Map<Worker, (r: SyntaxLayerRange[][]) => void> =
    new Map();

  constructor() {
    for (let i = 0; i < 3; i++) {
      const worker = new Worker(hljsWorkerUrl);
      this.poolBusy.add(worker);
      worker.onmessage = (e: MessageEvent<HljsWorkerResult>) => {
        this.handleResult(worker, e.data);
      };
      const initMsg: HljsInit = {url: hljsUrl};
      console.log(`HighlightService init ${i}`);
      worker.postMessage(initMsg);
    }
  }

  private moveIdleToBusy() {
    const worker = this.poolIdle.values().next().value;
    this.poolIdle.delete(worker);
    this.poolBusy.add(worker);
    return worker;
  }

  private moveBusyToIdle(worker: Worker) {
    this.poolBusy.delete(worker);
    this.poolIdle.add(worker);
    const resolver = this.queueForWorker.shift();
    if (resolver) {
      console.log('HighlightService notified task waiting for worker');
      resolver();
    }
  }

  private async requestWorker(): Promise<Worker> {
    if (this.poolIdle.size > 0) {
      const worker = this.moveIdleToBusy();
      return Promise.resolve(worker);
    }
    console.log(
      `HighlightService queueing for worker ${this.queueForWorker.length + 1}`
    );
    await new Promise<void>(r => this.queueForWorker.push(r));
    return this.requestWorker();
  }

  private handleResult(worker: Worker, result: HljsWorkerResult) {
    this.moveBusyToIdle(worker);
    if (result.error) console.error(`hljs failed: ${result.error}`);
    const resolver = this.queueForResult.get(worker)!;
    if (resolver) resolver(result.ranges ?? []);
    console.log(
      `HighlightService handleResult got ${result.ranges?.length} lines back`
    );
  }

  async highlight(
    language: string,
    code: string
  ): Promise<SyntaxLayerRange[][]> {
    const worker = await this.requestWorker();
    const message: HljsHighlight = {language, code};
    const promise = new Promise<SyntaxLayerRange[][]>(r =>
      this.queueForResult.set(worker, r)
    );
    console.log(
      `HighlightService requests highlight ${language} ${code.substring(0, 10)}`
    );
    worker.postMessage(message);
    const ranges = await promise;
    console.log(`HighlightService returns ranges ${ranges.length}`);
    return ranges;
  }

  finalize() {
    this.queueForResult.clear();
    this.queueForWorker.length = 0;
  }
}
