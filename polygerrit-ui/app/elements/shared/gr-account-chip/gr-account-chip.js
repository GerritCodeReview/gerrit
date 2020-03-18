/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import '../../../behaviors/fire-behavior/fire-behavior.js';
import '../gr-account-link/gr-account-link.js';
import '../gr-button/gr-button.js';
import '../gr-icons/gr-icons.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-account-chip_html.js';

/**
 * @appliesMixin Gerrit.FireMixin
 * @extends Polymer.Element
 */
class GrAccountChip extends mixinBehaviors( [
  Gerrit.FireBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-account-chip'; }
  /**
   * Fired to indicate a key was pressed while this chip was focused.
   *
   * @event account-chip-keydown
   */

  /**
   * Fired to indicate this chip should be removed, i.e. when the x button is
   * clicked or when the remove function is called.
   *
   * @event remove
   */

  static get properties() {
    return {
      account: Object,
      voteableText: String,
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      removable: {
        type: Boolean,
        value: false,
      },
      showAvatar: {
        type: Boolean,
        reflectToAttribute: true,
      },
      transparentBackground: {
        type: Boolean,
        value: false,
      },
    };
  }

  /** @override */
  ready() {
    super.ready();
    this._getHasAvatars().then(hasAvatars => {
      this.showAvatar = hasAvatars;
    });
  }

  _getBackgroundClass(transparent) {
    return transparent ? 'transparentBackground' : '';
  }

  _handleRemoveTap(e) {
    e.preventDefault();
    this.fire('remove', {account: this.account});
  }

  _getHasAvatars() {
    return this.$.restAPI.getConfig()
        .then(cfg => Promise.resolve(!!(
          cfg && cfg.plugin && cfg.plugin.has_avatars
        )));
  }
}

customElements.define(GrAccountChip.is, GrAccountChip);
