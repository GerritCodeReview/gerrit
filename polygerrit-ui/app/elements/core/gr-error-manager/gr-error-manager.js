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
  var CHECK_SIGN_IN_INTERVAL_MS = 60 * 1000;
  var STALE_CREDENTIAL_THRESHOLD_MS = 10 * 60 * 1000;
  var SIGN_IN_WIDTH_PX = 690;
  var SIGN_IN_HEIGHT_PX = 500;
  var TOO_MANY_FILES = 'too many files to find conflicts';
  var AUTHENTICATION_REQUIRED = 'Authentication required\n';

  Polymer({
    is: 'gr-error-manager',

    behaviors: [
      Gerrit.BaseUrlBehavior,
    ],

    properties: {
      /**
       * The ID of the account that was logged in when the app was launched. If
       * not set, then there was no account at launch.
       */
      knownAccountId: Number,

      _alertElement: Element,
      _hideAlertHandle: Number,
      _refreshingCredentials: {
        type: Boolean,
        value: false,
      },

      /**
       * The time (in milliseconds) since the most recent credential check.
       */
      _lastCredentialCheck: {
        type: Number,
        value: function() { return Date.now(); },
      }
    },

    attached: function() {
      this.listen(document, 'server-error', '_handleServerError');
      this.listen(document, 'network-error', '_handleNetworkError');
      this.listen(document, 'show-alert', '_handleShowAlert');
      this.listen(document, 'visibilitychange', '_handleVisibilityChange');
      this.listen(document, 'show-auth-required', '_handleAuthRequired');
    },

    detached: function() {
      this._clearHideAlertHandle();
      this.unlisten(document, 'server-error', '_handleServerError');
      this.unlisten(document, 'network-error', '_handleNetworkError');
      this.unlisten(document, 'show-auth-required', '_handleAuthRequired');
      this.unlisten(document, 'visibilitychange', '_handleVisibilityChange');
    },

    _shouldSuppressError: function(msg) {
      return msg.indexOf(TOO_MANY_FILES) > -1;
    },

    _handleAuthRequired: function() {
      this._showAuthErrorAlert(
          'Log in is required to perform that action.', 'Log in.');
    },

    _handleServerError: function(e) {
      Promise.all([
        e.detail.response.text(), this._getLoggedIn()
      ]).then(function(values) {
        var text = values[0];
        var loggedIn = values[1];
        if (e.detail.response.status === 403 &&
            loggedIn &&
            text === AUTHENTICATION_REQUIRED) {
          // The app was logged at one point and is now getting auth errors.
          // This indicates the auth token is no longer valid.
          this._showAuthErrorAlert('Auth error', 'Refresh credentials.');
        } else if (!this._shouldSuppressError(text)) {
          this._showAlert('Server error: ' + text);
        }
      }.bind(this));
    },

    _handleShowAlert: function(e) {
      this._showAlert(e.detail.message, e.detail.action, e.detail.callback);
    },

    _handleNetworkError: function(e) {
      this._showAlert('Server unavailable');
      console.error(e.detail.error.message);
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _showAlert: function(text, opt_actionText, opt_actionCallback) {
      if (this._alertElement) { return; }

      this._clearHideAlertHandle();
      this._hideAlertHandle =
        this.async(this._hideAlert, HIDE_ALERT_TIMEOUT_MS);
      var el = this._createToastAlert();
      el.show(text, opt_actionText, opt_actionCallback);
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

    _showAuthErrorAlert: function(errorText, actionText) {
      // TODO(viktard): close alert if it's not for auth error.
      if (this._alertElement) { return; }

      this._alertElement = this._createToastAlert();
      this._alertElement.show(errorText, actionText,
          this._createLoginPopup.bind(this));

      this._refreshingCredentials = true;
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
      // Ignore when the page is transitioning to hidden (or hidden is
      // undefined).
      if (document.hidden !== false) { return; }

      // If not currently refreshing credentials and the credentials are old,
      // request them to confirm their validity or (display an auth toast if it
      // fails).
      var timeSinceLastCheck = Date.now() - this._lastCredentialCheck;
      if (!this._refreshingCredentials &&
          this.knownAccountId !== undefined &&
          timeSinceLastCheck > STALE_CREDENTIAL_THRESHOLD_MS) {
        this._lastCredentialCheck = Date.now();
        this.$.restAPI.checkCredentials();
      }
    },

    _requestCheckLoggedIn: function() {
      this.debounce(
        'checkLoggedIn', this._checkSignedIn, CHECK_SIGN_IN_INTERVAL_MS);
    },

    _checkSignedIn: function() {
      this.$.restAPI.checkCredentials().then(function(account) {
        var isLoggedIn = !!account;
        this._lastCredentialCheck = Date.now();
        if (this._refreshingCredentials) {
          if (isLoggedIn) {

            // If the credentials were refreshed but the account is different
            // then reload the page completely.
            if (account._account_id !== this.knownAccountId) {
              this._reloadPage();
              return;
            }

            this._handleCredentialRefreshed();
          } else {
            this._requestCheckLoggedIn();
          }
        }
      }.bind(this));
    },

    _reloadPage: function() {
      window.location.reload();
    },

    _createLoginPopup: function() {
      var left = window.screenLeft + (window.outerWidth - SIGN_IN_WIDTH_PX) / 2;
      var top = window.screenTop + (window.outerHeight - SIGN_IN_HEIGHT_PX) / 2;
      var options = [
        'width=' + SIGN_IN_WIDTH_PX,
        'height=' + SIGN_IN_HEIGHT_PX,
        'left=' + left,
        'top=' + top,
      ];
      window.open(this.getBaseUrl() +
          '/login/%3FcloseAfterLogin', '_blank', options.join(','));
      this.listen(window, 'focus', '_handleWindowFocus');
    },

    _handleCredentialRefreshed: function() {
      this.unlisten(window, 'focus', '_handleWindowFocus');
      this._refreshingCredentials = false;
      this._hideAlert();
      this._showAlert('Credentials refreshed.');
    },

    _handleWindowFocus: function() {
      this.flushDebouncer('checkLoggedIn');
    },
  });
})();
