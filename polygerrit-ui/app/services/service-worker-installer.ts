/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {FlagsService, KnownExperimentId} from './flags/flags';
import {registerServiceWorker} from '../utils/worker-util';
import {ServiceWorkerMessageType} from '../types/service-worker-api';

const TRIGGER_NOTIFICATION_UPDATES_MS = 5 * 60 * 1000;
export class ServiceWorkerInstaller {
  initialized = false;

  constructor(private readonly flagsService: FlagsService) {}

  async init() {
    if (this.initialized) return;
    if (!this.flagsService.isEnabled(KnownExperimentId.PUSH_NOTIFICATIONS)) {
      return;
    }
    if (!('serviceWorker' in navigator)) {
      console.error('Service worker API not available');
      return;
    }
    await registerServiceWorker('/service-worker.js');
    const permission = await this.requestNotificationPermission();
    if (this.isPermitted(permission)) this.startTriggerTimer();
    this.initialized = true;
  }

  /**
   * Every 5 minutes, we trigger service-worker to get
   * latest updates in attention set and service-worker will create notifications.
   */
  startTriggerTimer() {
    window.setTimeout(() => {
      this.startTriggerTimer();
      navigator.serviceWorker.controller?.postMessage({
        type: ServiceWorkerMessageType.TRIGGER_NOTIFICATIONS,
      });
    }, TRIGGER_NOTIFICATION_UPDATES_MS);
  }

  async requestNotificationPermission() {
    return await window.Notification.requestPermission();
  }

  isPermitted(permission: NotificationPermission) {
    return permission === 'granted';
  }
}
