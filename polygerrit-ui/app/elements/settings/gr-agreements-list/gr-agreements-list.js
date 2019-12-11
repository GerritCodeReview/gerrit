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
(function() {
  'use strict';

  /**
   * @appliesMixin Gerrit.BaseUrlMixin
   */
  class GrAgreementsList extends Polymer.mixinBehaviors( [
    Gerrit.BaseUrlBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-agreements-list'; }

    static get properties() {
      return {
        _agreements: Array,
      };
    }

    attached() {
      super.attached();
      this.loadData();
    }

    loadData() {
      return this.$.restAPI.getAccountAgreements().then(agreements => {
        this._agreements = agreements;
      });
    }

    getUrl() {
      return this.getBaseUrl() + '/settings/new-agreement';
    }

    getUrlBase(item) {
      return this.getBaseUrl() + '/' + item;
    }
  }

  customElements.define(GrAgreementsList.is, GrAgreementsList);
})();
