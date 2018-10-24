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
    is: 'gr-account-info',

    /**
     * Fired when account details are changed.
     *
     * @event account-detail-update
     */

    properties: {
      usernameMutable: {
        type: Boolean,
        notify: true,
        computed: '_computeUsernameMutable(_serverConfig, _account.username)',
      },
      nameMutable: {
        type: Boolean,
        notify: true,
        computed: '_computeNameMutable(_serverConfig)',
      },
      hasUnsavedChanges: {
        type: Boolean,
        notify: true,
        computed: '_computeHasUnsavedChanges(_hasNameChange, ' +
            '_hasUsernameChange, _hasStatusChange)',
      },

      _hasNameChange: Boolean,
      _hasUsernameChange: Boolean,
      _hasStatusChange: Boolean,
      _loading: {
        type: Boolean,
        value: false,
      },
      _saving: {
        type: Boolean,
        value: false,
      },
      /** @type {?} */
      _account: Object,
      _serverConfig: Object,
      _username: {
        type: String,
        observer: '_usernameChanged',
      },
      _avatarChangeUrl: {
        type: String,
        value: '',
      },
    },

    observers: [
      '_nameChanged(_account.name)',
      '_statusChanged(_account.status)',
    ],

    async loadData() {
      const promises = [];

      this._loading = true;

      try {
        promises.push((async () => {
          this._serverConfig = await this.$.restAPI.getConfig();
        })());

        promises.push((async () => {
          const account = await this.$.restAPI.getAccount();
          this._hasNameChange = false;
          this._hasUsernameChange = false;
          this._hasStatusChange = false;
          // Provide predefined value for username to trigger computation of
          // username mutability.
          account.username = account.username || '';
          this._account = account;
          this._username = account.username;
        })());

        promises.push((async () => {
          this._avatarChangeUrl = await this.$.restAPI.getAvatarChangeUrl();
        })());

        await Promise.all(promises);
      } finally {
        this._loading = false;
      }
    },

    async save() {
      if (!this.hasUnsavedChanges) { return; }

      this._saving = true;
      // Set only the fields that have changed.
      // Must be done in sequence to avoid race conditions (@see Issue 5721)
      await this._maybeSetName();
      await this._maybeSetUsername();
      await this._maybeSetStatus();
      this._hasNameChange = false;
      this._hasStatusChange = false;
      this._saving = false;
      this.fire('account-detail-update');
    },

    async _maybeSetName() {
      if (this._hasNameChange && this.nameMutable) {
        return await this.$.restAPI.setAccountName(this._account.name);
      }
    },

    async _maybeSetUsername() {
      if (this._hasUsernameChange && this.usernameMutable) {
        return await this.$.restAPI.setAccountUsername(this._username);
      }
    },

    async _maybeSetStatus() {
      if (this._hasStatusChange) {
        return await this.$.restAPI.setAccountStatus(this._account.status);
      }
    },

    _computeHasUnsavedChanges(nameChanged, usernameChanged, statusChanged) {
      return nameChanged || usernameChanged || statusChanged;
    },

    _computeUsernameMutable(config, username) {
      // Username may not be changed once it is set.
      return config.auth.editable_account_fields.includes('USER_NAME') &&
          !username;
    },

    _computeNameMutable(config) {
      return config.auth.editable_account_fields.includes('FULL_NAME');
    },

    _statusChanged() {
      if (this._loading) { return; }
      this._hasStatusChange = true;
    },

    _usernameChanged() {
      if (this._loading || !this._account) { return; }
      this._hasUsernameChange =
          (this._account.username || '') !== (this._username || '');
    },

    _nameChanged() {
      if (this._loading) { return; }
      this._hasNameChange = true;
    },

    _handleKeydown(e) {
      if (e.keyCode === 13) { // Enter
        e.stopPropagation();
        this.save();
      }
    },

    _hideAvatarChangeUrl(avatarChangeUrl) {
      if (!avatarChangeUrl) {
        return 'hide';
      }

      return '';
    },
  });
})();
