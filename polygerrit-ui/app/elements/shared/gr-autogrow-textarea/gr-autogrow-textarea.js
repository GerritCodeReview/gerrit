// Copyright (C) 2017 The Android Open Source Project
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
    is: 'gr-autogrow-textarea',

    properties: {
      emoji: {
        type: Object,
        value:{
            happy: "😁"
          },
      },
      _index: Number,
      _hideAutocomplete: {
        type: Boolean,
        value: false,
      },
      _colonCount: Number,
      _emojiText: String,
      _suggestions: {
        type: Array,
        value: [{name: 'smile 😁', value: 'smile'}, {name: 'happy 😁', value: 'happy'}],
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    // keyBindings: {
    //   ':': '_handleColonKey',
    // },

    ready: function() {
      this.$.textarea.addEventListener('keypress', function(e) {
        this._handleAutoCompleteUpdate(e)
      }.bind(this));
    },

    /**
     * _handleKeydown used for key handling in the this.$.input AND all child
     * autocomplete options.
     */
    _handleKeydown: function(e) {
      this._focused = true;
      switch (e.keyCode) {
        case 38: // Up
          e.preventDefault();
          this.$.cursor.previous();
          break;
        case 40: // Down
          e.preventDefault();
          this.$.cursor.next();
          break;
        case 27: // Escape
          e.preventDefault();
          this._cancel();
          break;
        case 9: // Tab
          if (this._suggestions.length > 0) {
            e.preventDefault();
            this._commit(this.tabCompleteWithoutCommit);
          }
          break;
        case 13: // Enter
          e.preventDefault();
          this._commit();
          break;
        default:
          // For any normal keypress, return focus to the input to allow for
          // unbroken user input.
          this.$.textarea.focus();
      }
      this.fire('input-keydown', {keyCode: e.keyCode, input: this.$.textarea});
    },

    _updateSuggestions: function(e) {
      console.log(e)
    },

    _onInputFocus: function() {
      this._focused = true;
      this._updateSuggestions();
    },

    _handleColonKey: function(e) {
      this._openAutoComplete(e);
    },

    _openAutoComplete: function(e) {
      this._position = this.draft ? this.draft.length : 0;
      // this.$.autocomplete.query = this.query;
      this._emojiText = "";
      // this._hideAutocomplete = false;
      // this.$.autocomplete.focus();
    },

    _handleSuggestionTap: function(e) {
      e.stopPropagation();
      this.$.cursor.setCursor(e.target);
      //this._commit();
      this.focus();
    },

    _handleAutoCompleteUpdate: function(e){
      // colon
      if (e.keyCode == 58){
        if (this._colonCount == 1) {
          if (this.emoji[this._emojiText]) {
            e.preventDefault();
            this.draft = this.draft + this.emoji[this._emojiText];
          }
        //back space
        } else if (e.keycode == 46) {

        } else {
          this._emojiText = "";
          this._position = this.draft ? this.draft.length : 0;
          this._colonCount = 1;
        }


      } else {
        this._emojiText += e.key;
      }
    },

    _handleSelectEmoji: function(e) {
      console.log(e);
    },
  });
})();
