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

  const VALID_EMAIL_ALERT = 'Please input a valid email.';

  /**
    * @appliesMixin Gerrit.FireMixin
    */
  class GrAccountList extends Polymer.mixinBehaviors( [
    // Used in the tests for gr-account-list and other elements tests.
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-account-list'; }
    /**
     * Fired when user inputs an invalid email address.
     *
     * @event show-alert
     */

    static get properties() {
      return {
        accounts: {
          type: Array,
          value() { return []; },
          notify: true,
        },
        change: Object,
        filter: Function,
        placeholder: String,
        disabled: {
          type: Function,
          value: false,
        },

        /**
         * Returns suggestions and convert them to list item
         * @type {Gerrit.GrSuggestionsProvider}
         */
        suggestionsProvider: {
          type: Object,
        },

        /**
         * Needed for template checking since value is initially set to null.
         * @type {?Object}
         */
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

        /**
         * Returns suggestion items
         * @type {!function(string): Promise<Array<Gerrit.GrSuggestionItem>>}
         */
        _querySuggestions: {
          type: Function,
          value() {
            return this._getSuggestions.bind(this);
          },
        },

        /**
         * Set to true to disable suggestions on empty input.
         */
        skipSuggestOnEmpty: {
          type: Boolean,
          value: false,
        },
      };
    }

    created() {
      super.created();
      this.addEventListener('remove',
          e => this._handleRemove(e));
    }

    get accountChips() {
      return Array.from(
          Polymer.dom(this.root).querySelectorAll('gr-account-chip'));
    }

    get focusStart() {
      return this.$.entry.focusStart;
    }

    _getSuggestions(input) {
      if (this.skipSuggestOnEmpty && !input) {
        return Promise.resolve([]);
      }
      const provider = this.suggestionsProvider;
      if (!provider) {
        return Promise.resolve([]);
      }
      return provider.getSuggestions(input).then(suggestions => {
        if (!suggestions) { return []; }
        if (this.filter) {
          suggestions = suggestions.filter(this.filter);
        }
        return suggestions.map(suggestion =>
          provider.makeSuggestionItem(suggestion));
      });
    }

    _handleAdd(e) {
      this._addAccountItem(e.detail.value);
    }

    _addAccountItem(item) {
      // Append new account or group to the accounts property. We add our own
      // internal properties to the account/group here, so we clone the object
      // to avoid cluttering up the shared change object.
      if (item.account) {
        const account =
            Object.assign({}, item.account, {_pendingAdd: true});
        this.push('accounts', account);
      } else if (item.group) {
        if (item.confirm) {
          this.pendingConfirmation = item;
          return;
        }
        const group = Object.assign({}, item.group,
            {_pendingAdd: true, _group: true});
        this.push('accounts', group);
      } else if (this.allowAnyInput) {
        if (!item.includes('@')) {
          // Repopulate the input with what the user tried to enter and have
          // a toast tell them why they can't enter it.
          this.$.entry.setText(item);
          this.dispatchEvent(new CustomEvent('show-alert', {
            detail: {message: VALID_EMAIL_ALERT},
            bubbles: true,
            composed: true,
          }));
          return false;
        } else {
          const account = {email: item, _pendingAdd: true};
          this.push('accounts', account);
        }
      }
      this.pendingConfirmation = null;
      return true;
    }

    confirmGroup(group) {
      group = Object.assign(
          {}, group, {confirmed: true, _pendingAdd: true, _group: true});
      this.push('accounts', group);
      this.pendingConfirmation = null;
    }

    _computeChipClass(account) {
      const classes = [];
      if (account._group) {
        classes.push('group');
      }
      if (account._pendingAdd) {
        classes.push('pendingAdd');
      }
      return classes.join(' ');
    }

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
    }

    _computeRemovable(account, readonly) {
      if (readonly) { return false; }
      if (this.removableValues) {
        for (let i = 0; i < this.removableValues.length; i++) {
          if (this._accountMatches(this.removableValues[i], account)) {
            return true;
          }
        }
        return !!account._pendingAdd;
      }
      return true;
    }

    _handleRemove(e) {
      const toRemove = e.detail.account;
      this._removeAccount(toRemove);
      this.$.entry.focus();
    }

    _removeAccount(toRemove) {
      if (!toRemove || !this._computeRemovable(toRemove, this.readonly)) {
        return;
      }
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
    }

    _getNativeInput(paperInput) {
      // In Polymer 2 inputElement isn't nativeInput anymore
      return paperInput.$.nativeInput || paperInput.inputElement;
    }

    _handleInputKeydown(e) {
      const input = this._getNativeInput(e.detail.input);
      if (input.selectionStart !== input.selectionEnd ||
          input.selectionStart !== 0) {
        return;
      }
      switch (e.detail.keyCode) {
        case 8: // Backspace
          this._removeAccount(this.accounts[this.accounts.length - 1]);
          break;
        case 37: // Left arrow
          if (this.accountChips[this.accountChips.length - 1]) {
            this.accountChips[this.accountChips.length - 1].focus();
          }
          break;
      }
    }

    _handleChipKeydown(e) {
      const chip = e.target;
      const chips = this.accountChips;
      const index = chips.indexOf(chip);
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
    }

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
      const wasSubmitted = this._addAccountItem(text);
      if (wasSubmitted) { this.$.entry.clear(); }
      return wasSubmitted;
    }

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
    }

    _computeEntryHidden(maxCount, accountsRecord, readonly) {
      return (maxCount && maxCount <= accountsRecord.base.length) || readonly;
    }
  }

  customElements.define(GrAccountList.is, GrAccountList);
})();
