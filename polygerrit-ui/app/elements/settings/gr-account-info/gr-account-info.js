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

  /**
   * @appliesMixin Gerrit.FireMixin
   */
  class GrAccountInfo extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-account-info'; }
    /**
     * Fired when account details are changed.
     *
     * @event account-detail-update
     */

    static get properties() {
      return {
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
      };
    }

    static get observers() {
      return [
        '_nameChanged(_account.name)',
        '_statusChanged(_account.status)',
      ];
    }

    loadData() {
      const promises = [];

      this._loading = true;

      promises.push(this.$.restAPI.getConfig().then(config => {
        this._serverConfig = config;
      }));

      promises.push(this.$.restAPI.getAccount().then(account => {
        this._hasNameChange = false;
        this._hasUsernameChange = false;
        this._hasStatusChange = false;
        // Provide predefined value for username to trigger computation of
        // username mutability.
        account.username = account.username || '';
        this._account = account;
        this._username = account.username;
      }));

      promises.push(this.$.restAPI.getAvatarChangeUrl().then(url => {
        this._avatarChangeUrl = url;
      }));

      return Promise.all(promises).then(() => {
        this._loading = false;
      });
    }

    save() {
      if (!this.hasUnsavedChanges) {
        return Promise.resolve();
      }

      this._saving = true;
      // Set only the fields that have changed.
      // Must be done in sequence to avoid race conditions (@see Issue 5721)
      return this._maybeSetName()
          .then(this._maybeSetUsername.bind(this))
          .then(this._maybeSetStatus.bind(this))
          .then(() => {
            this._hasNameChange = false;
            this._hasStatusChange = false;
            this._saving = false;
            this.fire('account-detail-update');
          });
    }

    _maybeSetName() {
      return this._hasNameChange && this.nameMutable ?
        this.$.restAPI.setAccountName(this._account.name) :
        Promise.resolve();
    }

    _maybeSetUsername() {
      return this._hasUsernameChange && this.usernameMutable ?
        this.$.restAPI.setAccountUsername(this._username) :
        Promise.resolve();
    }

    _maybeSetStatus() {
      return this._hasStatusChange ?
        this.$.restAPI.setAccountStatus(this._account.status) :
        Promise.resolve();
    }

    _computeHasUnsavedChanges(nameChanged, usernameChanged, statusChanged) {
      return nameChanged || usernameChanged || statusChanged;
    }

    _computeUsernameMutable(config, username) {
      // Polymer 2: check for undefined
      if ([
        config,
        username,
      ].some(arg => arg === undefined)) {
        return undefined;
      }

      // Username may not be changed once it is set.
      return config.auth.editable_account_fields.includes('USER_NAME') &&
          !username;
    }

    _computeNameMutable(config) {
      return config.auth.editable_account_fields.includes('FULL_NAME');
    }

    _statusChanged() {
      if (this._loading) { return; }
      this._hasStatusChange = true;
    }

    _usernameChanged() {
      if (this._loading || !this._account) { return; }
      this._hasUsernameChange =
          (this._account.username || '') !== (this._username || '');
    }

    _nameChanged() {
      if (this._loading) { return; }
      this._hasNameChange = true;
    }

    _handleKeydown(e) {
      if (e.keyCode === 13) { // Enter
        e.stopPropagation();
        this.save();
      }
    }

    _hideAvatarChangeUrl(avatarChangeUrl) {
      if (!avatarChangeUrl) {
        return 'hide';
      }

      return '';
    }
  }

  customElements.define(GrAccountInfo.is, GrAccountInfo);
})();
