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
      _boundHandleAuthError: {
        type: Function,
        value: function() { return this._handleAuthError.bind(this); },
      }
    },

    attached: function() {
      document.addEventListener('auth-error', this._boundHandleAuthError);
    },

    detached: function() {
      document.removeEventListener('auth-error', this._boundHandleAuthError);
    },

    _handleAuthError: function(e) {
      this._getLoggedIn().then(function(loggedIn) {
        if (loggedIn) {
          // The app was logged at one point and is now getting auth errors.
          this._showAuthErrorAlert();
        }
      }.bind(this));
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _showAuthErrorAlert: function() {
      if (this._alertElement) { return; }

      var el = document.createElement('gr-alert');
      el.addEventListener('action', this._refreshPage.bind(this));
      el.show('Auth error', 'Refresh page');
    },

    _refreshPage: function() {
      window.location.reload();
    },
  });
})();
