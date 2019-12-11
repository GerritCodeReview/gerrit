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

  class GrUserHeader extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-user-header'; }

    static get properties() {
      return {
      /** @type {?String} */
        userId: {
          type: String,
          observer: '_accountChanged',
        },

        showDashboardLink: {
          type: Boolean,
          value: false,
        },

        loggedIn: {
          type: Boolean,
          value: false,
        },

        /**
         * @type {?{name: ?, email: ?, registered_on: ?}}
         */
        _accountDetails: {
          type: Object,
          value: null,
        },

        /** @type {?String} */
        _status: {
          type: String,
          value: null,
        },
      };
    }

    _accountChanged(userId) {
      if (!userId) {
        this._accountDetails = null;
        this._status = null;
        return;
      }

      this.$.restAPI.getAccountDetails(userId).then(details => {
        this._accountDetails = details;
      });
      this.$.restAPI.getAccountStatus(userId).then(status => {
        this._status = status;
      });
    }

    _computeDisplayClass(status) {
      return status ? ' ' : 'hide';
    }

    _computeDetail(accountDetails, name) {
      return accountDetails ? accountDetails[name] : '';
    }

    _computeStatusClass(accountDetails) {
      return this._computeDetail(accountDetails, 'status') ? '' : 'hide';
    }

    _computeDashboardUrl(accountDetails) {
      if (!accountDetails) { return null; }
      const id = accountDetails._account_id;
      const email = accountDetails.email;
      if (!id && !email ) { return null; }
      return Gerrit.Nav.getUrlForUserDashboard(id ? id : email);
    }

    _computeDashboardLinkClass(showDashboardLink, loggedIn) {
      return showDashboardLink && loggedIn ?
        'dashboardLink' : 'dashboardLink hide';
    }
  }

  customElements.define(GrUserHeader.is, GrUserHeader);
})();
