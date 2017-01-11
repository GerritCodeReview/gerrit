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
        notify: true,
      },
      change: Object,
      filter: Function,
      placeholder: String,
      pendingConfirmation: {
        type: Object,
        value: null,
        notify: true,
      },
      readonly: {
        type: Boolean,
        value: false,
      },
      /**
       * When true, the account-entry autocomplete uses the account suggest API
       * endpoint, which suggests any account in that Gerrit instance (and does
       * not suggest groups).
       *
       * When false/undefined, account-entry uses the suggest_reviewers API
       * endpoint, which suggests any account or group in that Gerrit instance
       * that is not already a reviewer (or is not CCed) on that change.
       */
      allowAnyUser: {
        type: Boolean,
        value: false,
      },
      /**
       * Array of values (groups/accounts) that are removable. When this prop is
       * undefined, all values are removable.
       */
      removableValues: Array,
      maxCount: {
        type: Number,
        value: 0,
      },
    },

    listeners: {
      'remove': '_handleRemove',
    },

    get accountChips() {
      return Polymer.dom(this.root).querySelectorAll('gr-account-chip');
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
      if (this.readonly) { return false; }
      if (this.removableValues) {
        for (var i = 0; i < this.removableValues.length; i++) {
          if (this.removableValues[i]._account_id === account._account_id) {
            return true;
          }
        }
        return !!account._pendingAdd;
      }
      return true;
    },

    _handleRemove: function(e) {
      var toRemove = e.detail.account;
      this._removeAccount(toRemove);
      this.$.entry.focus();
    },

    _removeAccount: function(toRemove) {
      if (!toRemove || !this._computeRemovable(toRemove)) { return; }
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
      console.warn('received remove event for missing account', toRemove);
    },

    _handleInputKeydown: function(e) {
      var input = e.detail.input;
      if (input.selectionStart !== input.selectionEnd ||
          input.selectionStart !== 0) {
        return;
      }
      switch (e.detail.keyCode) {
        case 8: // Backspace
          this._removeAccount(this.accounts[this.accounts.length - 1]);
          break;
        case 37: // Left arrow
          var chips = this.accountChips;
          if (chips[chips.length - 1]) {
            chips[chips.length - 1].focus();
          }
          break;
      }
    },

    _handleChipKeydown: function(e) {
      var chip = e.target;
      var chips = this.accountChips;
      var index = chips.indexOf(chip);
      switch (e.keyCode) {
        case 8: // Backspace
        case 13: // Enter
        case 32: // Spacebar
        case 46: // Delete
          this._removeAccount(chip.account);
          // Splice from this array to avoid inconsistent ordering of
          // event handling.
          chips.splice(index, 1);
          if (index < chips.length) {
            chips[index].focus();
          } else if (index > 0) {
            chips[index - 1].focus();
          } else {
            this.$.entry.focus();
          }
          break;
        case 37: // Left arrow
          if (index > 0) {
            chip.blur();
            chips[index - 1].focus();
          }
          break;
        case 39: // Right arrow
          chip.blur();
          if (index < chips.length - 1) {
            chips[index + 1].focus();
          } else {
            this.$.entry.focus();
          }
          break;
      }
    },

    additions: function() {
      return this.accounts.filter(function(account) {
        return account._pendingAdd;
      }).map(function(account) {
        if (account._group) {
          return {group: account};
        } else {
          return {account: account};
        }
      });
    },

    _computeEntryHidden: function(maxCount, accountsRecord, readonly) {
      return (maxCount && maxCount <= accountsRecord.base.length) || readonly;
    },
  });
})();
