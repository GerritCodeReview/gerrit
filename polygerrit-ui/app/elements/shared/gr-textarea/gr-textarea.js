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
    {value: '😊', match: 'smile :)'},
    {value: '👍', match: 'thumbs up'},
    {value: '😄', match: 'laugh :D'},
    {value: '🎉', match: 'party'},
    {value: '😞', match: 'sad :('},
    {value: '😂', match: 'tears :\')'},
    {value: '🙏', match: 'pray'},
    {value: '😐', match: 'neutral :|'},
    {value: '😮', match: 'shock :O'},
    {value: '👎', match: 'thumbs down'},
    {value: '😎', match: 'cool |;)'},
    {value: '😕', match: 'confused'},
    {value: '👌', match: 'ok'},
    {value: '🔥', match: 'fire'},
    {value: '👊', match: 'fistbump'},
    {value: '💯', match: '100'},
    {value: '💔', match: 'broken heart'},
    {value: '🍺', match: 'beer'},
    {value: '✔', match: 'check'},
    {value: '😋', match: 'tongue'},
    {value: '😭', match: 'crying :\'('},
    {value: '🐨', match: 'koala'},
    {value: '🤓', match: 'glasses'},
    {value: '😆', match: 'grin'},
    {value: '💩', match: 'poop'},
    {value: '😢', match: 'tear'},
    {value: '😒', match: 'unamused'},
    {value: '😉', match: 'wink ;)'},
    {value: '🍷', match: 'wine'},
    {value: '😜', match: 'winking tongue ;)'},
  ];

  /**
   * @appliesMixin Gerrit.FireMixin
   * @appliesMixin Gerrit.KeyboardShortcutMixin
   */
  class GrTextarea extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
    Gerrit.KeyboardShortcutBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-textarea'; }
    /**
     * @event bind-value-changed
     */

    static get properties() {
      return {
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
        hideBorder: {
          type: Boolean,
          value: false,
        },
        /** Text input should be rendered in monspace font.  */
        monospace: {
          type: Boolean,
          value: false,
        },
        /** Text input should be rendered in code font, which is smaller than the
          standard monospace font. */
        code: {
          type: Boolean,
          value: false,
        },
        /** @type(?number) */
        _colonIndex: Number,
        _currentSearchString: {
          type: String,
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
      };
    }

    get keyBindings() {
      return {
        esc: '_handleEscKey',
        tab: '_handleEnterByKey',
        enter: '_handleEnterByKey',
        up: '_handleUpKey',
        down: '_handleDownKey',
      };
    }

    ready() {
      super.ready();
      if (this.monospace) {
        this.classList.add('monospace');
      }
      if (this.code) {
        this.classList.add('code');
      }
      if (this.hideBorder) {
        this.$.textarea.classList.add('noBorder');
      }
    }

    closeDropdown() {
      return this.$.emojiSuggestions.close();
    }

    getNativeTextarea() {
      return this.$.textarea.textarea;
    }

    putCursorAtEnd() {
      const textarea = this.getNativeTextarea();
      // Put the cursor at the end always.
      textarea.selectionStart = textarea.value.length;
      textarea.selectionEnd = textarea.selectionStart;
      this.async(() => {
        textarea.focus();
      });
    }

    _handleEscKey(e) {
      if (this._hideAutocomplete) { return; }
      e.preventDefault();
      e.stopPropagation();
      this._resetEmojiDropdown();
    }

    _handleUpKey(e) {
      if (this._hideAutocomplete) { return; }
      e.preventDefault();
      e.stopPropagation();
      this.$.emojiSuggestions.cursorUp();
      this.$.textarea.textarea.focus();
      this.disableEnterKeyForSelectingEmoji = false;
    }

    _handleDownKey(e) {
      if (this._hideAutocomplete) { return; }
      e.preventDefault();
      e.stopPropagation();
      this.$.emojiSuggestions.cursorDown();
      this.$.textarea.textarea.focus();
      this.disableEnterKeyForSelectingEmoji = false;
    }

    _handleEnterByKey(e) {
      if (this._hideAutocomplete || this.disableEnterKeyForSelectingEmoji) {
        return;
      }
      e.preventDefault();
      e.stopPropagation();
      this._setEmoji(this.$.emojiSuggestions.getCurrentText());
    }

    _handleEmojiSelect(e) {
      this._setEmoji(e.detail.selected.dataset.value);
    }

    _setEmoji(text) {
      const colonIndex = this._colonIndex;
      this.text = this._getText(text);
      this.$.textarea.selectionStart = colonIndex + 1;
      this.$.textarea.selectionEnd = colonIndex + 1;
      this.$.reporting.reportInteraction('select-emoji');
      this._resetEmojiDropdown();
    }

    _getText(value) {
      return this.text.substr(0, this._colonIndex || 0) +
          value + this.text.substr(this.$.textarea.selectionStart);
    }

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
    }

    _getFontSize() {
      const fontSizePx = getComputedStyle(this).fontSize || '12px';
      return parseInt(fontSizePx.substr(0, fontSizePx.length - 2),
          10);
    }

    _getScrollTop() {
      return document.body.scrollTop;
    }

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

      const charAtCursor = e.detail && e.detail.value ?
        e.detail.value[this.$.textarea.selectionStart - 1] : '';
      if (charAtCursor !== ':' && this._colonIndex == null) { return; }

      // When a colon is detected, set a colon index. We are interested only on
      // colons after space or in beginning of textarea
      if (charAtCursor === ':') {
        if (this.$.textarea.selectionStart < 2 ||
            e.detail.value[this.$.textarea.selectionStart - 2] === ' ') {
          this._colonIndex = this.$.textarea.selectionStart - 1;
        }
      }

      this._currentSearchString = e.detail.value.substr(this._colonIndex + 1,
          this.$.textarea.selectionStart - this._colonIndex - 1);
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

    _openEmojiDropdown() {
      this.$.emojiSuggestions.open();
      this.$.reporting.reportInteraction('open-emoji-dropdown');
    }

    _formatSuggestions(matchedSuggestions) {
      const suggestions = [];
      for (const suggestion of matchedSuggestions) {
        suggestion.dataValue = suggestion.value;
        suggestion.text = suggestion.value + ' ' + suggestion.match;
        suggestions.push(suggestion);
      }
      this.set('_suggestions', suggestions);
    }

    _determineSuggestions(emojiText) {
      if (!emojiText.length) {
        this._formatSuggestions(ALL_SUGGESTIONS);
        this.disableEnterKeyForSelectingEmoji = true;
      } else {
        const matches = ALL_SUGGESTIONS.filter(suggestion => {
          return suggestion.match.includes(emojiText);
        }).slice(0, MAX_ITEMS_DROPDOWN);
        this._formatSuggestions(matches);
        this.disableEnterKeyForSelectingEmoji = false;
      }
    }

    _resetEmojiDropdown() {
      // hide and reset the autocomplete dropdown.
      Polymer.dom.flush();
      this._currentSearchString = '';
      this._hideAutocomplete = true;
      this.closeDropdown();
      this._colonIndex = null;
      this.$.textarea.textarea.focus();
    }

    _handleTextChanged(text) {
      this.dispatchEvent(
          new CustomEvent('value-changed', {detail: {value: text}}));
    }
  }

  customElements.define(GrTextarea.is, GrTextarea);
})();
