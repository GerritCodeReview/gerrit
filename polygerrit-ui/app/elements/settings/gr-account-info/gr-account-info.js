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
    is: 'gr-account-info',

    /**
     * Fired when account details are changed.
     *
     * @event account-detail-update
     */

    properties: {
      mutable: {
        type: Boolean,
        notify: true,
        computed: '_computeMutable(_serverConfig)',
      },
      hasUnsavedChanges: {
        type: Boolean,
        notify: true,
        computed: '_computeHasUnsavedChanges(_hasNameChange, _hasStatusChange)',
      },

      _hasNameChange: {
        type: Boolean,
        value: false,
      },
      _hasStatusChange: {
        type: Boolean,
        value: false,
      },
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
    },

    observers: [
      '_nameChanged(_account.name)',
      '_statusChanged(_account.status)',
    ],

    loadData() {
      const promises = [];

      this._loading = true;

      promises.push(this.$.restAPI.getConfig().then(config => {
        this._serverConfig = config;
      }));

      promises.push(this.$.restAPI.getAccount().then(account => {
        this._account = account;
      }));

      return Promise.all(promises).then(() => {
        this._loading = false;
      });
    },

    save() {
      if (!this.hasUnsavedChanges) {
        return Promise.resolve();
      }

      this._saving = true;
      // Set only the fields that have changed.
      // Must be done in sequence to avoid race conditions (@see Issue 5721)
      return this._maybeSetName()
          .then(this._maybeSetStatus.bind(this))
          .then(() => {
            this._hasNameChange = false;
            this._hasStatusChange = false;
            this._saving = false;
            this.fire('account-detail-update');
          });
    },

    _maybeSetName() {
      return this._hasNameChange && this.mutable ?
                this.$.restAPI.setAccountName(this._account.name) :
                Promise.resolve();
    },

    _maybeSetStatus() {
      return this._hasStatusChange ?
          this.$.restAPI.setAccountStatus(this._account.status) :
          Promise.resolve();
    },

    _computeHasUnsavedChanges(name, status) {
      return name || status;
    },

    _computeMutable(config) {
      return config.auth.editable_account_fields.includes('FULL_NAME');
    },

    _statusChanged() {
      if (this._loading) { return; }
      this._hasStatusChange = true;
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
  });
})();
