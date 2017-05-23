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
    is: 'gr-account-entry',

    /**
     * Fired when an account is entered.
     *
     * @event add
     */
    properties: {
      borderless: Boolean,
      change: Object,
      filter: Function,
      placeholder: String,
      /**
       * When true, account-entry uses the account suggest API endpoint, which
       * suggests any account in that Gerrit instance (and does not suggest
       * groups).
       *
       * When false/undefined, account-entry uses the suggest_reviewers API
       * endpoint, which suggests any account or group in that Gerrit instance
       * that is not already a reviewer (or is not CCed) on that change.
       */
      allowAnyUser: Boolean,

      suggestFrom: {
        type: Number,
        value: 3,
      },

      query: {
        type: Function,
        value() {
          return this._getReviewerSuggestions.bind(this);
        },
      },
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
    },

    _makeSuggestion(reviewer) {
      let name;
      let value;
      const generateStatusStr = function(account) {
        return account.status ? ' (' + account.status + ')' : '';
      };
      if (reviewer.account) {
        // Reviewer is an account suggestion from getChangeSuggestedReviewers.
        name = reviewer.account.name + ' <' + reviewer.account.email + '>' +
            generateStatusStr(reviewer.account);
        value = reviewer;
      } else if (reviewer.group) {
        // Reviewer is a group suggestion from getChangeSuggestedReviewers.
        name = reviewer.group.name + ' (group)';
        value = reviewer;
      } else if (reviewer._account_id) {
        // Reviewer is an account suggestion from getSuggestedAccounts.
        name = reviewer.name + ' <' + reviewer.email + '>' +
            generateStatusStr(reviewer);
        value = {account: reviewer, count: 1};
      }
      return {name, value};
    },

    _getReviewerSuggestions(input) {
      const api = this.$.restAPI;
      const xhr = this.allowAnyUser ?
          api.getSuggestedAccounts(input) :
          api.getChangeSuggestedReviewers(this.change._number, input);

      return xhr.then(reviewers => {
        if (!reviewers) { return []; }
        if (!this.filter) { return reviewers.map(this._makeSuggestion); }
        return reviewers
            .filter(this.filter)
            .map(this._makeSuggestion.bind(this));
      });
    },
  });
})();
