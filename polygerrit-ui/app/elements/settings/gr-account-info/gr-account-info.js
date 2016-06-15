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

    properties: {
      mutable: {
        type: Boolean,
        notify: true,
        computed: '_computeMutable(_serverConfig)',
      },
      hasUnsavedChanges: {
        type: Boolean,
        notify: true,
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
      _account: Object,
      _serverConfig: Object,
    },

    observers: [
      '_nameChanged(_account.name)',
    ],

    loadData: function() {
      var promises = [];

      this._loading = true;

      promises.push(this.$.restAPI.getConfig().then(function(config) {
        this._serverConfig = config;
      }.bind(this)));

      promises.push(this.$.restAPI.getAccount().then(function(account) {
        this._account = account;
      }.bind(this)));

      return Promise.all(promises).then(function() {
        this._loading = false;
      }.bind(this));
    },

    save: function() {
      if (!this.mutable || !this.hasUnsavedChanges) {
        return Promise.resolve();
      }

      this._saving = true;
      return this.$.restAPI.setAccountName(this._account.name).then(function() {
        this.hasUnsavedChanges = false;
        this._saving = false;
      }.bind(this));
    },

    _computeMutable: function(config) {
      return config.auth.editable_account_fields.indexOf('FULL_NAME') !== -1;
    },

    _nameChanged: function() {
      if (this._loading) { return; }
      this.hasUnsavedChanges = true;
    },

    _handleNameKeydown: function(e) {
      if (e.keyCode === 13) { // Enter
        e.stopPropagation();
        this.save();
      }
    },
  });
})();
