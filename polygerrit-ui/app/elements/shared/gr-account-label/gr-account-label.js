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
   * @appliesMixin Gerrit.DisplayNameMixin
   * @extends Polymer.Element
   */
  class GrAccountLabel extends Polymer.mixinBehaviors( [
    Gerrit.DisplayNameBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-account-label'; }

    static get properties() {
      return {
        /**
         * @type {{ name: string, status: string }}
         */
        account: Object,
        hideAvatar: {
          type: Boolean,
          value: false,
        },
        hideStatus: {
          type: Boolean,
          value: false,
        },
        _serverConfig: {
          type: Object,
          value: null,
        },
      };
    }

    /** @override */
    ready() {
      super.ready();
      this.$.restAPI.getConfig()
          .then(config => { this._serverConfig = config; });
    }

    _computeName(account, config) {
      return this.getDisplayName(config, account);
    }
  }

  customElements.define(GrAccountLabel.is, GrAccountLabel);
})();
