/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {HighlightJS} from '../types/types';
import {
  SyntaxWorkerMessage,
  SyntaxWorkerResult,
  isRequest,
  isInit,
} from '../types/syntax-worker-api';
import {highlightedStringToRanges} from '../utils/syntax-util';
import {importScript} from '../utils/worker-util';

// This is an entry point file of a bundle. Keep free of exports!

/**
 * This is a web worker for calling the HighlightJS library for syntax
 * highlighting. Files can be large and highlighting does not require
 * the `document` or the `DOM`, so it is a perfect fit for a web worker.
 *
 * This file is a just a hub hooking into the web worker API. The message
 * events for communicating with the main app are defined in the file
 * `types/worker-api.ts`. And the `meat` of the computation is done in the
 * file `syntax-util.ts`.
 */

/**
 * `self` is for a worker what `window` is for the web app. It is called
 * the `DedicatedWorkerGlobalScope`, see
 * https://developer.mozilla.org/en-US/docs/Web/API/DedicatedWorkerGlobalScope
 *
 * Once imported the HighlightJS lib exposes its functionality via the global
 * `hljs` variable.
 */
const ctx = self as DedicatedWorkerGlobalScope & {hljs?: HighlightJS};

/**
 * We are encapsulating the web worker API here, so this is the only place
 * where you need to know about it and the MessageEvents in this file.
 */
ctx.onmessage = function (e: MessageEvent<SyntaxWorkerMessage>) {
  try {
    const message = e.data;
    if (isInit(message)) {
      worker.init(message.url);
      const result: SyntaxWorkerResult = {ranges: []};
      ctx.postMessage(result);
    }
    if (isRequest(message)) {
      const ranges = worker.highlightCode(message.language, message.code);
      const result: SyntaxWorkerResult = {ranges};
      ctx.postMessage(result);
    }
  } catch (err) {
    let error = 'syntax worker error';
    if (err instanceof Error) error = err.message;
    const result: SyntaxWorkerResult = {error, ranges: []};
    ctx.postMessage(result);
  }
};

class SyntaxWorker {
  private highlightJsLib?: HighlightJS;

  init(highlightJsLibUrl: string) {
    importScript(ctx, highlightJsLibUrl);
    if (!ctx.hljs) {
      throw new Error('HighlightJS lib not available after import');
    }
    this.highlightJsLib = ctx.hljs;
    this.highlightJsLib.configure({classPrefix: ''});
  }

  highlightCode(language: string, code: string) {
    if (!this.highlightJsLib) throw new Error('worker not initialized');
    const highlighted = this.highlightJsLib.highlight(code, {
      language,
      ignoreIllegals: true,
    });
    return highlightedStringToRanges(highlighted.value);
  }
}

/** Singleton instance being referenced in `onmessage` function above. */
const worker = new SyntaxWorker();
