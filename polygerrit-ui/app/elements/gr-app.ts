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
import './gr-app-init';
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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-app_html';
import {initGerritPluginApi} from './shared/gr-js-api-interface/gr-gerrit';
import {customElement} from '@polymer/decorators';
import {installPolymerResin} from '../scripts/polymer-resin-install';

installPolymerResin(safeTypesBridge);

@customElement('gr-app')
class GrApp extends GestureEventListeners(LegacyElementMixin(PolymerElement)) {
  static get template() {
    return htmlTemplate;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-app': GrApp;
  }
}

initGlobalVariables();
initGerritPluginApi();
