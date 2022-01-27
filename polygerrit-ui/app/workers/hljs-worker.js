/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
onmessage = function(e) {
  console.log(`hljs-worker: message received ${JSON.stringify(e.data)}`);
  importScripts(e.data.url);
  self.hljs.configure({classPrefix: 'gr-diff gr-syntax gr-syntax-'});
  const result = self.hljs.highlight('typescript', e.data.code);
  postMessage(`Result from hljs worker: ${result.value}`);
};
