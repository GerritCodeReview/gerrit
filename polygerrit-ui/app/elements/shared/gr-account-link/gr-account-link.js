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
   * @appliesMixin Gerrit.BaseUrlMixin
   * @extends Polymer.Element
   */
  class GrAccountLink extends Polymer.mixinBehaviors( [
    Gerrit.BaseUrlBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-account-link'; }

    static get properties() {
      return {
        additionalText: String,
        account: Object,
        avatarImageSize: {
          type: Number,
          value: 32,
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
})();
