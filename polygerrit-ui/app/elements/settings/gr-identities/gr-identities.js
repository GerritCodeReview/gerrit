/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
    is: 'gr-identities',
    _legacyUndefinedCheck: true,

    properties: {
      _identities: Object,
      _idName: String,
    },

    loadData() {
      return this.$.restAPI.getExternalIds().then(id => {
        this._identities = id;
      });
    },

    _computeIdentity(id) {
      return id && id.startsWith('mailto:') ? '' : id;
    },

    _computeHideDeleteClass(canDelete) {
      return canDelete ? 'show' : '';
    },

    _handleDeleteItemConfirm() {
      this.$.overlay.close();
      return this.$.restAPI.deleteAccountIdentity([this._idName])
          .then(() => { this.loadData(); });
    },

    _handleConfirmDialogCancel() {
      this.$.overlay.close();
    },

    _handleDeleteItem(e) {
      const name = e.model.get('item.identity');
      if (!name) { return; }
      this._idName = name;
      this.$.overlay.open();
    },

    _computeIsTrusted(item) {
      return item ? '' : 'Untrusted';
    },

    filterIdentities(item) {
      return !item.identity.startsWith('username:');
    },
  });
})();
