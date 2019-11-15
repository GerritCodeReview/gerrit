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

  // Possible static search options for auto complete, without negations.
  const SEARCH_OPERATORS = [
    'added:',
    'age:',
    'age:1week', // Give an example age
    'assignee:',
    'author:',
    'branch:',
    'bug:',
    'cc:',
    'cc:self',
    'change:',
    'comment:',
    'commentby:',
    'commit:',
    'committer:',
    'conflicts:',
    'deleted:',
    'delta:',
    'dir:',
    'directory:',
    'ext:',
    'extension:',
    'file:',
    'footer:',
    'from:',
    'has:',
    'has:draft',
    'has:edit',
    'has:star',
    'has:stars',
    'has:unresolved',
    'hashtag:',
    'intopic:',
    'is:',
    'is:abandoned',
    'is:assigned',
    'is:closed',
    'is:ignored',
    'is:mergeable',
    'is:merged',
    'is:open',
    'is:owner',
    'is:private',
    'is:reviewed',
    'is:reviewer',
    'is:starred',
    'is:submittable',
    'is:watched',
    'is:wip',
    'label:',
    'message:',
    'onlyexts:',
    'onlyextensions:',
    'owner:',
    'ownerin:',
    'parentproject:',
    'project:',
    'projects:',
    'query:',
    'ref:',
    'reviewedby:',
    'reviewer:',
    'reviewer:self',
    'reviewerin:',
    'size:',
    'star:',
    'status:',
    'status:abandoned',
    'status:closed',
    'status:merged',
    'status:open',
    'status:reviewed',
    'submissionid:',
    'topic:',
    'tr:',
  ];

  // All of the ops, with corresponding negations.
  const SEARCH_OPERATORS_WITH_NEGATIONS =
    SEARCH_OPERATORS.concat(SEARCH_OPERATORS.map(op => `-${op}`));

  const MAX_AUTOCOMPLETE_RESULTS = 10;

  const TOKENIZE_REGEX = /(?:[^\s"]+|"[^"]*")+\s*/g;

  /**
    * @appliesMixin Gerrit.KeyboardShortcutMixin
    * @appliesMixin Gerrit.URLEncodingMixin
    */
  class GrSearchBar extends Polymer.mixinBehaviors( [
    Gerrit.KeyboardShortcutBehavior,
    Gerrit.URLEncodingBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-search-bar'; }
    /**
     * Fired when a search is committed
     *
     * @event handle-search
     */

    static get properties() {
      return {
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
        projectSuggestions: {
          type: Function,
          value() {
            return () => Promise.resolve([]);
          },
        },
        groupSuggestions: {
          type: Function,
          value() {
            return () => Promise.resolve([]);
          },
        },
        accountSuggestions: {
          type: Function,
          value() {
            return () => Promise.resolve([]);
          },
        },
        _inputVal: String,
        _threshold: {
          type: Number,
          value: 1,
        },
      };
    }

    keyboardShortcuts() {
      return {
        [this.Shortcut.SEARCH]: '_handleSearch',
      };
    }

    _valueChanged(value) {
      this._inputVal = value;
    }

    _handleInputCommit(e) {
      this._preventDefaultAndNavigateToInputVal(e);
    }

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
      const trimmedInput = this._inputVal && this._inputVal.trim();
      if (trimmedInput) {
        const predefinedOpOnlyQuery = SEARCH_OPERATORS_WITH_NEGATIONS.some(
            op => {
              return op.endsWith(':') && op === trimmedInput;
            }
        );
        if (predefinedOpOnlyQuery) {
          return;
        }
        this.dispatchEvent(new CustomEvent('handle-search', {
          detail: {inputVal: this._inputVal},
        }));
      }
    }

    /**
     * Determine what array of possible suggestions should be provided
     *     to _getSearchSuggestions.
     * @param {string} input - The full search term, in lowercase.
     * @return {!Promise} This returns a promise that resolves to an array of
     *     suggestion objects.
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
          return this.groupSuggestions(predicate, expression);

        case 'parentproject':
        case 'project':
          // Fetch projects.
          return this.projectSuggestions(predicate, expression);

        case 'author':
        case 'cc':
        case 'commentby':
        case 'committer':
        case 'from':
        case 'owner':
        case 'reviewedby':
        case 'reviewer':
          // Fetch accounts.
          return this.accountSuggestions(predicate, expression);

        default:
          return Promise.resolve(SEARCH_OPERATORS_WITH_NEGATIONS
              .filter(operator => operator.includes(input))
              .map(operator => ({text: operator})));
      }
    }

    /**
     * Get the sorted, pruned list of suggestions for the current search query.
     * @param {string} input - The complete search query.
     * @return {!Promise} This returns a promise that resolves to an array of
     *     suggestions.
     */
    _getSearchSuggestions(input) {
      // Allow spaces within quoted terms.
      const tokens = input.match(TOKENIZE_REGEX);
      const trimmedInput = tokens[tokens.length - 1].toLowerCase();

      return this._fetchSuggestions(trimmedInput)
          .then(suggestions => {
            if (!suggestions || !suggestions.length) { return []; }
            return suggestions
                // Prioritize results that start with the input.
                .sort((a, b) => {
                  const aContains = a.text.toLowerCase().indexOf(trimmedInput);
                  const bContains = b.text.toLowerCase().indexOf(trimmedInput);
                  if (aContains === bContains) {
                    return a.text.localeCompare(b.text);
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
                .map(({text, label}) => {
                  return {
                    name: text,
                    value: text,
                    label,
                  };
                });
          });
    }

    _handleSearch(e) {
      const keyboardEvent = this.getKeyboardEvent(e);
      if (this.shouldSuppressKeyboardShortcut(e) ||
          (this.modifierPressed(e) && !keyboardEvent.shiftKey)) { return; }

      e.preventDefault();
      this.$.searchInput.focus();
      this.$.searchInput.selectAll();
    }
  }

  customElements.define(GrSearchBar.is, GrSearchBar);
})();
