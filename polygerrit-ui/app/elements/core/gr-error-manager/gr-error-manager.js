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

  var HIDE_ALERT_TIMEOUT_MS = 5000;
  var CHECK_SIGN_IN_INTERVAL_MS = 60000;
  var SIGN_IN_WIDTH_PX = 690;
  var SIGN_IN_HEIGHT_PX = 500;
  var TOO_MANY_FILES = 'too many files to find conflicts';

  Polymer({
    is: 'gr-error-manager',

    properties: {
      _alertElement: Element,
      _hideAlertHandle: Number,
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

    _shouldSuppressError: function(msg) {
      return msg.indexOf(TOO_MANY_FILES) > -1;
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
          if (!this._shouldSuppressError(text)) {
            this._showAlert('Server error: ' + text);
          }
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
        this.async(this._hideAlert, HIDE_ALERT_TIMEOUT_MS);
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
      // TODO(viktard): close alert if it's not for auth error.
      if (this._alertElement) { return; }

      this._alertElement = this._createToastAlert();
      this._alertElement.show('Auth error', 'Refresh credentials.');
      this.listen(this._alertElement, 'action', '_createLoginPopup');

      if (typeof document.hidden !== undefined) {
        this.listen(document, 'visibilitychange', '_handleVisibilityChange');
      }
      this._requestCheckLoggedIn();
      if (!document.hidden) {
        this._handleVisibilityChange();
      }
    },

    _createToastAlert: function() {
      var el = document.createElement('gr-alert');
      el.toast = true;
      return el;
    },

    _handleVisibilityChange: function() {
      if (!document.hidden) {
        this.flushDebouncer('checkLoggedIn');
      }
    },

    _requestCheckLoggedIn: function() {
      this.debounce(
        'checkLoggedIn', this._checkSignedIn, CHECK_SIGN_IN_INTERVAL_MS);
    },

    _checkSignedIn: function() {
      this.$.restAPI.refreshCredentials().then(function(isLoggedIn) {
        if (isLoggedIn) {
          this._handleCredentialRefresh();
        } else {
          this._requestCheckLoggedIn();
        }
      }.bind(this));
    },

    _createLoginPopup: function(e) {
      var left = window.screenLeft + (window.outerWidth - SIGN_IN_WIDTH_PX) / 2;
      var top = window.screenTop + (window.outerHeight - SIGN_IN_HEIGHT_PX) / 2;
      var options = [
        'width=' + SIGN_IN_WIDTH_PX,
        'height=' + SIGN_IN_HEIGHT_PX,
        'left=' + left,
        'top=' + top,
      ];
      window.open('/login/%3FcloseAfterLogin', '_blank', options.join(','));
    },

    _handleCredentialRefresh: function() {
      this.unlisten(document, 'visibilitychange', '_handleVisibilityChange');
      this.unlisten(this._alertElement, 'action', '_createLoginPopup');
      this._hideAlert();
      this._showAlert('Credentials refreshed.');
    },
  });
})();
