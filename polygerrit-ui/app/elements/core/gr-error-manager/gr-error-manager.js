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

  const HIDE_ALERT_TIMEOUT_MS = 5000;
  const CHECK_SIGN_IN_INTERVAL_MS = 60 * 1000;
  const STALE_CREDENTIAL_THRESHOLD_MS = 10 * 60 * 1000;
  const SIGN_IN_WIDTH_PX = 690;
  const SIGN_IN_HEIGHT_PX = 500;
  const TOO_MANY_FILES = 'too many files to find conflicts';
  const AUTHENTICATION_REQUIRED = 'Authentication required\n';

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
        value() { return Date.now(); },
      },
    },

    attached() {
      this.listen(document, 'server-error', '_handleServerError');
      this.listen(document, 'network-error', '_handleNetworkError');
      this.listen(document, 'auth-error', '_handleAuthError');
      this.listen(document, 'show-alert', '_handleShowAlert');
      this.listen(document, 'visibilitychange', '_handleVisibilityChange');
      this.listen(document, 'show-auth-required', '_handleAuthRequired');
    },

    detached() {
      this._clearHideAlertHandle();
      this.unlisten(document, 'server-error', '_handleServerError');
      this.unlisten(document, 'network-error', '_handleNetworkError');
      this.unlisten(document, 'auth-error', '_handleAuthError');
      this.unlisten(document, 'show-auth-required', '_handleAuthRequired');
      this.unlisten(document, 'visibilitychange', '_handleVisibilityChange');
    },

    _shouldSuppressError(msg) {
      return msg.includes(TOO_MANY_FILES);
    },

    _handleAuthRequired() {
      this._showAuthErrorAlert(
          'Log in is required to perform that action.', 'Log in.');
    },

    _handleAuthError() {
      this._showAuthErrorAlert('Auth error', 'Refresh credentials.');
    },

    _handleServerError(e) {
      Promise.all([
        e.detail.response.text(), this._getLoggedIn(),
      ]).then(values => {
        const text = values[0];
        const loggedIn = values[1];
        if (e.detail.response.status === 403 &&
            loggedIn &&
            text === AUTHENTICATION_REQUIRED) {
          // The app was logged at one point and is now getting auth errors.
          // This indicates the auth token is no longer valid.
          this._handleAuthError();
        } else if (!this._shouldSuppressError(text)) {
          this._showAlert('Server error: ' + text);
        }
        console.error(text);
      });
    },

    _handleShowAlert(e) {
      this._showAlert(e.detail.message, e.detail.action, e.detail.callback,
          e.detail.dismissOnNavigation);
    },

    _handleNetworkError(e) {
      this._showAlert('Server unavailable');
      console.error(e.detail.error.message);
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _showAlert(text, opt_actionText, opt_actionCallback,
        dismissOnNavigation) {
      if (this._alertElement) { return; }

      this._clearHideAlertHandle();
      if (dismissOnNavigation) {
        // Persist alert until navigation.
        this.listen(document, 'location-change', '_hideAlert');
      } else {
        this._hideAlertHandle =
          this.async(this._hideAlert, HIDE_ALERT_TIMEOUT_MS);
      }
      const el = this._createToastAlert();
      el.show(text, opt_actionText, opt_actionCallback);
      this._alertElement = el;
    },

    _hideAlert() {
      if (!this._alertElement) { return; }

      this._alertElement.hide();
      this._alertElement = null;

      // Remove listener for page navigation, if it exists.
      this.unlisten(document, 'location-change', '_hideAlert');
    },

    _clearHideAlertHandle() {
      if (this._hideAlertHandle != null) {
        this.cancelAsync(this._hideAlertHandle);
        this._hideAlertHandle = null;
      }
    },

    _showAuthErrorAlert(errorText, actionText) {
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

    _createToastAlert() {
      const el = document.createElement('gr-alert');
      el.toast = true;
      return el;
    },

    _handleVisibilityChange() {
      // Ignore when the page is transitioning to hidden (or hidden is
      // undefined).
      if (document.hidden !== false) { return; }

      // If not currently refreshing credentials and the credentials are old,
      // request them to confirm their validity or (display an auth toast if it
      // fails).
      const timeSinceLastCheck = Date.now() - this._lastCredentialCheck;
      if (!this._refreshingCredentials &&
          this.knownAccountId !== undefined &&
          timeSinceLastCheck > STALE_CREDENTIAL_THRESHOLD_MS) {
        this._lastCredentialCheck = Date.now();
        this.$.restAPI.checkCredentials();
      }
    },

    _requestCheckLoggedIn() {
      this.debounce(
          'checkLoggedIn', this._checkSignedIn, CHECK_SIGN_IN_INTERVAL_MS);
    },

    _checkSignedIn() {
      this.$.restAPI.checkCredentials().then(account => {
        const isLoggedIn = !!account;
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
      });
    },

    _reloadPage() {
      window.location.reload();
    },

    _createLoginPopup() {
      const left = window.screenLeft +
          (window.outerWidth - SIGN_IN_WIDTH_PX) / 2;
      const top = window.screenTop +
          (window.outerHeight - SIGN_IN_HEIGHT_PX) / 2;
      const options = [
        'width=' + SIGN_IN_WIDTH_PX,
        'height=' + SIGN_IN_HEIGHT_PX,
        'left=' + left,
        'top=' + top,
      ];
      window.open(this.getBaseUrl() +
          '/login/%3FcloseAfterLogin', '_blank', options.join(','));
      this.listen(window, 'focus', '_handleWindowFocus');
    },

    _handleCredentialRefreshed() {
      this.unlisten(window, 'focus', '_handleWindowFocus');
      this._refreshingCredentials = false;
      this._hideAlert();
      this._showAlert('Credentials refreshed.');
    },

    _handleWindowFocus() {
      this.flushDebouncer('checkLoggedIn');
    },
  });
})();
