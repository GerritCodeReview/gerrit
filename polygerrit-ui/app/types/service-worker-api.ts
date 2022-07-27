/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * This file defines the API of service-worker, which is now used
 * mostly for notifications, but can be used in future for preloading resources.
 */

/** Type of incoming messages for ServiceWorker. */
export enum ServiceWorkerMessageType {
  TRIGGER_NOTIFICATIONS = 'TRIGGER_NOTIFICATIONS',
}

export const TRIGGER_NOTIFICATION_UPDATES_MS = 5 * 60 * 1000;
