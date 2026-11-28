/**
 * @license
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
import {HighlightJS} from '../types/types';
import {SyntaxWorkerMessage} from '../types/syntax-worker-api';
import {importScript} from '../utils/worker-util';
import {SyntaxWorker} from './syntax-worker';

const ctx = self as DedicatedWorkerGlobalScope & {hljs?: HighlightJS};
const worker = new SyntaxWorker(url => {
  importScript(ctx, url);
  return ctx.hljs;
});

ctx.onmessage = (event: MessageEvent<SyntaxWorkerMessage>) => {
  const result = worker.handleMessage(event.data);
  if (result) ctx.postMessage(result);
};
