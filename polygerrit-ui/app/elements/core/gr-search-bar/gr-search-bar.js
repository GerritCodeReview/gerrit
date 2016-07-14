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

  Polymer({
    is: 'gr-search-bar',

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    listeners: {
      'searchInput.keydown': '_inputKeyDownHandler',
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

      _inputVal: String,
    },

    _valueChanged: function(value) {
      this._inputVal = value;
    },

    _inputKeyDownHandler: function(e) {
      if (e.keyCode == 13) {  // Enter key
        this._preventDefaultAndNavigateToInputVal(e);
      }
    },

    _preventDefaultAndNavigateToInputVal: function(e) {
      e.preventDefault();
      Polymer.dom(e).rootTarget.blur();
      // @see Issue 4255.
      page.show('/q/' + encodeURIComponent(encodeURIComponent(this._inputVal)));
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
