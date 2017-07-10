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

  // Possible static search options for auto complete.
  const SEARCH_OPERATORS = [
    'added',
    'age',
    'age:1week', // Give an example age
    'author',
    'branch',
    'bug',
    'cc',
    'cc:self',
    'change',
    'comment',
    'commentby',
    'commit',
    'committer',
    'conflicts',
    'deleted',
    'delta',
    'file',
    'from',
    'has',
    'has:draft',
    'has:edit',
    'has:star',
    'has:stars',
    'intopic',
    'is',
    'is:abandoned',
    'is:closed',
    'is:draft',
    'is:ignored',
    'is:mergeable',
    'is:merged',
    'is:open',
    'is:owner',
    'is:pending',
    'is:reviewed',
    'is:reviewer',
    'is:starred',
    'is:watched',
    'label',
    'message',
    'owner',
    'ownerin',
    'parentproject',
    'project',
    'projects',
    'query',
    'ref',
    'reviewedby',
    'reviewer',
    'reviewer:self',
    'reviewerin',
    'size',
    'star',
    'status',
    'status:abandoned',
    'status:closed',
    'status:draft',
    'status:merged',
    'status:open',
    'status:pending',
    'status:reviewed',
    'topic',
    'tr',
  ];

  const SELF_EXPRESSION = 'self';
  const ME_EXPRESSION = 'me';

  const MAX_AUTOCOMPLETE_RESULTS = 10;

  const TOKENIZE_REGEX = /(?:[^\s"]+|"[^"]*")+\s*/g;

  Polymer({
    is: 'gr-search-bar',

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    listeners: {
      'searchButton.tap': '_preventDefaultAndNavigateToInputVal',
    },

    keyBindings: {
      '/': '_handleForwardSlashKey',
    },

    properties: {
      value: {
        type: String,
        value: '',
        notify: true,
        observer: '_valueChanged',
      },
      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },
      query: {
        type: Function,
        value() {
          return this._getSearchSuggestions.bind(this);
        },
      },
      _inputVal: String,
      _threshold: {
        type: Number,
        value: 1,
      },
    },

    _valueChanged(value) {
      this._inputVal = value;
    },

    _handleInputCommit(e) {
      this._preventDefaultAndNavigateToInputVal(e);
    },

    /**
     * This function is called in a few different cases:
     *   - e.target is the search button
     *   - e.target is the gr-autocomplete widget (#searchInput)
     *   - e.target is the input element wrapped within #searchInput
     *
     * @param {!Event} e
     */
    _preventDefaultAndNavigateToInputVal(e) {
      e.preventDefault();
      const target = Polymer.dom(e).rootTarget;
      // If the target is the #searchInput or has a sub-input component, that
      // is what holds the focus as opposed to the target from the DOM event.
      if (target.$.input) {
        target.$.input.blur();
      } else {
        target.blur();
      }
      if (this._inputVal) {
        page.show('/q/' + this.encodeURL(this._inputVal, false));
      }
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
    _fetchAccounts(predicate, expression) {
      if (expression.length === 0) { return Promise.resolve([]); }
      return this.$.restAPI.getSuggestedAccounts(
          expression,
          MAX_AUTOCOMPLETE_RESULTS)
          .then(accounts => {
            if (!accounts) { return []; }
            return accounts.map(acct =>
                predicate + ':"' + acct.name + ' <' + acct.email + '>"');
          }).then(accounts => {
            // When the expression supplied is a beginning substring of 'self',
            // add it as an autocomplete option.
            if (SELF_EXPRESSION.startsWith(expression)) {
              return accounts.concat([predicate + ':' + SELF_EXPRESSION]);
            } else if (ME_EXPRESSION.startsWith(expression)) {
              return accounts.concat([predicate + ':' + ME_EXPRESSION]);
            } else {
              return accounts;
            }
          });
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
    _fetchGroups(predicate, expression) {
      if (expression.length === 0) { return Promise.resolve([]); }
      return this.$.restAPI.getSuggestedGroups(
          expression,
          MAX_AUTOCOMPLETE_RESULTS)
          .then(groups => {
            if (!groups) { return []; }
            const keys = Object.keys(groups);
            return keys.map(key => predicate + ':' + key);
          });
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
    _fetchProjects(predicate, expression) {
      return this.$.restAPI.getSuggestedProjects(
          expression,
          MAX_AUTOCOMPLETE_RESULTS)
          .then(projects => {
            if (!projects) { return []; }
            const keys = Object.keys(projects);
            return keys.map(key => predicate + ':' + key);
          });
    },

    /**
     * Determine what array of possible suggestions should be provided
     *     to _getSearchSuggestions.
     * @param {string} input - The full search term, in lowercase.
     * @return {!Promise} This returns a promise that resolves to an array of
     *     strings.
     */
    _fetchSuggestions(input) {
      // Split the input on colon to get a two part predicate/expression.
      const splitInput = input.split(':');
      const predicate = splitInput[0];
      const expression = splitInput[1] || '';
      // Switch on the predicate to determine what to autocomplete.
      switch (predicate) {
        case 'ownerin':
        case 'reviewerin':
          // Fetch groups.
          return this._fetchGroups(predicate, expression);

        case 'parentproject':
        case 'project':
          // Fetch projects.
          return this._fetchProjects(predicate, expression);

        case 'author':
        case 'cc':
        case 'commentby':
        case 'committer':
        case 'from':
        case 'owner':
        case 'reviewedby':
        case 'reviewer':
          // Fetch accounts.
          return this._fetchAccounts(predicate, expression);

        default:
          return Promise.resolve(SEARCH_OPERATORS
              .filter(operator => operator.includes(input)));
      }
    },

    /**
     * Get the sorted, pruned list of suggestions for the current search query.
     * @param {string} input - The complete search query.
     * @return {!Promise} This returns a promise that resolves to an array of
     *     strings.
     */
    _getSearchSuggestions(input) {
      // Allow spaces within quoted terms.
      const tokens = input.match(TOKENIZE_REGEX);
      const trimmedInput = tokens[tokens.length - 1].toLowerCase();

      return this._fetchSuggestions(trimmedInput)
          .then(operators => {
            if (!operators || !operators.length) { return []; }
            return operators
                // Prioritize results that start with the input.
                .sort((a, b) => {
                  const aContains = a.toLowerCase().indexOf(trimmedInput);
                  const bContains = b.toLowerCase().indexOf(trimmedInput);
                  if (aContains === bContains) {
                    return a.localeCompare(b);
                  }
                  if (aContains === -1) {
                    return 1;
                  }
                  if (bContains === -1) {
                    return -1;
                  }
                  return aContains - bContains;
                })
                // Return only the first {MAX_AUTOCOMPLETE_RESULTS} results.
                .slice(0, MAX_AUTOCOMPLETE_RESULTS - 1)
                // Map to an object to play nice with gr-autocomplete.
                .map(operator => {
                  return {
                    name: operator,
                    value: operator,
                  };
                });
          });
    },

    _handleForwardSlashKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.searchInput.focus();
      this.$.searchInput.selectAll();
    },
  });
})();
