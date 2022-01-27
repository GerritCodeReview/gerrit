/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {HighlightJS} from '../types/types';
import {
  HljsWorkerMessage,
  HljsWorkerResult,
  isHighlight,
  isInit,
} from '../types/worker-api';
import {highlightedStringToRanges} from '../utils/hljs-util';

// Keep this entry point file free of exports.

interface HljsWorkerContext {
  hljs?: HighlightJS;
}

const ctx: Worker & HljsWorkerContext = self as any;

ctx.onmessage = function (e: MessageEvent<HljsWorkerMessage>) {
  try {
    const message = e.data;
    if (isInit(message)) {
      worker.init(message.url);
      const result: HljsWorkerResult = {ranges: []};
      ctx.postMessage(result);
    }
    if (isHighlight(message)) {
      const ranges = worker.highlight(message.language, message.code);
      const result: HljsWorkerResult = {ranges};
      ctx.postMessage(result);
    }
  } catch (err: any) {
    const result: HljsWorkerResult = {
      error: err?.message ?? 'hljs worker error',
      ranges: [],
    };
    ctx.postMessage(result);
  }
};

class HljsWorker {
  private hljs?: HighlightJS;

  init(url: string) {
    const startMs = Date.now();
    importScripts(url);
    if (!ctx.hljs) throw new Error('hljs not available after import');
    this.hljs = ctx.hljs;
    this.hljs.configure({classPrefix: ''});
    console.log(
      `hljs-worker: ${Date.now()} ${Date.now() - startMs} configured`
    );
  }

  highlight(language: string, code: string) {
    const startMs = Date.now();
    if (!this.hljs) throw new Error('worker not initialized');
    const highlight = this.hljs.highlight(language, code, true);
    const ranges = highlightedStringToRanges(highlight.value);
    console.log(`hljs-worker: ${Date.now()} ${Date.now() - startMs} highlit`);
    return ranges;
  }
}

const worker = new HljsWorker();
