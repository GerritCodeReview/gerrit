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
/* TODO(taoalpha): Remove once all legacyUndefinedCheck removed. */
/*
  FIXME(polymer-modulizer): the above comments were extracted
  from HTML and may be out of place here. Review them and
  then delete this comment!
*/
import './gr-app-init.js';
import './font-roboto-local-loader.js';
import '../scripts/bundled-polymer.js';
import 'polymer-resin/standalone/polymer-resin.js';
import {initGlobalVariables} from './gr-app-global-var-init.js';
// Initialize global variables before any other imports
// If a plugin is loaded in one of the subsequent import (with async/await, for
// example), then all global variables must be initialized at this time
initGlobalVariables();

import '../behaviors/safe-types-behavior/safe-types-behavior.js';
import './gr-app-element.js';
import './change-list/gr-embed-dashboard/gr-embed-dashboard.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-app_html.js';

security.polymer_resin.install({
  allowedIdentifierPrefixes: [''],
  reportHandler: security.polymer_resin.CONSOLE_LOGGING_REPORT_HANDLER,
  safeTypesBridge: Gerrit.SafeTypes.safeTypesBridge,
});

/** @extends Polymer.Element */
class GrApp extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-app'; }
}

customElements.define(GrApp.is, GrApp);
