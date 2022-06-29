/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {FlagsService, KnownExperimentId} from './flags/flags';

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
    await navigator.serviceWorker.register('/service-worker.js');
    await this.requestNotificationPermission();
    this.initialized = true;
  }

  async requestNotificationPermission() {
    const permission = await window.Notification.requestPermission();
    if (permission !== 'granted') {
      throw new Error('Permission not granted for Notification');
    }
  }
}
