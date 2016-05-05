// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  Polymer({
    is: 'gr-error-manager',

    properties: {
      _alertElement: Element,
      _hideAlertHandle: Number,
      _hideAlertTimeout: {
        type: Number,
        value: 5000,
      },
    },

    attached: function() {
      this.listen(document, 'server-error', '_handleServerError');
      this.listen(document, 'network-error', '_handleNetworkError');
    },

    detached: function() {
      this._clearHideAlertHandle();
      this.unlisten(document, 'server-error', '_handleServerError');
      this.unlisten(document, 'network-error', '_handleNetworkError');
    },

    _handleServerError: function(e) {
      if (e.detail.response.status === 403) {
        this._getLoggedIn().then(function(loggedIn) {
          if (loggedIn) {
            // The app was logged at one point and is now getting auth errors.
            // This indicates the auth token is no longer valid.
            this._showAuthErrorAlert();
          }
        }.bind(this));
      } else {
        e.detail.response.text().then(function(text) {
          this._showAlert('Server error: ' + text);
        }.bind(this));
      }
    },

    _handleNetworkError: function(e) {
      this._showAlert('Server unavailable');
      console.error(e.detail.error.message);
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _showAlert: function(text) {
      if (this._alertElement) { return; }

      this._clearHideAlertHandle();
      this._hideAlertHandle =
            this.async(this._hideAlert.bind(this), this._hideAlertTimeout);
      var el = this._createToastAlert();
      el.show(text);
      this._alertElement = el;
    },

    _hideAlert: function() {
      if (!this._alertElement) { return; }

      this._alertElement.hide();
      this._alertElement = null;
    },

    _clearHideAlertHandle: function() {
      if (this._hideAlertHandle != null) {
        this.cancelAsync(this._hideAlertHandle);
        this._hideAlertHandle = null;
      }
    },

    _showAuthErrorAlert: function() {
      if (this._alertElement) { return; }

      var el = this._createToastAlert();
      el.addEventListener('action', this._refreshPage.bind(this));
      el.show('Auth error', 'Refresh page');
      this._alertElement = el;
    },

    _createToastAlert: function() {
      var el = document.createElement('gr-alert');
      el.toast = true;
      return el;
    },

    _refreshPage: function() {
      window.location.reload();
    },
  });
})();
