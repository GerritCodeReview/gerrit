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

import {safeTypesBridge} from '../utils/safe-types-util.js';

// We need to use goog.declareModuleId internally in google for TS-imports-JS
// case. To avoid errors when goog is not available, the empty implementation is
// added.
window.goog = window.goog || {declareModuleId(name) {}};
import './gr-app-init.js';
import './font-roboto-local-loader.js';
// Sets up global Polymer variable, because plugins requires it.
import '../scripts/bundled-polymer.js';

/**
 * setCancelSyntheticClickEvents is set to true by
 * default which will cancel synthetic click events
 * on older touch device.
 * See https://github.com/Polymer/polymer/issues/5289
 */
import {setPassiveTouchGestures, setCancelSyntheticClickEvents} from '@polymer/polymer/lib/utils/settings.js';
setCancelSyntheticClickEvents(false);
setPassiveTouchGestures(true);

import 'polymer-resin/standalone/polymer-resin.js';
import {initGlobalVariables} from './gr-app-global-var-init.js';
import './gr-app-element.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-app_html.js';
import {initGerritPluginApi} from './shared/gr-js-api-interface/gr-gerrit.js';
import {appContext} from '../services/app-context.js';

security.polymer_resin.install({
  allowedIdentifierPrefixes: [''],
  reportHandler: security.polymer_resin.CONSOLE_LOGGING_REPORT_HANDLER,
  safeTypesBridge,
});

/** @extends PolymerElement */
class GrApp extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  // When you are converting gr-app.js to ts, implement interface AppElement
  // from the gr-app-types.ts
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-app'; }
}

customElements.define(GrApp.is, GrApp);

initGlobalVariables();
initGerritPluginApi(appContext);
