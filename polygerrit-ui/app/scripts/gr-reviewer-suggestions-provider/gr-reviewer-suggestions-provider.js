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
import {getAccountDisplayName, getGroupDisplayName} from '../../utils/display-name-util.js';

/**
 * @enum {string}
 */
export const SUGGESTIONS_PROVIDERS_USERS_TYPES = {
  REVIEWER: 'reviewers',
  CC: 'ccs',
  ANY: 'any',
};

export class GrReviewerSuggestionsProvider {
  static create(restApi, changeNumber, usersType) {
    switch (usersType) {
      case SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER:
        return new GrReviewerSuggestionsProvider(restApi, changeNumber,
            input => restApi.getChangeSuggestedReviewers(changeNumber,
                input));
      case SUGGESTIONS_PROVIDERS_USERS_TYPES.CC:
        return new GrReviewerSuggestionsProvider(restApi, changeNumber,
            input => restApi.getChangeSuggestedCCs(changeNumber, input));
      case SUGGESTIONS_PROVIDERS_USERS_TYPES.ANY:
        return new GrReviewerSuggestionsProvider(restApi, changeNumber,
            input => restApi.getSuggestedAccounts(
                `cansee:${changeNumber} ${input}`));
      default:
        throw new Error(`Unknown users type: ${usersType}`);
    }
  }

  constructor(restAPI, changeNumber, apiCall) {
    this._changeNumber = changeNumber;
    this._apiCall = apiCall;
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

    return this._apiCall(input)
        .then(reviewers => (reviewers || []));
  }

  makeSuggestionItem(suggestion) {
    if (suggestion.account) {
      // Reviewer is an account suggestion from getChangeSuggestedReviewers.
      return {
        name: getAccountDisplayName(this._config,
            suggestion.account),
        value: suggestion,
      };
    }

    if (suggestion.group) {
      // Reviewer is a group suggestion from getChangeSuggestedReviewers.
      return {
        name: getGroupDisplayName(suggestion.group),
        value: suggestion,
      };
    }

    if (suggestion._account_id) {
      // Reviewer is an account suggestion from getSuggestedAccounts.
      return {
        name: getAccountDisplayName(this._config,
            suggestion),
        value: {account: suggestion, count: 1},
      };
    }
  }
}
