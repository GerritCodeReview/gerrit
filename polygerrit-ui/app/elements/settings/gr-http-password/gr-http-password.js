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
    is: 'gr-http-password',

    /**
     * Fired when getting the password fails with non-404.
     *
     * @event network-error
     */

    properties: {
      _serverConfig: Object,
      _username: String,
      _password: String,
      _passwordVisible: {
        type: Boolean,
        value: false,
      },
      _hasPassword: Boolean,
    },

    loadData: function() {
      var promises = [];

      promises.push(this.$.restAPI.getAccount().then(function(account) {
        this._username = account.username;
      }.bind(this)));

      promises.push(this.$.restAPI
          .getAccountHttpPassword(this._handleGetPasswordError.bind(this))
          .then(function(pass) {
            this._password = pass;
            this._hasPassword = !!pass;
          }.bind(this)));

      return Promise.all(promises);
    },

    _handleGetPasswordError: function(response) {
      if (response.status === 404) {
        this._hasPassword = false;
      } else {
        this.fire('network-error', {response: response});
      }
    },

    _handleViewPasswordTap: function() {
      this._passwordVisible = true;
    },

    _handleGenerateTap: function() {
      this.$.restAPI.generateAccountHttpPassword().then(function(newPassword) {
        this._hasPassword = true;
        this._passwordVisible = true;
        this._password = newPassword;
      }.bind(this));
    },

    _handleClearTap: function() {
      this.$.restAPI.deleteAccountHttpPassword().then(function() {
        this._password = '';
        this._hasPassword = false;
      }.bind(this));
    },
  });
})();
