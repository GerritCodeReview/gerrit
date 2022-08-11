/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ServiceWorker} from './service-worker-class';

/**
 * `self` is for a worker what `window` is for the web app. It is called
 * the `ServiceWorkerGlobalScope`, see
 * https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope
 */
const ctx = self as {} as ServiceWorkerGlobalScope;

/** Singleton instance */
const serviceWorker = new ServiceWorker(ctx);
serviceWorker.init();
