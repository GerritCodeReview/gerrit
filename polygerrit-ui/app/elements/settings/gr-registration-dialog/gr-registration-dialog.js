// Copyright (C) 2017 The Android Open Source Project
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
      _account: Object,
      _saving: Boolean,
    },

    hostAttributes: {
      role: 'dialog',
    },

    attached() {
      this.$.restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          this.$.restAPI.getAccount().then(account => {
            this._account = account;
          });
        } else {
          this._account = null;
        }
      });
    },

    _handleUsernameKeydown(e) {
      if (e.keyCode === 13) { // Enter
        e.stopPropagation();
        this._save();
      }
    },

    _handleNameKeydown(e) {
      if (e.keyCode === 13) { // Enter
        e.stopPropagation();
        this._save();
      }
    },

    _save() {
      this._saving = true;
      const promises = [];
      if (this.$.username.bindValue &&
          this._account.username !== this.$.username.bindValue) {
        promises.push(
            this.$.restAPI.setAccountUsername(this.$.username.bindValue));
      }
      if (this.$.name.bindValue &&
          this._account.name !== this.$.name.bindValue) {
        promises.push(this.$.restAPI.setAccountName(this.$.name.bindValue));
      }
      if (this.$.email.value) {
        promises.push(
            this.$.restAPI.setPreferredAccountEmail(this.$.email.value));
      }
      return Promise.all(promises).then(() => {
        this._saving = false;
        this.fire('account-detail-update');
      });
    },

    _handleSave(e) {
      e.preventDefault();
      this._save().then(() => {
        this.fire('close');
      });
    },

    _handleClose(e) {
      e.preventDefault();
      this._saving = true; // disable buttons indefinitely
      this.fire('close');
    },
  });
})();
