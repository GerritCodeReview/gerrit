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
(function() {
  'use strict';

  /**
    * @appliesMixin Gerrit.FireMixin
    */
  class GrAccountChip extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
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
        additionalText: String,
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
      return this.$.restAPI.getConfig().then(cfg => {
        return Promise.resolve(!!(cfg && cfg.plugin && cfg.plugin.has_avatars));
      });
    }
  }

  customElements.define(GrAccountChip.is, GrAccountChip);
})();
