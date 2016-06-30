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
    is: 'gr-account-list',

    properties: {
      accounts: {
        type: Array,
      },
      change: {
        type: Object,
      },
    },

    discardPendingChanges: function() {
      var restoredList = [];
      for (var i = 0; i < this.accounts.length; i++) {
        var account = this.accounts[i];
        if (!account._pendingAdd) {
          // TODO(logan): Polyfill for Object.assign in IE.
          var restored = Object.assign({}, account);
          delete restored._pendingAdd;
          delete restored._pendingRemove;
          restoredList.push(restored);
        }
      }
      console.log('restored list:', restoredList);
      this.splice('accounts', 0, this.accounts.length, restoredList);
    },

    _handleAdd: function(e) {
      console.log('adding');
      var account = e.detail.account;
      account._pendingAdd = true;
      console.log('pushing');
      if (this.accounts === undefined) {
        this.set('accounts', [account]);
        console.log('this.accounts now', this.accounts);
      } else {
        this.push('accounts', account);
      }
      console.log('done');
    },

    _handleRemove: function(e) {
      var account = e.detail.account;
      for (var i = 0; i < this.accounts.length; i++) {
        if (this.accounts[i]._account_id === account._account_id) {
          if (account._pendingAdd) {
            this.splice('accounts', i, 1);
          } else {
            // TODO(logan): Polyfill for Object.assign in IE.
            var newAccount = Object.assign({_pendingRemove: true}, account);
            this.splice('accounts', i, 1, newAccount);
          }
          break;
        }
      }
    },

    _computeCanRemoveReviewer: function(account) {
      return account._pendingAdd || account._pendingRemove;
    },

    _chipClass: function(account) {
      if (account._pendingAdd) {
        return "pending-add";
      }
      if (account._pendingRemove) {
        return "pending-remove";
      }
      return "";
    },

    additions: function() {
      console.log('additions: this.accounts =', this.accounts);
      if (this.accounts === undefined) {
        return [];
      }
      var accounts = [];
      for (var i = 0; i < this.accounts.length; i++) {
        var account = this.accounts[i];
        if (account._pendingAdd) {
          accounts.push(account);
        }
      }
      return accounts;
    },
  });
})();
