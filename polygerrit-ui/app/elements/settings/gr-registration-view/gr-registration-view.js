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
    is: 'gr-registration-view',

    /**
     * Fired when account details are changed.
     *
     * @event account-detail-update
     */

    properties: {
      /** @type {?} */
      _account: Object,
      /** @type {?} */
      _serverConfig: Object,
      _saving: Boolean,
      params: Object,
      /**
       * For testing purposes.
       */
      _loadingPromise: Object,
    },

    attached() {
      const promises = [];
      promises.push(this._getAccount());

      promises.push(this.$.restAPI.getConfig().then(config => {
        this._serverConfig = config;
      }));

      this._loadingPromise = Promise.all(promises).then(() => {
        this._saving = false;
      });
    },

    _getAccount() {
      return this.$.restAPI.getAccount().then(account => {
        this._account = account;
      });
    },

    _handleUserNameKeydown(e) {
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

    _handleEmailKeydown(e) {
      if (e.keyCode === 13) { // Enter
        e.stopPropagation();
        this._save();
      }
    },

    _save() {
      this._saving = true;
      const promises = [];
      if (this.$.nameInput.bindValue &&
          this._account.name !== this.$.nameInput.bindValue) {
        promises.push(
            this.$.restAPI.setAccountName(this.$.nameInput.bindValue));
      }

      if (this.$.emailInput.value) {
        promises.push(
            this.$.restAPI.addAccountEmail(this.$.emailInput.value));
      }

      if (this.$.emailSelect.bindValue) {
        promises.push(
            this.$.restAPI.setPreferredAccountEmail(
                this.$.emailSelect.bindValue));
      }

      return Promise.all(promises).then(() => {
        this._saving = false;
        this.fire('account-detail-update');
        this._getAccount();
      });
    },

    _handleSave(e) {
      e.preventDefault();
      this._save();
    },

    _handleContinue(e) {
      e.preventDefault();
      const path = this.params.path ? this.params.path : '/';
      const pathWithSlash = path.startsWith('/') ? path : '/' + path;
      return page.show(pathWithSlash);
    },

    _handleSettingUsername() {
      this.$.overlay.close();
      if (!this.$.userNameInput.bindValue) { return; }
      return this.$.restAPI.setAccountUsername(this.$.userNameInput.bindValue)
          .then(res => {
            this._getAccount();
          });
    },

    _handleConfirmDialogCancel() {
      this.$.overlay.close();
    },

    _handleSetUsername(e) {
      this.$.overlay.open();
    },
  });
})();
