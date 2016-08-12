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
  var SEARCH_OPERATORS = [
    'age',
    'age:1week', // Give an example age
    'change',
    'owner',
    'ownerin',
    'author',
    'committer',
    'reviewer',
    'reviewer:self',
    'reviewerin',
    'reviewedby',
    'commit',
    'comment',
    'message',
    'commentby',
    'from',
    'file',
    'conflicts',
    'project',
    'projects',
    'parentproject',
    'branch',
    'topic',
    'intopic',
    'ref',
    'tr',
    'bug',
    'label',
    'query',
    'has',
    'has:draft',
    'has:edit',
    'has:star',
    'has:stars',
    'star',
    'is',
    'is:starred',
    'is:watched',
    'is:reviewed',
    'is:owner',
    'is:reviewer',
    'is:open',
    'is:pending',
    'is:draft',
    'is:closed',
    'is:merged',
    'is:abandoned',
    'is:mergeable',
    'status',
    'status:open',
    'status:pending',
    'status:reviewed',
    'status:closed',
    'status:merged',
    'status:abandoned',
    'status:draft',
    'added',
    'deleted',
    'delta',
    'size',
  ];

  var MAX_AUTOCOMPLETE_RESULTS = 10;

  Polymer({
    is: 'gr-search-bar',

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    listeners: {
      'searchButton.tap': '_preventDefaultAndNavigateToInputVal',
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
        value: function() { return document.body; },
      },
      query: {
        type: Function,
        value: function() {
          return this._getSearchSuggestions.bind(this);
        },
      },
      _inputVal: String,
    },

    _valueChanged: function(value) {
      this._inputVal = value;
    },

    _handleInputCommit: function(e) {
      this._preventDefaultAndNavigateToInputVal(e);
    },

    _preventDefaultAndNavigateToInputVal: function(e) {
      e.preventDefault();
      Polymer.dom(e).rootTarget.blur();
      // @see Issue 4255.
      page.show('/q/' + encodeURIComponent(encodeURIComponent(this._inputVal)));
    },

    /**
     * Determines what array of possible suggestions should be provided
     * to _getSearchSuggestions.
     * @param {string} input - The full search term.
     * @return {promise} This returns a promise that resolves to an array of
     *     strings.
     */
    _promiseSearchSuggestions: function(input) {
      // Split the input on colon to get a two part predicate/expression.
      var splitInput = input.split(':');
      var predicate = splitInput[0];
      var expression = splitInput[1] || '';
      // Switch on the predicate to determine what to autocomplete.
      switch (predicate) {
        case 'ownerin':
        case 'reviewerin':
          // Fetch groups.
          if (expression.length === 0) { return Promise.resolve([]); }
          var xhr = this.$.restAPI.getSuggestedGroups(
              expression,
              MAX_AUTOCOMPLETE_RESULTS);
          return xhr.then(function(groups) {
            if (!groups) { return []; }
            var keys = Object.keys(groups);
            return keys.map(function(key) { return predicate + ':' + key });
          });

        case 'parentproject':
        case 'project':
          // Fetch projects.
          var xhr = this.$.restAPI.getSuggestedProjects(
              expression,
              MAX_AUTOCOMPLETE_RESULTS);
          return xhr.then(function(projects) {
            if (!projects) { return []; }
            var keys = Object.keys(projects);
            return keys.map(function(key) { return predicate + ':' + key });
          });

        case 'author':
        case 'commentby':
        case 'committer':
        case 'from':
        case 'owner':
        case 'reviewedby':
        case 'reviewer':
          // Fetch accounts.
          if (expression.length === 0) { return Promise.resolve([]); }
          var xhr = this.$.restAPI.getSuggestedAccounts(
              expression,
              MAX_AUTOCOMPLETE_RESULTS);
          return xhr.then(function(accounts) {
            if (!accounts) { return []; }
            return accounts.map(function(acct) {
              return predicate + ':"' + acct.name + ' <' + acct.email + '>"';
            });
          });

        default:
          return Promise.resolve(SEARCH_OPERATORS
              .filter(function(operator) {
                return operator.indexOf(input) !== -1;
              }));
      }
    },

    _getSearchSuggestions: function(input) {
      // Allow spaces within quoted terms.
      var tokens = input.match(/(?:[^\s"]+|"[^"]*")+/g);
      var trimmedInput = tokens[tokens.length - 1].toLowerCase();

      return this._promiseSearchSuggestions(trimmedInput)
          .then(function(operators) {
            if (!operators) { return []; }
            return operators
                // Disallow autocomplete values that exactly match the str.
                .filter(function(operator) {
                  return input.indexOf(operator.toLowerCase()) == -1;
                })
                // Prioritize results that start with the input.
                .sort(function(operator) {
                  return operator.indexOf(trimmedInput);
                })
                // Return only the first {MAX_AUTOCOMPLETE_RESULTS} results
                .slice(0, MAX_AUTOCOMPLETE_RESULTS - 1)
                // Map to an object to play nice with gr-autocomplete
                .map(function(operator) {
                  return {
                    name: operator,
                    value: operator,
                  };
                });
          }.bind(this));
    },

    _handleKey: function(e) {
      if (this.shouldSupressKeyboardShortcut(e)) { return; }
      switch (e.keyCode) {
        case 191:  // '/' or '?' with shift key.
          // TODO(andybons): Localization using e.key/keypress event.
          if (e.shiftKey) { break; }
          e.preventDefault();
          var s = this.$.searchInput;
          s.focus();
          s.setSelectionRange(0, s.value.length);
          break;
      }
    },
  });
})();
