/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {define} from '../../models/dependency';
import {
  SyntaxWorkerRequest,
  SyntaxWorkerInit,
  SyntaxWorkerResult,
  SyntaxWorkerMessageType,
  SyntaxLayerLine,
} from '../../types/syntax-worker-api';
import {Finalizable} from '../../types/types';
import {prependOrigin} from '../../utils/url-util';
import {createWorker} from '../../utils/worker-util';
import {ReportingService} from '../gr-reporting/gr-reporting';

const hljsLibUrl = `${
  window.STATIC_RESOURCE_PATH ?? ''
}/bower_components/highlightjs/highlight.min.js`;

const syntaxWorkerUrl = `${
  window.STATIC_RESOURCE_PATH ?? ''
}/workers/syntax-worker.js`;

/**
 * It is unlikely that a pool size greater than 3 will gain anything, because
 * the app also needs the resources to process the results.
 */
const WORKER_POOL_SIZE = 3;

/**
 * Safe guard for not killing the browser.
 */
export const CODE_MAX_LINES = 20 * 1000;

/**
 * Safe guard for not killing the browser. Maximum in number of chars.
 */
const CODE_MAX_LENGTH = 25 * CODE_MAX_LINES;

export const highlightServiceToken =
  define<HighlightService>('highlight-service');
/**
 * Service for syntax highlighting. Maintains some HighlightJS workers doing
 * their job in the background.
 */
export class HighlightService implements Finalizable {
  // visible for testing
  poolIdle: Set<Worker | undefined> = new Set();

  // visible for testing
  poolBusy: Set<Worker | undefined> = new Set();

  // visible for testing
  /** Queue for waiting that a worker becomes available. */
  queueForWorker: Array<() => void> = [];

  // visible for testing
  /** Queue for waiting on the results of a worker. */
  queueForResult: Map<Worker, (r: SyntaxLayerLine[]) => void> = new Map();

  constructor(readonly reporting: ReportingService) {
    for (let i = 0; i < WORKER_POOL_SIZE; i++) {
      this.addWorker();
    }
  }

  /** Allows tests to produce fake workers. */
  protected createWorker() {
    return createWorker(prependOrigin(syntaxWorkerUrl));
  }

  /** Creates, initializes and then moves a worker to the idle pool. */
  private addWorker() {
    const worker = this.createWorker();
    // Will move to the idle pool after being initialized.
    this.poolBusy.add(worker);
    worker.onmessage = (e: MessageEvent<SyntaxWorkerResult>) => {
      this.handleResult(worker, e.data);
    };
    const initMsg: SyntaxWorkerInit = {
      type: SyntaxWorkerMessageType.INIT,
      url: prependOrigin(hljsLibUrl),
    };
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
  private async requestWorker(): Promise<Worker | undefined> {
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
  private handleResult(worker: Worker, result: SyntaxWorkerResult) {
    this.moveBusyToIdle(worker);
    if (result.error) {
      this.reporting.error(
        'Diff Syntax Layer',
        new Error(`syntax worker failed: ${result.error}`)
      );
    }
    const resolver = this.queueForResult.get(worker);
    this.queueForResult.delete(worker);
    if (resolver) resolver(result.ranges ?? []);
  }

  async highlight(
    language?: string,
    code?: string
  ): Promise<SyntaxLayerLine[]> {
    if (!language || !code) return [];
    if (code.length > CODE_MAX_LENGTH) return [];
    const worker = await this.requestWorker();
    if (!worker) return [];
    const message: SyntaxWorkerRequest = {
      type: SyntaxWorkerMessageType.REQUEST,
      language,
      code,
    };
    const promise = new Promise<SyntaxLayerLine[]>(r => {
      this.queueForResult.set(worker, r);
    });
    worker.postMessage(message);
    return await promise;
  }

  finalize() {
    for (const worker of this.poolIdle) {
      worker?.terminate();
    }
    this.poolIdle.clear();
    for (const worker of this.poolBusy) {
      worker?.terminate();
    }
    this.poolBusy.clear();
    this.queueForResult.clear();
    this.queueForWorker.length = 0;
  }
}
