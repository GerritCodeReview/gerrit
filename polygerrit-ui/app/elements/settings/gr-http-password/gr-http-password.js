/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

  Polymer({
    is: 'gr-http-password',

    properties: {
      _username: String,
      _generatedPassword: String,
      _passwordUrl: String,
    },

    attached() {
      this.loadData();
    },

    loadData() {
      const promises = [];

      promises.push(this.$.restAPI.getAccount().then(account => {
        this._username = account.username;
      }));

      promises.push(this.$.restAPI.getConfig().then(info => {
        this._passwordUrl = info.auth.http_password_url || null;
      }));

      return Promise.all(promises);
    },

    _handleGenerateTap() {
      this._generatedPassword = 'Generating...';
      this.$.generatedPasswordOverlay.open();
      this.$.restAPI.generateAccountHttpPassword().then(newPassword => {
        this._generatedPassword = newPassword;
      });
    },

    _closeOverlay() {
      this.$.generatedPasswordOverlay.close();
    },

    _generatedPasswordOverlayClosed() {
      this._generatedPassword = '';
    },
  });
})();
