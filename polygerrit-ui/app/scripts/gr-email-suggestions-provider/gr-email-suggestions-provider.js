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

  if (window.GrEmailSuggestionsProvider) {
    return;
  }

  class GrEmailSuggestionsProvider {
    constructor(restAPI) {
      this._restAPI = restAPI;
    }

    getSuggestions(input) {
      return this._restAPI.getSuggestedAccounts(`${input}`)
          .then(accounts => {
            if (!accounts) { return []; }
            return accounts;
          });
    }

    makeSuggestionItem(account) {
      return {
        name: GrDisplayNameUtils.getAccountDisplayName(null, account, true),
        value: {account, count: 1},
      };
    }
  }

  window.GrEmailSuggestionsProvider = GrEmailSuggestionsProvider;
})(window);
