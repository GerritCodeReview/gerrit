/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * We cannot import the worker script from cdn directly, because that is
 * creating cross-origin issues. Instead we have to create a worker script on
 * the fly and pull the actual worker via `importScripts()`. Apparently that
 * is a well established pattern.
 */
function wrapUrl(url: string) {
  const content = `importScripts("${url}");`;
  return URL.createObjectURL(new Blob([content], {type: 'text/javascript'}));
}

export function createWorker(workerUrl: string): Worker {
  if (!workerUrl.startsWith('http'))
    throw new Error(`Worker URL '${workerUrl}' does not start with 'http'.`);
  return new Worker(wrapUrl(workerUrl));
}

export function registerServiceWorker(workerUrl: string) {
  return window.navigator.serviceWorker.register(workerUrl);
}

export function importScript(scope: WorkerGlobalScope, url: string): void {
  scope.importScripts(url);
}
