/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

function wrapUrl(url: string) {
  const content = `importScripts("${url}");`;
  return URL.createObjectURL(new Blob([content], {type: 'text/javascript'}));
}

const testWorkerUrl = wrapUrl(
  `${window.STATIC_RESOURCE_PATH}/workers/test-worker.js`
);

const hljsWorkerUrl = wrapUrl(
  `${window.STATIC_RESOURCE_PATH}/workers/hljs-worker.js`
);

export function createTestWorker() {
  const worker = new Worker(testWorkerUrl);
  worker.onmessage = function (e) {
    console.log(`Message received from test-worker ${JSON.stringify(e.data)}`);
  };
  worker.postMessage('Hello test-worker from the main script.');
}

export function createHljsWorker(code: string) {
  const worker = new Worker(hljsWorkerUrl);
  const url = `${window.STATIC_RESOURCE_PATH}/bower_components/highlightjs/highlight.min.js`;
  let resolver: (s: string) => void;
  const promise = new Promise<string>(resolve => (resolver = resolve));
  worker.onmessage = function (e) {
    console.log(`Message received from hljs-worker ${JSON.stringify(e.data)}`);
    resolver(e.data);
  };
  console.log(`createHljsWorker postMessage ${code.length}`);
  worker.postMessage({url, code});
  return promise;
}
