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

  const MAX_ITEMS_DROPDOWN = 10;
  const VERTICAL_OFFSET = 7;

  const ALL_SUGGESTIONS = [
    {value: '💯', match: '100'},
    {value: '💔', match: 'broken heart'},
    {value: '🍺', match: 'beer'},
    {value: '✔', match: 'check'},
    {value: '😎', match: 'cool'},
    {value: '😕', match: 'confused'},
    {value: '😭', match: 'crying'},
    {value: '🔥', match: 'fire'},
    {value: '👊', match: 'fistbump'},
    {value: '🐨', match: 'koala'},
    {value: '😄', match: 'laugh'},
    {value: '🤓', match: 'glasses'},
    {value: '😆', match: 'grin'},
    {value: '😐', match: 'neutral'},
    {value: '👌', match: 'ok'},
    {value: '🎉', match: 'party'},
    {value: '💩', match: 'poop'},
    {value: '🙏', match: 'pray'},
    {value: '😞', match: 'sad'},
    {value: '😮', match: 'shock'},
    {value: '😊', match: 'smile'},
    {value: '😢', match: 'tear'},
    {value: '😂', match: 'tears'},
    {value: '😋', match: 'tongue'},
    {value: '👍', match: 'thumbs up'},
    {value: '👎', match: 'thumbs down'},
    {value: '😒', match: 'unamused'},
    {value: '😉', match: 'wink'},
    {value: '🍷', match: 'wine'},
    {value: '😜', match: 'winking tongue'},
  ];

  Polymer({
    is: 'gr-textarea',

    /**
     * @event bind-value-changed
     */

    properties: {
      autocomplete: Boolean,
      disabled: Boolean,
      rows: Number,
      maxRows: Number,
      placeholder: String,
      fixedPositionDropdown: Boolean,
      moveToRoot: Boolean,
      text: {
        type: String,
        notify: true,
        observer: '_handleTextChanged',
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
      /** @type(?number) */
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
      _suggestions: Array,
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      esc: '_handleEscKey',
      tab: '_handleEnterByKey',
      enter: '_handleEnterByKey',
      up: '_handleUpKey',
      down: '_handleDownKey',
    },

    ready() {
      this._resetEmojiDropdown();
      if (this.monospace) {
        this.classList.add('monospace');
      }
      if (this.hideBorder) {
        this.$.textarea.classList.add('noBorder');
      }
      if (this.backgroundColor) {
        this.updateStyles({'--background-color': this.backgroundColor});
      }
      this.listen(this.$.emojiSuggestions, 'dropdown-closed', '_resetAndFocus');
      this.listen(this.$.emojiSuggestions, 'item-selected',
          '_handleEmojiSelect');
    },

    detached() {
      this.closeDropdown();
      this.listen(this.$.emojiSuggestions, 'dropdown-closed', '_resetAndFocus');
      this.listen(this.$.emojiSuggestions, 'item-selected',
          '_handleEmojiSelect');
    },

    closeDropdown() {
      if (!this.$.emojiSuggestions.hidden) {
        this._closeEmojiDropdown();
      }
    },

    getNativeTextarea() {
      return this.$.textarea.textarea;
    },

    putCursorAtEnd() {
      const textarea = this.getNativeTextarea();
      // Put the cursor at the end always.
      textarea.selectionStart = textarea.value.length;
      textarea.selectionEnd = textarea.selectionStart;
      this.async(() => {
        textarea.focus();
      });
    },

    _handleEscKey(e) {
      if (this._hideAutocomplete) { return; }
      e.preventDefault();
      e.stopPropagation();
      this._resetAndFocus();
    },

    _resetAndFocus() {
      this._resetEmojiDropdown();
      this.$.textarea.textarea.focus();
    },

    _handleUpKey(e) {
      if (this._hideAutocomplete) { return; }
      e.preventDefault();
      e.stopPropagation();
      this.$.emojiSuggestions.cursorUp();
      this.$.textarea.textarea.focus();
    },

    _handleDownKey(e) {
      if (this._hideAutocomplete) { return; }
      e.preventDefault();
      e.stopPropagation();
      this.$.emojiSuggestions.cursorDown();
      this.$.textarea.textarea.focus();
    },

    _handleEnterByKey(e) {
      if (this._hideAutocomplete) { return; }
      e.preventDefault();
      e.stopPropagation();
      this.text = this._getText(this.$.emojiSuggestions.getCurrentText());
      this._resetEmojiDropdown();
    },

    _handleEmojiSelect(e) {
      this.text = this._getText(e.detail.selected.dataset.value);
      this._resetEmojiDropdown();
    },

    _getText(value) {
      return this.text.substr(0, this._colonIndex || 0) +
          value + this.text.substr(this.$.textarea.selectionStart) + ' ';
    },

    _getPositionOfCursor() {
      this.$.hiddenText.textContent = this.$.textarea.value.substr(0,
          this.$.textarea.selectionStart);

      const caratSpan = document.createElement('span');
      this.$.hiddenText.appendChild(caratSpan);
      return caratSpan.getBoundingClientRect();
    },

    _getFontSize() {
      const fontSizePx = getComputedStyle(this).fontSize || '12px';
      return parseInt(fontSizePx.substr(0, fontSizePx.length - 2),
          10);
    },

    _getScrollTop() {
      return document.body.scrollTop;
    },

    /**
     * This positions the dropdown to be just below the cursor position. It is
     * calculated by having a hidden element with the same width and styling of
     * the tetarea and the text up until the point of interest. Then a span
     * element is added to the end so that there is a specific element to get
     * the position of.  Line height is determined (or falls back to 12px) as
     * extra height to add.
     */
    _updateSelectorPosition() {
      // These are broken out into separate functions for testability.
      const caratPosition = this._getPositionOfCursor();
      const fontSize = this._getFontSize();

      let top = caratPosition.top + fontSize + VERTICAL_OFFSET;

      if (!this.fixedPositionDropdown) {
        top += this._getScrollTop();
      }
      top += 'px';
      const left = caratPosition.left + 'px';
      this.$.emojiSuggestions.setPosition(top, left);
    },

    /**
     * _handleKeydown used for key handling in the this.$.textarea AND all child
     * autocomplete options.
     */
    _onValueChanged(e) {
      // Relay the event.
      this.fire('bind-value-changed', e);

      // If cursor is not in textarea (just opened with colon as last char),
      // Don't do anything.
      if (!e.currentTarget.focused) { return; }
      const newChar = e.detail.value[this.$.textarea.selectionStart - 1];

      // When a colon is detected, set a colon index, but don't do anything else
      // yet.
      if (newChar === ':') {
        this._colonIndex = this.$.textarea.selectionStart - 1;
      // If the colon index exists, continue to determine what needs to be done
      // with the dropdown. It may be open or closed at this point.
      } else if (this._colonIndex !== null) {
        // The search string is a substring of the textarea's value from (1
        // position after) the colon index to the cursor position.
        this._currentSearchString = e.detail.value.substr(this._colonIndex + 1,
            this.$.textarea.selectionStart);
        // Under the following conditions, close and reset the dropdown:
        // - The cursor is no longer at the end of the current search string
        // - The search string is an space or new line
        // - The colon has been removed
        // - There are no suggestions that match the search string
        if (this.$.textarea.selectionStart !==
            this._currentSearchString.length + this._colonIndex + 1 ||
            this._currentSearchString === ' ' ||
            this._currentSearchString === '\n' ||
            !(e.detail.value[this._colonIndex] === ':') ||
            !this._suggestions.length) {
          this._resetEmojiDropdown();
        // Otherwise open the dropdown and set the position to be just below the
        // cursor.
        } else if (this.$.emojiSuggestions.hidden) {
          this._hideAutocomplete = false;
          this._openEmojiDropdown();
          this._updateSelectorPosition();
        }
        this.$.textarea.textarea.focus();
      }
    },

    _closeEmojiDropdown() {
      this.$.emojiSuggestions.close();
      this.$.emojiSuggestions.hidden = true;
    },

    _openEmojiDropdown() {
      this.$.emojiSuggestions.open();
      this.$.emojiSuggestions.hidden = false;
    },

    _formatSuggestions(matchedSuggestions) {
      const suggestions = [];
      for (const suggestion of matchedSuggestions) {
        suggestion.dataValue = suggestion.value;
        suggestion.text = suggestion.value + ' ' + suggestion.match;
        suggestions.push(suggestion);
      }
      this._suggestions = suggestions;
    },

    _determineSuggestions(emojiText) {
      if (!emojiText.length) {
        this._formatSuggestions(ALL_SUGGESTIONS);
      }
      const matches = ALL_SUGGESTIONS.filter(suggestion => {
        return suggestion.match.includes(emojiText);
      }).splice(0, MAX_ITEMS_DROPDOWN);
      this._formatSuggestions(matches);
    },

    _resetEmojiDropdown() {
      // hide and reset the autocomplete dropdown.
      Polymer.dom.flush();
      this._currentSearchString = '';
      this._hideAutocomplete = true;
      this.closeDropdown();
      this._colonIndex = null;
      this.$.textarea.textarea.focus();
    },

    _handleTextChanged(text) {
      this.dispatchEvent(
          new CustomEvent('value-changed', {detail: {value: text}}));
    },
  });
})();
