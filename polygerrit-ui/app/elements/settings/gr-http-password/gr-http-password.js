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

    properties: {
      _username: String,
      _generatedPassword: String,
      _passwordUrl: String,
    },

    loadData: function() {
      var promises = [];

      promises.push(this.$.restAPI.getAccount().then(function(account) {
        this._username = account.username;
      }.bind(this)));

      promises.push(this.$.restAPI.getConfig().then(function(info) {
        this._passwordUrl = info.auth.http_password_url || null;
      }.bind(this)));

      return Promise.all(promises);
    },

    _handleGenerateTap: function() {
      this._generatedPassword = 'Generating...';
      this.$.generatedPasswordOverlay.open();
      this.$.restAPI.generateAccountHttpPassword().then(function(newPassword) {
        this._generatedPassword = newPassword;
      }.bind(this));
    },

    _closeOverlay: function() {
      this.$.generatedPasswordOverlay.close();
    },

    _generatedPasswordOverlayClosed: function() {
      this._generatedPassword = null;
    },
  });
})();
