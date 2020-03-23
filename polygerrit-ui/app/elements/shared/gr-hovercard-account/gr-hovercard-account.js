/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import '@polymer/iron-icon/iron-icon.js';
import '../../../styles/shared-styles.js';
import '../gr-avatar/gr-avatar.js';
import '../gr-button/gr-button.js';
import {hovercardBehaviorMixin} from '../gr-hovercard/gr-hovercard-behavior.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-hovercard-account_html.js';

/** @extends Polymer.Element */
class GrHovercardAccount extends GestureEventListeners(
    hovercardBehaviorMixin(LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-hovercard-account'; }

  static get properties() {
    return {
      account: Object,
      voteableText: String,
      attention: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
    };
  }
}

customElements.define(GrHovercardAccount.is, GrHovercardAccount);
