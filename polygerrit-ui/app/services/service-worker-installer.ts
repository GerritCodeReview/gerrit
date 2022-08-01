/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {FlagsService, KnownExperimentId} from './flags/flags';
import {registerServiceWorker} from '../utils/worker-util';
import {UserModel} from '../models/user/user-model';
import {AccountDetailInfo} from '../api/rest-api';

/** Type of incoming messages for ServiceWorker. */
export enum ServiceWorkerMessageType {
  TRIGGER_NOTIFICATIONS = 'TRIGGER_NOTIFICATIONS',
}

export const TRIGGER_NOTIFICATION_UPDATES_MS = 5 * 60 * 1000;

export class ServiceWorkerInstaller {
  initialized = false;

  account?: AccountDetailInfo;

  constructor(
    private readonly flagsService: FlagsService,
    private readonly userModel: UserModel
  ) {
    this.userModel.account$.subscribe(acc => (this.account = acc));
  }

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
    const permission = await Notification.requestPermission();
    if (this.isPermitted(permission)) this.startTriggerTimer();
    this.initialized = true;
  }

  /**
   * Every 5 minutes, we trigger service-worker to get
   * latest updates in attention set and service-worker will create
   * notifications.
   */
  startTriggerTimer() {
    setTimeout(() => {
      this.startTriggerTimer();
      navigator.serviceWorker.controller?.postMessage({
        type: ServiceWorkerMessageType.TRIGGER_NOTIFICATIONS,
        account: this.account,
      });
    }, TRIGGER_NOTIFICATION_UPDATES_MS);
  }

  isPermitted(permission: NotificationPermission) {
    return permission === 'granted';
  }
}
