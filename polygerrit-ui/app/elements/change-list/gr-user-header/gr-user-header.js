/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-avatar/gr-avatar.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/dashboard-header-styles.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="dashboard-header-styles">
      .name {
        display: inline-block;
      }
      .name hr {
        width: 100%;
      }
      .status.hide,
      .name.hide,
      .dashboardLink.hide {
        display: none;
      }
    </style>
    <gr-avatar account="[[_accountDetails]]" image-size="100" aria-label="Account avatar"></gr-avatar>
    <div class="info">
      <h1 class="name">
        [[_computeDetail(_accountDetails, 'name')]]
      </h1>
      <hr>
      <div class\$="status [[_computeStatusClass(_accountDetails)]]">
        <span>Status:</span> [[_status]]
      </div>
      <div>
        <span>Email:</span>
        <a href="mailto:[[_computeDetail(_accountDetails, 'email')]]"><!--
          -->[[_computeDetail(_accountDetails, 'email')]]</a>
      </div>
      <div>
        <span>Joined:</span>
        <gr-date-formatter date-str="[[_computeDetail(_accountDetails, 'registered_on')]]">
        </gr-date-formatter>
      </div>
    </div>
    <div class="info">
      <div class\$="[[_computeDashboardLinkClass(showDashboardLink, loggedIn)]]">
        <a href\$="[[_computeDashboardUrl(_accountDetails)]]">View dashboard</a>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-user-header',

  properties: {
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
  },

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
  },

  _computeDisplayClass(status) {
    return status ? ' ' : 'hide';
  },

  _computeDetail(accountDetails, name) {
    return accountDetails ? accountDetails[name] : '';
  },

  _computeStatusClass(accountDetails) {
    return this._computeDetail(accountDetails, 'status') ? '' : 'hide';
  },

  _computeDashboardUrl(accountDetails) {
    if (!accountDetails || !accountDetails.email) { return null; }
    return Gerrit.Nav.getUrlForUserDashboard(accountDetails.email);
  },

  _computeDashboardLinkClass(showDashboardLink, loggedIn) {
    return showDashboardLink && loggedIn ?
        'dashboardLink' : 'dashboardLink hide';
  }
});
