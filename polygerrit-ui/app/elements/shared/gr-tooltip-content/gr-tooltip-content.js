/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import '../../../scripts/bundled-polymer.js';

import '../gr-icons/gr-icons.js';
import '../../../behaviors/gr-tooltip-behavior/gr-tooltip-behavior.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-tooltip-content_html.js';

/**
 * @appliesMixin Gerrit.TooltipMixin
 * @extends Polymer.Element
 */
class GrTooltipContent extends mixinBehaviors( [
  Gerrit.TooltipBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-tooltip-content'; }

  static get properties() {
    return {
      title: {
        type: String,
        reflectToAttribute: true,
      },
      maxWidth: {
        type: String,
        reflectToAttribute: true,
      },
      positionBelow: {
        type: Boolean,
        valye: false,
        reflectToAttribute: true,
      },
      showIcon: {
        type: Boolean,
        value: false,
      },
    };
  }
}

customElements.define(GrTooltipContent.is, GrTooltipContent);
