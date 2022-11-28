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
import {define} from '../models/dependency';
import {Model} from '../models/model';
import {Observable} from 'rxjs';
import {select} from '../utils/observable-util';

/** Type of incoming messages for ServiceWorker. */
export enum ServiceWorkerMessageType {
  TRIGGER_NOTIFICATIONS = 'TRIGGER_NOTIFICATIONS',
  USER_PREFERENCE_CHANGE = 'USER_PREFERENCE_CHANGE',
  REPORTING = 'REPORTING',
}

export const TRIGGER_NOTIFICATION_UPDATES_MS = 5 * 60 * 1000;

export const serviceWorkerInstallerToken = define<ServiceWorkerInstaller>(
  'service-worker-installer'
);

/**
 * Service worker state:
 * initialized - True when service worker registered and event listeners added.
 *             - False otherwise
 * shouldShowPrompt - True when user didn't make decision about notifications
 *                  - False otherwise
 */
export interface ServiceWorkerInstallerState {
  initialized: boolean;
  shouldShowPrompt: boolean;
}

export class ServiceWorkerInstaller extends Model<ServiceWorkerInstallerState> {
  readonly initialized$: Observable<Boolean | undefined> = select(
    this.state$,
    state => state.initialized
  );

  readonly shouldShowPrompt$: Observable<Boolean | undefined> = select(
    this.initialized$,
    _ => this.shouldShowPrompt()
  );

  // Internal state, it's exposed in initialized$
  private initialized = false;

  account?: AccountDetailInfo;

  allowBrowserNotificationsPreference?: boolean;

  constructor(
    private readonly flagsService: FlagsService,
    private readonly reportingService: ReportingService,
    private readonly userModel: UserModel
  ) {
    super({initialized: false, shouldShowPrompt: false});
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
    const permission = Notification.permission;
    this.reportingService.reportLifeCycle(LifeCycle.NOTIFICATION_PERMISSION, {
      permission,
    });
    if (this.isPermitted(permission)) this.startTriggerTimer();
    this.initialized = true;
    this.updateState({initialized: true});
    // Assumption: service worker will send event only to 1 client.
    navigator.serviceWorker.onmessage = event => {
      if (event.data?.type === ServiceWorkerMessageType.REPORTING) {
        this.reportingService.reportLifeCycle(LifeCycle.SERVICE_WORKER_UPDATE, {
          eventName: event.data.eventName as string | undefined,
        });
      }
    };
  }

  private shouldShowPrompt(): boolean {
    if (!this.initialized) return false;
    if (this.isPermitted(Notification.permission)) return false;
    if (!this.flagsService.isEnabled(KnownExperimentId.PUSH_NOTIFICATIONS)) {
      return false;
    }
    if (!this.areNotificationsEnabled()) return false;
    return true;
  }

  public async requestPermission() {
    const permission = await Notification.requestPermission();
    this.reportingService.reportLifeCycle(LifeCycle.NOTIFICATION_PERMISSION, {
      requested: true,
      permission,
    });
    if (this.isPermitted(permission)) this.startTriggerTimer();
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
