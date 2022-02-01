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
 * It is unlikely that a pool size greater than 3 will gain anything, because
 * the app also needs the resources to process the results.
 */
const WORKER_POOL_SIZE = 3;

/**
 * Service for syntax highlighting. Maintains some HighlightJS workers doing
 * their job in the background.
 */
export class HighlightService implements Finalizable {
  private poolIdle: Set<Worker> = new Set();

  private poolBusy: Set<Worker> = new Set();

  /** Queue for waiting that a worker becomes available. */
  private queueForWorker: Array<() => void> = [];

  /** Queue for waiting on the results of a worker. */
  private queueForResult: Map<Worker, (r: SyntaxLayerRange[][]) => void> =
    new Map();

  constructor() {
    for (let i = 0; i < WORKER_POOL_SIZE; i++) {
      this.addWorker();
    }
  }

  /** Creates, initializes and then moves a worker to the idle pool. */
  private addWorker() {
    const worker = new Worker(hljsWorkerUrl);
    this.poolBusy.add(worker);
    worker.onmessage = (e: MessageEvent<HljsWorkerResult>) => {
      this.handleResult(worker, e.data);
    };
    const initMsg: HljsInit = {url: hljsUrl};
    worker.postMessage(initMsg);
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
    if (resolver) resolver();
  }

  /**
   * If there is worker in the idle pool, then return it. Otherwise wait for a
   * worker to become a available.
   */
  private async requestWorker(): Promise<Worker> {
    if (this.poolIdle.size > 0) {
      const worker = this.moveIdleToBusy();
      return Promise.resolve(worker);
    }
    await new Promise<void>(r => this.queueForWorker.push(r));
    return this.requestWorker();
  }

  /**
   * A worker is done with its job. Move it back to the idle pool and notify the
   * resolver that is waiting for the results.
   */
  private handleResult(worker: Worker, result: HljsWorkerResult) {
    this.moveBusyToIdle(worker);
    if (result.error) console.error(`hljs failed: ${result.error}`);
    const resolver = this.queueForResult.get(worker)!;
    if (resolver) resolver(result.ranges ?? []);
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
    worker.postMessage(message);
    return await promise;
  }

  finalize() {
    this.queueForResult.clear();
    this.queueForWorker.length = 0;
  }
}
