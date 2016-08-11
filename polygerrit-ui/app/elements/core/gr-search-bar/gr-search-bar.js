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

    // TODO(kaspern): Expand support for more complicated autocomplete features
    _getSearchSuggestions: function(input) {
      return Promise.resolve(SEARCH_OPERATORS).then(function(operators) {
        if (!operators) { return []; }
        var lowerCaseInput = input
            .substring(input.lastIndexOf(' ') + 1)
            .toLowerCase();
        return operators
            .filter(function(operator) {
              return operator.indexOf(lowerCaseInput) != -1;
            })
            // prioritize results that start with the input
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
