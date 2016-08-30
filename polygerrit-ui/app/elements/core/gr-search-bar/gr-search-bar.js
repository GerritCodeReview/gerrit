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
    'added',
    'age',
    'age:1week', // Give an example age
    'author',
    'branch',
    'bug',
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

    // TODO(kaspern): Flesh this out better.
    _makeSuggestion: function(str) {
      return {
        name: str,
        value: str,
      };
    },

    // TODO(kaspern): Expand support for more complicated autocomplete features.
    _getSearchSuggestions: function(input) {
      return Promise.resolve(SEARCH_OPERATORS).then(function(operators) {
        if (!operators) { return []; }
        var lowerCaseInput = input
            .substring(input.lastIndexOf(' ') + 1)
            .toLowerCase();
        return operators
            .filter(function(operator) {
              // Disallow autocomplete values that exactly match the whole str.
              var opContainsInput = operator.indexOf(lowerCaseInput) !== -1;
              var inputContainsOp = lowerCaseInput.indexOf(operator) !== -1;
              return opContainsInput && !inputContainsOp;
            })
            // Prioritize results that start with the input.
            .sort(function(operator) {
              return operator.indexOf(lowerCaseInput);
            })
            .map(this._makeSuggestion);
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
