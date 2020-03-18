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
import '../../../behaviors/base-url-behavior/base-url-behavior.js';

import '../../../scripts/bundled-polymer.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../gr-account-label/gr-account-label.js';
import '../../../styles/shared-styles.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-account-link_html.js';

/**
 * @appliesMixin Gerrit.BaseUrlMixin
 * @extends Polymer.Element
 */
class GrAccountLink extends mixinBehaviors( [
  Gerrit.BaseUrlBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-account-link'; }

  static get properties() {
    return {
      voteableText: String,
      account: Object,
      hideAvatar: {
        type: Boolean,
        value: false,
      },
      hideStatus: {
        type: Boolean,
        value: false,
      },
    };
  }

  _computeOwnerLink(account) {
    if (!account) { return; }
    return Gerrit.Nav.getUrlForOwner(
        account.email || account.username || account.name ||
        account._account_id);
  }
}

customElements.define(GrAccountLink.is, GrAccountLink);
