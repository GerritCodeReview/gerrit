/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
onmessage = function(e) {
  console.log(`test-worker: message received ${JSON.stringify(e.data)}`);
  postMessage('Hello from the test-worker!');
};
