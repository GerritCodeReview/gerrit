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

// This is an entry point file of a bundle. Keep free of exports!

/**
 * This is a web worker for calling the HighlightJS library for syntax
 * highlighting. Files can be large and highlighting does not require
 * the `document` or the `DOM`, so it is a perfect fit for a web worker.
 *
 * This file is a just a hub hooking into the web worker API. The message
 * events for communicating with the main app are defined in the file
 * `types/worker-api.ts`. And the `meat` of the computation is done in the
 * file `hljs-util.ts`.
 */

type Context = Worker & {hljs?: HighlightJS};
const ctx: Context = self as unknown as Context;

/**
 * We are encapsulating the web worker API here, so this is the only place
 * where you need to know about it and the MessageEvents in this file.
 */
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
  } catch (err) {
    let error = 'hljs worker error';
    if (err instanceof Error) error = err.message;
    const result: HljsWorkerResult = {error, ranges: []};
    ctx.postMessage(result);
  }
};

class HljsWorker {
  private hljs?: HighlightJS;

  init(url: string) {
    importScripts(url);
    if (!ctx.hljs) throw new Error('hljs not available after import');
    this.hljs = ctx.hljs;
    this.hljs.configure({classPrefix: ''});
  }

  highlight(language: string, code: string) {
    if (!this.hljs) throw new Error('worker not initialized');
    const highlight = this.hljs.highlight(language, code, true);
    return highlightedStringToRanges(highlight.value);
  }
}

/** Singleton instance being referenced in `onmessage` function above. */
const worker = new HljsWorker();
