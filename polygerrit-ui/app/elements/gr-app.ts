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
import {provide, DIPolymerElement} from '../services/dependency';
import {htmlTemplate} from './gr-app_html';
import {customElement} from '@polymer/decorators';
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

const appContext = createAppContext();
injectAppContext(appContext);
const reportingService = appContext.reportingService;
initVisibilityReporter(reportingService);
initPerformanceReporter(reportingService);
initErrorReporter(reportingService);

installPolymerResin(safeTypesBridge);

@customElement('gr-app')
export class GrApp extends DIPolymerElement {
  private finalizables: Finalizable[] = [];

  override connectedCallback() {
    super.connectedCallback();
    const dependencies = createAppDependencies(appContext);
    for (const [token, service] of dependencies) {
      provide(this, token, () => service);
    }
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    for (const f of this.finalizables) {
      f.finalize();
    }
    this.finalizables = [];
  }

  static get template() {
    return htmlTemplate;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-app': GrApp;
  }
}

initGlobalVariables(appContext);
