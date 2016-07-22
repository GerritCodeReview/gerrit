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
        value: function() { return []; },
      },
      change: Object,
      placeholder: String,
      pendingConfirmation: {
        type: Object,
        value: null,
        notify: true,
      },
      readonly: Boolean,

      filter: {
        type: Function,
        value: function() {
          return this._filterSuggestion.bind(this);
        },
      },
    },

    listeners: {
      'remove': '_handleRemove',
    },

    get focusStart() {
      return this.$.entry.focusStart;
    },

    _handleAdd: function(e) {
      var reviewer = e.detail.value;
      // Append new account or group to the accounts property. We add our own
      // internal properties to the account/group here, so we clone the object
      // to avoid cluttering up the shared change object.
      // TODO(logan): Polyfill for Object.assign in IE.
      if (reviewer.account) {
        var account = Object.assign({}, reviewer.account, {_pendingAdd: true});
        this.push('accounts', account);
      } else if (reviewer.group) {
        if (reviewer.confirm) {
          this.pendingConfirmation = reviewer;
          return;
        }
        var group = Object.assign({}, reviewer.group,
            {_pendingAdd: true, _group: true});
        this.push('accounts', group);
      }
      this.pendingConfirmation = null;
    },

    confirmGroup: function(group) {
      group = Object.assign(
          {}, group, {confirmed: true, _pendingAdd: true, _group: true});
      this.push('accounts', group);
      this.pendingConfirmation = null;
    },

    _computeChipClass: function(account) {
      var classes = [];
      if (account._group) {
        classes.push('group');
      }
      if (account._pendingAdd) {
        classes.push('pendingAdd');
      }
      return classes.join(' ');
    },

    _computeRemovable: function(account) {
      return !this.readonly && !!account._pendingAdd;
    },

    _filterSuggestion: function(reviewer) {
      if (!this.$.entry.notOwnerOrReviewer(reviewer)) {
        return false;
      }
      for (var i = 0; i < this.accounts.length; i++) {
        var account = this.accounts[i];
        if (!account._pendingAdd) {
          continue;
        }
        if (reviewer.group && account._group &&
            reviewer.group.id === account.id) {
          return false;
        }
        if (reviewer.account && !account._group &&
            account._account_id === account._account_id) {
          return false;
        }
      }
      return true;
    },

    _handleRemove: function(e) {
      var toRemove = e.detail.account;
      for (var i = 0; i < this.accounts.length; i++) {
        var matches;
        var account = this.accounts[i];
        if (toRemove._group) {
          matches = toRemove.id === account.id;
        } else {
          matches = toRemove._account_id === account._account_id;
        }
        if (matches) {
          this.splice('accounts', i, 1);
          return;
        }
      }
      console.warn('received remove event for missing account',
          e.detail.account);
    },

    additions: function() {
      var result = [];
      return this.accounts.filter(function(account) {
        return account._pendingAdd;
      }).map(function(account) {
        if (account._group) {
          return {group: account};
        } else {
          return {account: account};
        }
      });
      return result;
    },
  });
})();
