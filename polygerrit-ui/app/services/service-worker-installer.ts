/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {FlagsService, KnownExperimentId} from './flags/flags';
import {
  areNotificationsEnabled,
  registerServiceWorker,
} from '../utils/worker-util';
import {UserModel} from '../models/user/user-model';
import {AccountDetailInfo} from '../api/rest-api';
import {until} from '../utils/async-util';
import {LifeCycle} from '../constants/reporting';
import {ReportingService} from './gr-reporting/gr-reporting';

/** Type of incoming messages for ServiceWorker. */
export enum ServiceWorkerMessageType {
  TRIGGER_NOTIFICATIONS = 'TRIGGER_NOTIFICATIONS',
  USER_PREFERENCE_CHANGE = 'USER_PREFERENCE_CHANGE',
  REPORTING = 'REPORTING',
}

export const TRIGGER_NOTIFICATION_UPDATES_MS = 5 * 60 * 1000;

export class ServiceWorkerInstaller {
  initialized = false;

  account?: AccountDetailInfo;

  allowBrowserNotificationsPreference?: boolean;

  constructor(
    private readonly flagsService: FlagsService,
    private readonly reportingService: ReportingService,
    private readonly userModel: UserModel
  ) {
    if (!this.flagsService.isEnabled(KnownExperimentId.PUSH_NOTIFICATIONS)) {
      return;
    }
    this.userModel.account$.subscribe(acc => (this.account = acc));
    this.userModel.preferences$.subscribe(prefs => {
      if (
        this.allowBrowserNotificationsPreference !==
        prefs.allow_browser_notifications
      ) {
        this.allowBrowserNotificationsPreference =
          prefs.allow_browser_notifications;
        navigator.serviceWorker.controller?.postMessage({
          type: ServiceWorkerMessageType.USER_PREFERENCE_CHANGE,
          allowBrowserNotificationsPreference:
            this.allowBrowserNotificationsPreference,
        });
      }
    });
    Promise.all([
      until(this.userModel.account$, account => !!account),
      until(
        this.userModel.preferences$,
        prefs => !!prefs.allow_browser_notifications
      ),
    ]).then(() => {
      this.init();
    });
  }

  private async init() {
    if (this.initialized) return;
    if (!this.flagsService.isEnabled(KnownExperimentId.PUSH_NOTIFICATIONS)) {
      return;
    }
    if (!this.areNotificationsEnabled()) return;

    if (!('serviceWorker' in navigator)) {
      console.error('Service worker API not available');
      return;
    }
    await registerServiceWorker('/service-worker.js');
    const permission = await Notification.requestPermission();
    this.reportingService.reportLifeCycle(LifeCycle.NOTIFICATION_PERMISSION, {
      permission,
    });
    if (this.isPermitted(permission)) this.startTriggerTimer();
    this.initialized = true;
  }

  areNotificationsEnabled() {
    // Push Notification developer can have notification enabled even if they
    // are disabled for this.account.
    if (
      !this.flagsService.isEnabled(
        KnownExperimentId.PUSH_NOTIFICATIONS_DEVELOPER
      ) &&
      !areNotificationsEnabled(this.account)
    ) {
      return false;
    }

    return this.allowBrowserNotificationsPreference;
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
