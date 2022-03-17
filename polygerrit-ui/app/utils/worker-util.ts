/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ReportingService} from '../services/gr-reporting/gr-reporting';
import {initErrorReporter} from '../services/gr-reporting/gr-reporting_impl';

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

export function createWorker(
  workerUrl: string,
  reportingService?: ReportingService
): Worker {
  const worker = new Worker(wrapUrl(workerUrl));
  if (reportingService) initErrorReporter(reportingService, worker);
  return worker;
}

export function importScript(scope: WorkerGlobalScope, url: string): void {
  scope.importScripts(url);
}
