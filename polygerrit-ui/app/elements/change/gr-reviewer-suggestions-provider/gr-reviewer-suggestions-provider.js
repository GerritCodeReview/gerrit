/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
    is: 'gr-reviewer-suggestions-provider',

    properties: {
      change: Object,

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

      getSuggestions: {
        type: Function,
        readOnly: true,
        value() {
          return this._getSuggestions.bind(this);
        },
      },

      _loggedIn: Boolean,
    },

    attached() {
      this.$.restAPI.getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
      });
    },

    _getSuggestions(input) {
      if (!this.change || !this.change._number || !this._loggedIn) {
        return Promise.resolve([]);
      }

      const api = this.$.restAPI;
      const xhr = this.allowAnyUser ?
          api.getSuggestedAccounts(`cansee:${this.change._number} ${input}`) :
          api.getChangeSuggestedReviewers(this.change._number, input);

      return xhr.then(reviewers => {
        if (!reviewers) { return []; }
        return reviewers;
      });
    },
  });
})();
