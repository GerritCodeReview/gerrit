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
    is: 'gr-ssh-editor',

    properties: {
      hasUnsavedChanges: {
        type: Boolean,
        value: false,
        notify: true,
      },
      _keys: Array,
      _keyToView: Object,
      _newKey: {
        type: String,
        value: '',
      },
      _keysToRemove: {
        type: Array,
        value: function() { return []; },
      },
    },

    loadData: function() {
      return this.$.restAPI.getAccountSSHKeys().then(function(keys) {
        this._keys = keys;
      }.bind(this));
    },

    save: function() {
      var promises = this._keysToRemove.map(function(key) {
        this.$.restAPI.deleteAccountSSHKey(key.seq);
      }.bind(this));

      return Promise.all(promises).then(function() {
        this._keysToRemove = [];
        this.hasUnsavedChanges = false;
      }.bind(this));
    },

    _getStatusLabel: function(isValid) {
      return isValid ? 'Valid' : 'Invalid';
    },

    _showKey: function(e) {
      var index = parseInt(e.target.getAttribute('data-index'), 10);
      this._keyToView = this._keys[index];
      this.$.viewKeyOverlay.open();
    },

    _closeOverlay: function() {
      this.$.viewKeyOverlay.close();
    },

    _handleDeleteKey: function(e) {
      var index = parseInt(e.target.getAttribute('data-index'), 10);
      this.push('_keysToRemove', this._keys[index]);
      this.splice('_keys', index, 1);
      this.hasUnsavedChanges = true;
    },

    _handleAddKey: function() {
      this.$.addButton.disabled = true;
      this.$.newKey.disabled = true;
      return this.$.restAPI.addAccountSSHKey(this._newKey.trim())
          .then(function(key) {
            this.$.newKey.disabled = false;
            this._newKey = '';
            this.push('_keys', key);
          }.bind(this))
          .catch(function() {
            this.$.addButton.disabled = false;
            this.$.newKey.disabled = false;
          }.bind(this));
    },

    _computeAddButtonDisabled: function(newKey) {
      return !newKey.length;
    },
  });
})();
