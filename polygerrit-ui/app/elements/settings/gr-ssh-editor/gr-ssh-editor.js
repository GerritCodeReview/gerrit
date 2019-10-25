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
    is: 'gr-ssh-editor',

    properties: {
      hasUnsavedChanges: {
        type: Boolean,
        value: false,
        notify: true,
      },
      _keys: Array,
      /** @type {?} */
      _keyToView: Object,
      _newKey: {
        type: String,
        value: '',
      },
      _keysToRemove: {
        type: Array,
        value() { return []; },
      },
    },

    loadData() {
      return this.$.restAPI.getAccountSSHKeys().then(keys => {
        this._keys = keys;
      });
    },

    save() {
      const promises = this._keysToRemove.map(key => {
        this.$.restAPI.deleteAccountSSHKey(key.seq);
      });

      return Promise.all(promises).then(() => {
        this._keysToRemove = [];
        this.hasUnsavedChanges = false;
      });
    },

    _getStatusLabel(isValid) {
      return isValid ? 'Valid' : 'Invalid';
    },

    _showKey(e) {
      const el = Polymer.dom(e).localTarget;
      const index = parseInt(el.getAttribute('data-index'), 10);
      this._keyToView = this._keys[index];
      this.$.viewKeyOverlay.open();
    },

    _closeOverlay() {
      this.$.viewKeyOverlay.close();
    },

    _handleDeleteKey(e) {
      const el = Polymer.dom(e).localTarget;
      const index = parseInt(el.getAttribute('data-index'), 10);
      this.push('_keysToRemove', this._keys[index]);
      this.splice('_keys', index, 1);
      this.hasUnsavedChanges = true;
    },

    _handleAddKey() {
      this.$.addButton.disabled = true;
      this.$.newKey.disabled = true;
      return this.$.restAPI.addAccountSSHKey(this._newKey.trim())
          .then(key => {
            this.$.newKey.disabled = false;
            this._newKey = '';
            this.push('_keys', key);
          }).catch(() => {
            this.$.addButton.disabled = false;
            this.$.newKey.disabled = false;
          });
    },

    _computeAddButtonDisabled(newKey) {
      return !newKey.length;
    },
  });
})();
