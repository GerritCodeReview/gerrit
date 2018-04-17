/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

  const MAX_ITEMS_DROPDOWN = 10;

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
      text: {
        type: String,
        notify: true,
        observer: '_handleTextChanged',
      },
      backgroundColor: {
        type: String,
        value: 'var(--view-background-color)',
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
      // Offset makes dropdown appear below text.
      _verticalOffset: {
        type: Number,
        value: 20,
        readOnly: true,
      },
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
    },

    closeDropdown() {
      return this.$.emojiSuggestions.close();
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
      this._resetEmojiDropdown();
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
    /**
     * Uses a hidden element with the same width and styling of the textarea and
     * the text up until the point of interest. Then caratSpan element is added
     * to the end and is set to be the positionTarget for the dropdown. Together
     * this allows the dropdown to appear near where the user is typing.
     */
    _updateCaratPosition() {
      this._hideAutocomplete = false;
      this.$.hiddenText.textContent = this.$.textarea.value.substr(0,
          this.$.textarea.selectionStart);

      const caratSpan = this.$.caratSpan;
      this.$.hiddenText.appendChild(caratSpan);
      this.$.emojiSuggestions.positionTarget = caratSpan;
      this._openEmojiDropdown();
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
        } else if (this.$.emojiSuggestions.isHidden) {
          this._updateCaratPosition();
        }
        this.$.textarea.textarea.focus();
      }
    },
    _openEmojiDropdown() {
      this.$.emojiSuggestions.open();
    },

    _formatSuggestions(matchedSuggestions) {
      const suggestions = [];
      for (const suggestion of matchedSuggestions) {
        suggestion.dataValue = suggestion.value;
        suggestion.text = suggestion.value + ' ' + suggestion.match;
        suggestions.push(suggestion);
      }
      this.set('_suggestions', suggestions);
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
