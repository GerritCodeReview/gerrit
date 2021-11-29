/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {safeTypesBridge} from '../utils/safe-types-util';
import './font-roboto-local-loader';
// Sets up global Polymer variable, because plugins requires it.
import '../scripts/bundled-polymer';

/**
 * setCancelSyntheticClickEvents is set to true by
 * default which will cancel synthetic click events
 * on older touch device.
 * See https://github.com/Polymer/polymer/issues/5289
 */
import {
  setPassiveTouchGestures,
  setCancelSyntheticClickEvents,
} from '@polymer/polymer/lib/utils/settings';
setCancelSyntheticClickEvents(false);
setPassiveTouchGestures(true);

import {initGlobalVariables} from './gr-app-global-var-init';
import './gr-app-element';
import {Finalizable} from '../services/registry';
import {provide} from '../models/dependency';
import {installPolymerResin} from '../scripts/polymer-resin-install';

import {
  createAppContext,
  createAppDependencies,
} from '../services/app-context-init';
import {
  initVisibilityReporter,
  initPerformanceReporter,
  initErrorReporter,
} from '../services/gr-reporting/gr-reporting_impl';
import {injectAppContext} from '../services/app-context';
import {html, LitElement} from 'lit';
import {customElement} from 'lit/decorators';

const appContext = createAppContext();
injectAppContext(appContext);
const reportingService = appContext.reportingService;
initVisibilityReporter(reportingService);
initPerformanceReporter(reportingService);
initErrorReporter(reportingService);

installPolymerResin(safeTypesBridge);
const requestNotificationPermission = async () => {
  const permission = await window.Notification.requestPermission();
  // value of permission can be 'granted', 'default', 'denied'
  // granted: user has accepted the request
  // default: user has dismissed the notification permission popup by clicking on x
  // denied: user has denied the request.
  if (permission !== 'granted') {
    throw new Error('Permission not granted for Notification');
  }
};

if ('serviceWorker' in navigator) {
  // Use the window load event to keep the page load performant
  setTimeout(async () => {
    console.log('Register service worker');
    await navigator.serviceWorker.register('http://localhost:8081/service-worker.js');
    const permission = await requestNotificationPermission();
    // navigator.serviceWorker.register('/service-worker.js');
  }, 2000);
}

@customElement('gr-app')
export class GrApp extends LitElement {
  private finalizables: Finalizable[] = [];

  override connectedCallback() {
    super.connectedCallback();
    const dependencies = createAppDependencies(appContext);
    for (const [token, service] of dependencies) {
      this.finalizables.push(service);
      provide(this, token, () => service);
    }
  }

  override disconnectedCallback() {
    for (const f of this.finalizables) {
      f.finalize();
    }
    this.finalizables = [];
    super.disconnectedCallback();
  }

  override render() {
    return html`<gr-app-element id="app-element"></gr-app-element>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-app': GrApp;
  }
}

initGlobalVariables(appContext);
