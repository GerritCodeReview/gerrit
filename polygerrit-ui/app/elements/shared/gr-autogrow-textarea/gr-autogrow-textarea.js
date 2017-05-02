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

  var MAX_ITEMS_DROPDOWN = 10;

  Polymer({
    is: 'gr-autogrow-textarea',

    properties: {
      autocomplete: Boolean,
      disabled: Boolean,
      rows: Number,
      maxRows: Number,
      placeholder: String,
      text: {
        type: String,
        notify: true,
      },
      backgroundColor: {
        type: String,
        value: '#fff',
      },
      hideBorder: {
        type: Boolean,
        value: false,
      },
      monospace: {
        type: Boolean,
        value: false,
      },
      _allSuggestions: {
        type: Object,
        value: [
          { value: '💯', match: '100' },
          { value: '💔', match: 'broken heart' },
          { value: '🍺', match: 'beer' },
          { value: '✔', match: 'check' },
          { value: '😎', match: 'cool' },
          { value: '😕', match: 'confused' },
          { value: '😭', match: 'crying' },
          { value: '🔥', match: 'fire' },
          { value: '👊', match: 'fistbump' },
          { value: '🐨', match: 'koala' },
          { value: '😄', match: 'laugh' },
          { value: '🤓', match: 'glasses' },
          { value: '😆', match: 'grin' },
          { value: '😐', match: 'neutral' },
          { value: '👌', match: 'ok' },
          { value: '🎉', match: 'party' },
          { value: '💩', match: 'poop' },
          { value: '🙏', match: 'pray' },
          { value: '😞', match: 'sad' },
          { value: '😮', match: 'shock' },
          { value: '😊', match: 'smile' },
          { value: '😢', match: 'tear' },
          { value: '😂', match: 'tears' },
          { value: '😋', match: 'tongue' },
          { value: '👍', match: 'thumbs up' },
          { value: '👎', match: 'thumbs down' },
          { value: '😒', match: 'unamused' },
          { value: '😉', match: 'wink' },
          { value: '🍷', match: 'wine' },
          { value: '😜', match: 'winking tongue' },
        ],

      },
      _colonIndex: Number,
      _currentSearchString: {
        type: String,
        value: '',
        observer: '_determineSuggestions',
      },
      _hideAutocomplete: {
        type: Boolean,
        value: true,
      },
      _index: Number,
      _suggestions: {
        type: Array,
      },
      _selected: {
        type: Object,
        observer: '_handleEmojiSelect',
        notify: true,
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      'esc': '_handleEscKey',
      'tab': '_handleEnterByKey',
      'enter': '_handleEnterByKey',
      'up': '_handleUpKey',
      'down': '_handleDownKey',
    },

    ready: function() {
      this._resetEmojiDropdown();
      if (this.monospace) {
        this.$.textarea.classList.add('monospace');
        this.$.hiddenText.classList.add('monospace');
      }
      if (this.hideBorder) {
        this.$.textarea.classList.add('no-border');
      }
      if (this.backgroundColor) {
        this.customStyle['--background-color'] = this.backgroundColor;
        this.updateStyles();
      }
      this.listen(this.$.emojiSuggestions, 'dropdown-closed', '_resetAndFocus');
    },

    detached: function() {
      this.closeDropdown();
    },

    closeDropdown: function() {
      if (this.$.emojiSuggestions.isOpen) {
        this.$.emojiSuggestions.close();
      }
    },

    _handleEscKey: function(e) {
      if (!this._hideAutocomplete) {
        e.preventDefault();
        e.stopPropagation();
        this._resetAndFocus();
      }
    },

    _resetAndFocus: function() {
      this._resetEmojiDropdown();
      this.$.textarea.textarea.focus();
    },

    _handleUpKey: function(e) {
      if (!this._hideAutocomplete) {
        this.$.emojiSuggestions.up(e);
      }
    },

    _handleDownKey: function(e) {
      if (!this._hideAutocomplete) {
        e.preventDefault();
        e.stopPropagation();
        this.$.emojiSuggestions.down(e);
      }
    },

    _handleEnterByKey: function(e) {
      if (!this._hideAutocomplete) {
        e.preventDefault();
        e.stopPropagation();
        this._selected = this.$.emojiSuggestions.getCursorTarget();
      }
    },

    _handleEmojiSelect: function(selectedItem) {

      this.text = this.text.substr(0, this._colonIndex) +
          selectedItem.dataset.value +
          this.text.substr(this.$.textarea.selectionStart) + ' ';
      this._resetEmojiDropdown();
    },

    _getPositionOfCursor: function() {
      this.$.hiddenText.textContent = this.$.textarea.value.substr(0 ,
              this.$.textarea.selectionStart);

      var caratSpan = document.createElement('span');
      this.$.hiddenText.appendChild(caratSpan);
      return caratSpan.getBoundingClientRect();
    },

    _getFontSize: function() {
      var fontSizePx = getComputedStyle(this).fontSize || '12px';
      return parseInt(fontSizePx.substr(0, fontSizePx.length - 2),
          10);
    },

    /**
     * This positions the dropdown to be just below the cursor position. It is
     * calculated by having a hidden element with the same width and styling of
     * the tetarea and the text up until the point of interest. Then a span
     * element is added to the end so that there is a specific element to get
     * the position of.  Line height is determined (or falls back to 12px) as
     * extra height to add.
     */
    _updateSelectorPosition: function() {
      // a little bit of extra height when placing the selector.
      var EXTRA_HEIGHT = 7;

      // These are broken out into separate functions for testability.
      var caratPosition = this._getPositionOfCursor();
      var fontSize = this._getFontSize();

      var top = caratPosition.top + fontSize +
        EXTRA_HEIGHT + 'px';
      var left = this.$.emojiSuggestions.style.left = caratPosition.left + 'px';
      this.$.emojiSuggestions.setPosition(top, left);
    },

    /**
     * _handleKeydown used for key handling in the this.$.textarea AND all child
     * autocomplete options.
     */
    _onValueChanged: function(e) {
      // If cursor is not in textarea (just opened with colon as last char),
      // Don't do anything.
      if (!e.currentTarget.focused) { return }
      var newChar = e.detail.value[this.$.textarea.selectionStart - 1];

      if (newChar == ':') {
        this._colonIndex = this.$.textarea.selectionStart - 1;
      } else if (this._colonIndex !== null) {
        this._currentSearchString =
            e.detail.value.substr(this._colonIndex + 1,
                this.$.textarea.selectionStart);
        if (this.$.textarea.selectionStart !==
            this._currentSearchString.length + this._colonIndex + 1 ||
            this._currentSearchString == ' ' ||
            this._currentSearchString == '\n' ||
            !e.detail.value[this._colonIndex] === ':' ||
            !this._suggestions.length) {
          this._resetEmojiDropdown();
        } else if (!this.$.emojiSuggestions.isOpen) {
            this._hideAutocomplete = false;
            this.$.emojiSuggestions.open();
            this._updateSelectorPosition();
        }
        this.$.textarea.textarea.focus();
      }
    },

    _formatSuggestions: function(matchedSuggestions) {
      var suggestions = [];
      matchedSuggestions.forEach(function(suggestion) {
        suggestion.text = suggestion.value + ' ' + suggestion.match;
        suggestions.push(suggestion);
      });
      this._suggestions = suggestions;
    },

    _determineSuggestions: function(emojiText) {
      if (!emojiText.length) {
        this._formatSuggestions(this._allSuggestions);
      }
      var matches = this._allSuggestions.filter(function(suggestion) {
        return suggestion.match.indexOf(emojiText) !== -1;
      }.bind(this)).splice(0, MAX_ITEMS_DROPDOWN);
      this._formatSuggestions(matches);
    },

    _resetEmojiDropdown: function() {
      // hide and reset the autocomplete dropdown.
      Polymer.dom.flush();
      this._currentSearchString = '';
      this._hideAutocomplete = true;
      this.closeDropdown();
      this._colonIndex = null;
      this.$.textarea.textarea.focus();
    },
  });
})();
