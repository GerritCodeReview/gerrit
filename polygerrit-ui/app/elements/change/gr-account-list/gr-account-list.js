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
      if (this.accounts === undefined) {
        return;
      }
      var restoredList = [];
      for (var i = 0; i < this.accounts.length; i++) {
        var account = this.accounts[i];
        if (!account._pendingAdd) {
          // TODO(logan): Polyfill for Object.assign in IE.
          var restored = Object.assign({}, account);
          delete restored._pendingAdd;
          restoredList.push(restored);
        }
      }
      this.set('accounts', restoredList);
    },

    _handleAdd: function(e) {
      var account = e.detail.account;
      account._pendingAdd = true;
      if (this.accounts === undefined) {
        this.set('accounts', [account]);
      } else {
        this.push('accounts', account);
      }
    },

    _chipClass: function(account) {
      if (account._pendingAdd) {
        return "pending-add";
      }
      return "";
    },

    additions: function() {
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
