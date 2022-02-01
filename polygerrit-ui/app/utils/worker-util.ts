/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * We cannot import the worker script from cdn directly, because that is
 * creating cross-origin issues. Instead we have to create a worker script on
 * the fly and pull the actual worker via `importScripts()`. Apparently that
 * is a well established pattern. :shrug
 */
function wrapUrl(url: string) {
  const content = `importScripts("${url}");`;
  return URL.createObjectURL(new Blob([content], {type: 'text/javascript'}));
}

const testWorkerUrl = wrapUrl(
  `${window.STATIC_RESOURCE_PATH}/workers/test-worker.js`
);

export const hljsWorkerUrl = wrapUrl(
  `${window.STATIC_RESOURCE_PATH}/workers/hljs-worker.js`
);

export function createTestWorker() {
  const worker = new Worker(testWorkerUrl);
  worker.onmessage = function (e) {
    console.log(`Message received from test-worker ${JSON.stringify(e.data)}`);
  };
  worker.postMessage('Hello test-worker from the main script.');
}
