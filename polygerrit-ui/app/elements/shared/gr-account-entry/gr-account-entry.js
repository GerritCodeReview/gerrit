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
    is: 'gr-account-entry',
    _legacyUndefinedCheck: true,

    /**
     * Fired when an account is entered.
     *
     * @event add
     */

    /**
     * When allowAnyInput is true, account-text-changed is fired when input text
     * changed. This is needed so that the reply dialog's save button can be
     * enabled for arbitrary cc's, which don't need a 'commit'.
     *
     * @event account-text-changed
     */
    properties: {
      allowAnyInput: Boolean,
      borderless: Boolean,
      filter: Function,
      placeholder: String,

      // suggestFrom = 0 to enable default suggestions.
      suggestFrom: {
        type: Number,
        value: 0,
      },

      querySuggestions: {
        type: Object,
        notify: true,
        value() {
          return () => Promise.resolve([]);
        },
      },

      _autocompleteQuery: {
        type: Function,
        value() {
          return this._getSuggestions.bind(this);
        },
      },

      _config: Object,
      /** The value of the autocomplete entry. */
      _inputText: {
        type: String,
        observer: '_inputTextChanged',
      },

    },

    behaviors: [
      Gerrit.AnonymousNameBehavior,
      Gerrit.FireBehavior,
    ],

    attached() {
      this.$.restAPI.getConfig().then(cfg => {
        this._config = cfg;
      });
    },

    get focusStart() {
      return this.$.input.focusStart;
    },

    focus() {
      this.$.input.focus();
    },

    clear() {
      this.$.input.clear();
    },

    setText(text) {
      this.$.input.setText(text);
    },

    getText() {
      return this.$.input.text;
    },

    _handleInputCommit(e) {
      this.fire('add', {value: e.detail.value});
      this.$.input.focus();
    },

    _accountOrAnon(name) { // TODO: bad naming
      return this.getUserName(this._config, name, false);
    },

    _inputTextChanged(text) {
      if (text.length && this.allowAnyInput) {
        this.dispatchEvent(new CustomEvent(
                'account-text-changed', {bubbles: true, composed: true}));
      }
    },

    _makeSuggestion(suggestion) { // TODO: bad naming
      let name;
      let value;
      const generateStatusStr = function(account) {
        return account.status ? '(' + account.status + ')' : '';
      };
      if (suggestion.account) {
        // Reviewer is an account suggestion from getChangeSuggestedReviewers.
        const reviewerName = this._accountOrAnon(suggestion.account);
        const reviewerEmail = this._reviewerEmail(suggestion.account.email);
        const reviewerStatus = generateStatusStr(suggestion.account);
        name = [reviewerName, reviewerEmail, reviewerStatus]
            .filter(p => p.length > 0).join(' ');
        value = suggestion;
      } else if (suggestion.group) {
        // Reviewer is a group suggestion from getChangeSuggestedReviewers.
        name = suggestion.group.name + ' (group)';
        value = suggestion;
      } else if (suggestion._account_id) {
        // Reviewer is an account suggestion from getSuggestedAccounts.
        const reviewerName = this._accountOrAnon(suggestion);
        const reviewerEmail = this._reviewerEmail(suggestion.email);
        const reviewerStatus = generateStatusStr(suggestion);
        name = [reviewerName, reviewerEmail, reviewerStatus]
            .filter(p => p.length > 0).join(' ');
        value = {account: suggestion, count: 1};
      }
      return {name, value};
    },

    _getSuggestions(input) {
      return this.querySuggestions(input).then(suggestions => {
        if (!suggestions) { return []; }
        if (!this.filter) {
          return suggestions.map(this._makeSuggestion.bind(this));
        }
        return suggestions
            .filter(this.filter)
            .map(this._makeSuggestion.bind(this));
      });
    },

    _reviewerEmail(email) {
      if (typeof email !== 'undefined') {
        return '<' + email + '>';
      }

      return '';
    },
  });
})();
