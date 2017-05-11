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
    is: 'gr-block-user',

    properties: {
      account: {
        type: Object,
        observer: '_accountChanged',
      },
      _isLoading: {
        type: Boolean,
        value: false,
      },
      _isBlocked: Boolean,
    },

    _computeHideBlockActions(account, _isLoading) {
      return !account || _isLoading;
    },

    _handleAccountSearch() {
      this.$.restAPI.getUserAccount(this.$.entry.getText()).then(
          account => {
            this.account = account;
          });
    },

    _accountChanged() {
      if (!this.account) {
        return;
      }
      this._isLoading = true;
      const accountId = this.account._account_id;
      this.$.restAPI.getUserBlocked(accountId).then(isBlocked => {
        if (!this.account || accountId !== this.account._account_id) {
          // Late response.
          return;
        }
        this._isLoading = false;
        this._isBlocked = isBlocked;
      });
    },

    _setAccount(e) {
      this.account = e.detail.value.account;
    },

    _clearAccount(e) {
      this.account = null;
      this._isLoading = false;
    },

    _handleUnblockUserTap() {
      this.$.restAPI.unblockUser(this.account._account_id).then(
          isUnblocked => {
            this._isBlocked = !isUnblocked;
          });
    },

    _handleBlockUserTap() {
      this.$.overlay.open();
    },

    _handleConfirmDialogCancel() {
      this.$.overlay.close();
    },

    _handleBlockUserConfirm() {
      this.$.restAPI.blockUser(this.account._account_id)
          .then(isBlocked => {
            this._isBlocked = isBlocked;
            this._handleConfirmDialogCancel();
          });
    },
  });
})();
