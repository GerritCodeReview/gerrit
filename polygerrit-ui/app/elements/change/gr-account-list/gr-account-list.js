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

  const VALID_EMAIL_ALERT = 'Please input a valid email.';

  Polymer({
    is: 'gr-account-list',

    /**
     * Fired when user inputs an invalid email address.
     *
     * @event show-alert
     */

    properties: {
      accounts: {
        type: Array,
        value() { return []; },
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
       * When true, allows for non-suggested inputs to be added.
       */
      allowAnyInput: {
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

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      'backspace': '_handleBackspace',
      'left': '_handleLeftArrow',
      'right': '_handleRightArrow',
      'enter space del': '_handleRemoveChip',
    },

    listeners: {
      remove: '_handleRemove',
    },

    get accountChips() {
      return Polymer.dom(this.root).querySelectorAll('gr-account-chip');
    },

    get focusStart() {
      return this.$.entry.focusStart;
    },

    _handleAdd(e) {
      this._addReviewer(e.detail.value);
    },

    _addReviewer(reviewer) {
      // Append new account or group to the accounts property. We add our own
      // internal properties to the account/group here, so we clone the object
      // to avoid cluttering up the shared change object.
      if (reviewer.account) {
        const account =
            Object.assign({}, reviewer.account, {_pendingAdd: true});
        this.push('accounts', account);
      } else if (reviewer.group) {
        if (reviewer.confirm) {
          this.pendingConfirmation = reviewer;
          return;
        }
        const group = Object.assign({}, reviewer.group,
            {_pendingAdd: true, _group: true});
        this.push('accounts', group);
      } else if (this.allowAnyInput) {
        if (!reviewer.includes('@')) {
          // Repopulate the input with what the user tried to enter and have
          // a toast tell them why they can't enter it.
          this.$.entry.setText(reviewer);
          this.dispatchEvent(new CustomEvent('show-alert',
            {detail: {message: VALID_EMAIL_ALERT}, bubbles: true}));
          return false;
        } else {
          const account = {email: reviewer, _pendingAdd: true};
          this.push('accounts', account);
        }
      }
      this.pendingConfirmation = null;
      return true;
    },

    confirmGroup(group) {
      group = Object.assign(
          {}, group, {confirmed: true, _pendingAdd: true, _group: true});
      this.push('accounts', group);
      this.pendingConfirmation = null;
    },

    _computeChipClass(account) {
      const classes = [];
      if (account._group) {
        classes.push('group');
      }
      if (account._pendingAdd) {
        classes.push('pendingAdd');
      }
      return classes.join(' ');
    },

    _accountMatches(a, b) {
      if (a && b) {
        if (a._account_id) {
          return a._account_id === b._account_id;
        }
        if (a.email) {
          return a.email === b.email;
        }
      }
      return a === b;
    },

    _computeRemovable(account) {
      if (this.readonly) { return false; }
      if (this.removableValues) {
        for (let i = 0; i < this.removableValues.length; i++) {
          if (this._accountMatches(this.removableValues[i], account)) {
            return true;
          }
        }
        return !!account._pendingAdd;
      }
      return true;
    },

    _handleRemove(e) {
      const toRemove = e.detail.account;
      this._removeAccount(toRemove);
      this.$.entry.focus();
    },

    _removeAccount(toRemove) {
      if (!toRemove || !this._computeRemovable(toRemove)) { return; }
      for (let i = 0; i < this.accounts.length; i++) {
        let matches;
        const account = this.accounts[i];
        if (toRemove._group) {
          matches = toRemove.id === account.id;
        } else {
          matches = this._accountMatches(toRemove, account);
        }
        if (matches) {
          this.splice('accounts', i, 1);
          return;
        }
      }
      console.warn('received remove event for missing account', toRemove);
    },

    _handleBackspace(e) {
      const target = this.getRootTarget(e);
      if (target.localName === 'input') {
        if (target.selectionStart !== target.selectionEnd ||
            target.selectionStart !== 0) {
          return;
        }
        e.preventDefault();
        this._removeAccount(this.accounts[this.accounts.length - 1]);
      } else {
        e.preventDefault();
        this._removeSelectedChip(target);
      }
    },

    _handleLeftArrow(e) {
      const target = this.getRootTarget(e);
      if (target.localName === 'input') {
        if (target.selectionStart !== target.selectionEnd ||
            target.selectionStart !== 0) {
          return;
        }
        if (this.accountChips[this.accountChips.length - 1]) {
          this.accountChips[this.accountChips.length - 1].focus();
          e.preventDefault();
        }
      } else {
        const chips = this.accountChips;
        const index = chips.indexOf(target);
        if (index > 0) {
          e.preventDefault();
          target.blur();
          chips[index - 1].focus();
        }
      }
    },

    _handleRightArrow(e) {
      const target = this.getRootTarget(e);
      if (target.localName !== 'input') {
        e.preventDefault();
        const chips = this.accountChips;
        const index = chips.indexOf(target);
        target.blur();
        if (index < chips.length - 1) {
          chips[index + 1].focus();
        } else {
          this.$.entry.focus();
        }
      }
    },

    _handleRemoveChip(e) {
      const target = this.getRootTarget(e);
      if (target.localName !== 'input') {
        e.preventDefault();
        this._removeSelectedChip(target);
      }
    },

    _removeSelectedChip(chip) {
      const chips = this.accountChips;
      const index = chips.indexOf(chip);
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
    },
    
    /**
     * Submit the text of the entry as a reviewer value, if it exists. If it is
     * a successful submit of the text, clear the entry value.
     *
     * @return {boolean} If there is text in the entry, return true if the
     *     submission was successful and false if not. If there is no text,
     *     return true.
     */
    submitEntryText() {
      const text = this.$.entry.getText();
      if (!text.length) { return true; }
      const wasSubmitted = this._addReviewer(text);
      if (wasSubmitted) { this.$.entry.clear(); }
      return wasSubmitted;
    },

    additions() {
      return this.accounts.filter(account => {
        return account._pendingAdd;
      }).map(account => {
        if (account._group) {
          return {group: account};
        } else {
          return {account};
        }
      });
    },

    _computeEntryHidden(maxCount, accountsRecord, readonly) {
      return (maxCount && maxCount <= accountsRecord.base.length) || readonly;
    },
  });
})();
