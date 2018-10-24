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

  const MAX_AUTOCOMPLETE_RESULTS = 10;
  const SELF_EXPRESSION = 'self';
  const ME_EXPRESSION = 'me';

  Polymer({
    is: 'gr-smart-search',

    properties: {
      searchQuery: String,
      _config: Object,
      _projectSuggestions: {
        type: Function,
        value() {
          return this._fetchProjects.bind(this);
        },
      },
      _groupSuggestions: {
        type: Function,
        value() {
          return this._fetchGroups.bind(this);
        },
      },
      _accountSuggestions: {
        type: Function,
        value() {
          return this._fetchAccounts.bind(this);
        },
      },
    },

    behaviors: [
      Gerrit.AnonymousNameBehavior,
    ],

    async attached() {
      this._config = await this.$.restAPI.getConfig();
    },

    _handleSearch(e) {
      const input = e.detail.inputVal;
      if (input) {
        Gerrit.Nav.navigateToSearchQuery(input);
      }
    },

    _accountOrAnon(name) {
      return this.getUserName(this._serverConfig, name, false);
    },

    /**
     * Fetch from the API the predicted projects.
     * @param {string} predicate - The first part of the search term, e.g.
     *     'project'
     * @param {string} expression - The second part of the search term, e.g.
     *     'gerr'
     * @return {!Promise} This returns a promise that resolves to an array of
     *     strings.
     */
    async _fetchProjects(predicate, expression) {
      const projects = await this.$.restAPI.getSuggestedProjects(
          expression,
          MAX_AUTOCOMPLETE_RESULTS);
      if (!projects) { return []; }
      const keys = Object.keys(projects);
      return keys.map(key => ({text: predicate + ':' + key}));
    },

    /**
     * Fetch from the API the predicted groups.
     * @param {string} predicate - The first part of the search term, e.g.
     *     'ownerin'
     * @param {string} expression - The second part of the search term, e.g.
     *     'polyger'
     * @return {!Promise} This returns a promise that resolves to an array of
     *     strings.
     */
    async _fetchGroups(predicate, expression) {
      if (expression.length === 0) { return []; }
      const groups = await this.$.restAPI.getSuggestedGroups(
          expression,
          MAX_AUTOCOMPLETE_RESULTS);
      if (!groups) { return []; }
      const keys = Object.keys(groups);
      return keys.map(key => ({text: predicate + ':' + key}));
    },

    /**
     * Fetch from the API the predicted accounts.
     * @param {string} predicate - The first part of the search term, e.g.
     *     'owner'
     * @param {string} expression - The second part of the search term, e.g.
     *     'kasp'
     * @return {!Promise} This returns a promise that resolves to an array of
     *     strings.
     */
    async _fetchAccounts(predicate, expression) {
      if (expression.length === 0) { return []; }
      const suggestions = await this.$.restAPI.getSuggestedAccounts(
          expression,
          MAX_AUTOCOMPLETE_RESULTS);
      if (!suggestions) { return []; }

      const accounts = this._mapAccountsHelper(suggestions, predicate);
      // When the expression supplied is a beginning substring of 'self',
      // add it as an autocomplete option.
      if (SELF_EXPRESSION.startsWith(expression)) {
        return accounts.concat(
            [{text: predicate + ':' + SELF_EXPRESSION}]);
      } else if (ME_EXPRESSION.startsWith(expression)) {
        return accounts.concat([{text: predicate + ':' + ME_EXPRESSION}]);
      } else {
        return accounts;
      }
    },

    _mapAccountsHelper(accounts, predicate) {
      return accounts.map(account => ({
        label: account.name || '',
        text: account.email ?
            `${predicate}:${account.email}` :
            `${predicate}:"${this._accountOrAnon(account)}"`,
      }));
    },
  });
})();
