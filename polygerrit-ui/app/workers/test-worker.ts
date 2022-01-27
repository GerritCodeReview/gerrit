/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
const ctx: Worker = self as any;

ctx.onmessage = function (e: MessageEvent) {
  console.log(`test-worker: message received ${JSON.stringify(e.data)}`);
  ctx.postMessage('Hello from the test-worker!');
};
