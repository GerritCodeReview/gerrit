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
    is: 'gr-email-suggestions-provider',

    properties: {
      getSuggestions: {
        type: Function,
        readOnly: true,
        value() {
          return this._getSuggestions.bind(this);
        },
      },

      makeSuggestionItem: {
        type: Function,
        readonly: true,
        value() {
          return this._makeSuggestionItem.bind(this);
        },
      },

      _loggedIn: Boolean,
    },

    behaviors: [
      Gerrit.AccountDisplayNameBehavior,
    ],

    _getSuggestions(input) {
      const api = this.$.restAPI;
      const xhr = api.getSuggestedAccounts(`${input}`);

      return xhr.then(accounts => {
        if (!accounts) { return []; }
        return accounts;
      });
    },

    _makeSuggestionItem(account) {
      return {
        name: this.getAccountDisplayName(account),
        value: {account, count: 1},
      };
    },
  });
})();
