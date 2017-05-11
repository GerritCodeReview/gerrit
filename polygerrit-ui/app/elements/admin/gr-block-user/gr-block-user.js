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

    _computeHideBlockActions: function(account, _isLoading) {
      return !account || _isLoading;
    },

    _handleAccountSearch: function() {
      this.$.restAPI.getUserAccount(this.$.entry.getText()).then(
        function(account) {
          this.account = account;
        }.bind(this));
    },

    _accountChanged: function() {
      if (!this.account) {
        return;
      }
      this._isLoading = true;
      var accountId = this.account._account_id;
      this.$.restAPI.getUserBlocked(accountId).then(function(isBlocked) {
        if (!this.account || accountId !== this.account._account_id) {
          // Late response.
          return;
        }
        this._isLoading = false;
        this._isBlocked = isBlocked;
      }.bind(this));
    },

    _setAccount: function(e) {
      this.account = e.detail.value.account;
    },

    _clearAccount: function(e) {
      this.account = null;
      this._isLoading = false;
    },

    _handleUnblockUserTap: function() {
      this.$.restAPI.unblockUser(this.account._account_id).then(
        function(isUnblocked) {
          this._isBlocked = !isUnblocked;
        }.bind(this));
    },

    _handleBlockUserTap: function() {
      this.$.overlay.open();
    },

    _handleConfirmDialogCancel: function() {
      this.$.overlay.close();
    },

    _handleBlockUserConfirm: function() {
      this.$.restAPI.blockUser(this.account._account_id)
        .then(function(isBlocked) {
          this._isBlocked = isBlocked;
          this._handleConfirmDialogCancel();
        }.bind(this));
    },
  });
})();
