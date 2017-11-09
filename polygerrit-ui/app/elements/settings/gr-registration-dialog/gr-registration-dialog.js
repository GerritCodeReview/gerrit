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
    is: 'gr-registration-dialog',

    /**
     * Fired when account details are changed.
     *
     * @event account-detail-update
     */

    /**
     * Fired when the close button is pressed.
     *
     * @event close
     */

    properties: {
      /** @type {?} */
      _account: {
        type: Object,
        value: () => {
          // Prepopulate possibly undefined fields with values to trigger
          // computed bindings.
          return {email: null, name: null, username: null};
        },
      },
      _saving: {
        type: Boolean,
        value: false,
      },
    },

    hostAttributes: {
      role: 'dialog',
    },

    attached() {
      this.$.restAPI.getAccount().then(account => {
        // Using Object.assign here allows preservation of the default values
        // supplied in the value generating function of this._account, unless
        // they are overridden by properties in the account from the response.
        this._account = Object.assign({}, this._account, account);
      });
    },

    _save() {
      this._saving = true;
      const promises = [
        this.$.restAPI.setAccountName(this.$.name.value),
        this.$.restAPI.setAccountUsername(this.$.username.value),
        this.$.restAPI.setPreferredAccountEmail(this.$.email.value || ''),
      ];
      return Promise.all(promises).then(() => {
        this._saving = false;
        this.fire('account-detail-update');
      });
    },

    _handleSave(e) {
      e.preventDefault();
      this._save().then(this.close.bind(this));
    },

    _handleClose(e) {
      e.preventDefault();
      this.close();
    },

    close() {
      this._saving = true; // disable buttons indefinitely
      this.fire('close');
    },

    _computeSaveDisabled(name, username, email, saving) {
      return !name || !username || !email || saving;
    },

    _computeSettingsUrl() {
      return Gerrit.Nav.getUrlForSettings();
    },
  });
})();
