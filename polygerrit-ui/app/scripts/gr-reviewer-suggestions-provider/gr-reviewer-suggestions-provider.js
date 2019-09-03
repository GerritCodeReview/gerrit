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
(function(window) {
  'use strict';

  if (window.GrReviewerSuggestionsProvider) {
    return;
  }

  class GrReviewerSuggestionsProvider {
    constructor(restAPI, changeNumber, allowAnyUser) {
      this._changeNumber = changeNumber;
      this._allowAnyUser = allowAnyUser;
      this._restAPI = restAPI;
    }

    init() {
      if (this._initPromise) {
        return this._initPromise;
      }
      const getConfigPromise = this._restAPI.getConfig().then(cfg => {
        this._config = cfg;
      });
      const getLoggedInPromise = this._restAPI.getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
      });
      this._initPromise = Promise.all([getConfigPromise, getLoggedInPromise])
        .then(() => {
          this._initialized = true;
        });
      return this._initPromise;
    }

    getSuggestions(input) {
      if (!this._initialized || !this._loggedIn) {
        return Promise.resolve([]);
      }
      const api = this._restAPI;
      const xhr = this._allowAnyUser ?
          api.getSuggestedAccounts(`cansee:${this._changeNumber} ${input}`) :
          api.getChangeSuggestedReviewers(this._changeNumber, input);

      return xhr.then(reviewers => (reviewers || []));
    }

    makeSuggestionItem(suggestion) {
      if (suggestion.account) {
        // Reviewer is an account suggestion from getChangeSuggestedReviewers.
        return {
          name: GrDisplayNameUtils.getAccountDisplayName(this._config,
              suggestion.account, false),
          value: suggestion,
        };
      }

      if (suggestion.group) {
        // Reviewer is a group suggestion from getChangeSuggestedReviewers.
        return {
          name: GrDisplayNameUtils.getGroupDisplayName(suggestion.group),
          value: suggestion,
        };
      }

      if (suggestion._account_id) {
        // Reviewer is an account suggestion from getSuggestedAccounts.
        return {
          name: GrDisplayNameUtils.getAccountDisplayName(this._config,
              suggestion, false),
          value: {account: suggestion, count: 1},
        };
      }
    }
  }

  window.GrReviewerSuggestionsProvider = GrReviewerSuggestionsProvider;
})(window);
