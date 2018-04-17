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

  const TOKENIZE_REGEX = /(?:[^\s"]+|"[^"]*")+/g;
  const DEBOUNCE_WAIT_MS = 200;

  Polymer({
    is: 'gr-autocomplete',

    /**
     * Fired when a value is chosen.
     *
     * @event commit
     */

    /**
     * Fired when the user cancels.
     *
     * @event cancel
     */

    /**
     * Fired on keydown to allow for custom hooks into autocomplete textbox
     * behavior.
     *
     * @event input-keydown
     */

    properties: {

      /**
       * Query for requesting autocomplete suggestions. The function should
       * accept the input as a string parameter and return a promise. The
       * promise should yield an array of suggestion objects with "name" and
       * "value" properties. The "name" property will be displayed in the
       * suggestion entry. The "value" property will be emitted if that
       * suggestion is selected.
       *
       * @type {function(string): Promise<?>}
       */
      query: {
        type: Function,
        value() {
          return function() {
            return Promise.resolve([]);
          };
        },
      },

      /**
       * The number of characters that must be typed before suggestions are
       * made. If threshold is zero, default suggestions are enabled.
       */
      threshold: {
        type: Number,
        value: 1,
      },

      allowNonSuggestedValues: Boolean,
      borderless: Boolean,
      underlined: {
        type: Boolean,
        value: false,
      },
      disabled: Boolean,
      showSearchIcon: {
        type: Boolean,
        value: false,
      },
      // Vertical offset needed for a 1em font-size with no vertical padding.
      // Inputs with additional padding will need to increase vertical offset.
      verticalOffset: {
        type: Number,
        value: 20,
      },

      text: {
        type: String,
        value: '',
        notify: true,
      },

      placeholder: String,

      clearOnCommit: {
        type: Boolean,
        value: false,
      },

      /**
       * When true, tab key autocompletes but does not fire the commit event.
       * When false, tab key not caught, and focus is removed from the element.
       * See Issue 4556, Issue 6645.
       */
      tabComplete: {
        type: Boolean,
        value: false,
      },

      value: {
        type: String,
        notify: true,
      },

      /**
       * Multi mode appends autocompleted entries to the value.
       * If false, autocompleted entries replace value.
       */
      multi: {
        type: Boolean,
        value: false,
      },

      /**
       * When true and uncommitted text is left in the autocomplete input after
       * blurring, the text will appear red.
       */
      warnUncommitted: {
        type: Boolean,
        value: false,
      },

      /**
       * When true, querying for suggestions is not debounced w/r/t keypresses
       */
      noDebounce: {
        type: Boolean,
        value: false,
      },

      /** @type {?} */
      _suggestions: {
        type: Array,
        value() { return []; },
      },

      _suggestionEls: {
        type: Array,
        value() { return []; },
      },

      _index: Number,
      _disableSuggestions: {
        type: Boolean,
        value: false,
      },
      _focused: {
        type: Boolean,
        value: false,
      },

      /** The DOM element of the selected suggestion. */
      _selected: Object,
    },

    observers: [
      '_maybeOpenDropdown(_suggestions, _focused)',
      '_updateSuggestions(text, threshold, noDebounce)',
    ],

    attached() {
      this.listen(document.body, 'tap', '_handleBodyTap');
    },

    detached() {
      this.unlisten(document.body, 'tap', '_handleBodyTap');
      this.cancelDebouncer('update-suggestions');
    },

    get focusStart() {
      return this.$.input;
    },

    focus() {
      this.$.input.focus();
    },

    selectAll() {
      const nativeInputElement = this.$.input.inputElement;
      if (!this.$.input.value) { return; }
      nativeInputElement.setSelectionRange(0, this.$.input.value.length);
    },

    clear() {
      this.text = '';
    },

    _handleItemSelect(e) {
      // Let _handleKeydown deal with keyboard interaction.
      if (e.detail.trigger !== 'tap') { return; }
      this._selected = e.detail.selected;
      this._commit();
    },

    get _inputElement() {
      return this.$.input;
    },

    /**
     * Set the text of the input without triggering the suggestion dropdown.
     * @param {string} text The new text for the input.
     */
    setText(text) {
      this._disableSuggestions = true;
      this.text = text;
      this._disableSuggestions = false;
    },

    _onInputFocus() {
      this._focused = true;
      this._updateSuggestions(this.text, this.threshold, this.noDebounce);
      this.$.input.classList.remove('warnUncommitted');
      // Needed so that --paper-input-container-input updated style is applied.
      this.updateStyles();
    },

    _onInputBlur() {
      this.$.input.classList.toggle('warnUncommitted',
          this.warnUncommitted && this.text.length && !this._focused);
      // Needed so that --paper-input-container-input updated style is applied.
      this.updateStyles();
    },

    _updateSuggestions(text, threshold, noDebounce) {
      if (this._disableSuggestions) { return; }
      if (text === undefined || text.length < threshold) {
        this._suggestions = [];
        this.value = '';
        return;
      }

      const update = () => {
        this.query(text).then(suggestions => {
          if (text !== this.text) {
            // Late response.
            return;
          }
          for (const suggestion of suggestions) {
            suggestion.text = suggestion.name;
          }
          this._suggestions = suggestions;
          Polymer.dom.flush();
          if (this._index === -1) {
            this.value = '';
          }
        });
      };

      if (noDebounce) {
        update();
      } else {
        this.debounce('update-suggestions', update, DEBOUNCE_WAIT_MS);
      }
    },

    _maybeOpenDropdown(suggestions, focused) {
      if (suggestions.length > 0 && focused) {
        return this.$.suggestions.open();
      }
      return this.$.suggestions.close();
    },

    _computeClass(borderless, underlined) {
      const classes = [];
      if (borderless) {
        classes.push('borderless')
      }
      if (underlined) {
        classes.push('underlined')
      }
      return classes.join(' ');
    },

    /**
     * _handleKeydown used for key handling in the this.$.input AND all child
     * autocomplete options.
     */
    _handleKeydown(e) {
      this._focused = true;
      switch (e.keyCode) {
        case 38: // Up
          e.preventDefault();
          this.$.suggestions.cursorUp();
          break;
        case 40: // Down
          e.preventDefault();
          this.$.suggestions.cursorDown();
          break;
        case 27: // Escape
          e.preventDefault();
          this._cancel();
          break;
        case 9: // Tab
          if (this._suggestions.length > 0 && this.tabComplete) {
            e.preventDefault();
            this._handleInputCommit(true);
            this.focus();
          } else {
            this._focused = false;
          }
          break;
        case 13: // Enter
          e.preventDefault();
          this._handleInputCommit();
          break;
        default:
          // For any normal keypress, return focus to the input to allow for
          // unbroken user input.
          this.$.input.inputElement.focus();
      }
      this.fire('input-keydown', {keyCode: e.keyCode, input: this.$.input});
    },

    _cancel() {
      if (this._suggestions.length) {
        this.set('_suggestions', []);
      } else {
        this.fire('cancel');
      }
    },

    /**
     * @param {boolean=} opt_tabComplete
     */
    _handleInputCommit(opt_tabComplete) {
      // Nothing to do if the dropdown is not open.
      if (!this.allowNonSuggestedValues
          && this.$.suggestions.isHidden) { return; }

      this._selected = this.$.suggestions.getCursorTarget();
      this._commit(opt_tabComplete);
    },

    _updateValue(suggestion, suggestions) {
      if (!suggestion) { return; }
      const completed = suggestions[suggestion.dataset.index].value;
      if (this.multi) {
        // Append the completed text to the end of the string.
        // Allow spaces within quoted terms.
        const tokens = this.text.match(TOKENIZE_REGEX);
        tokens[tokens.length - 1] = completed;
        this.value = tokens.join(' ');
      } else {
        this.value = completed;
      }
    },

    _handleBodyTap(e) {
      const eventPath = Polymer.dom(e).path;
      for (let i = 0; i < eventPath.length; i++) {
        if (eventPath[i] === this) {
          return;
        }
      }
      this._focused = false;
    },

    _handleSuggestionTap(e) {
      e.stopPropagation();
      this.$.cursor.setCursor(e.target);
      this._commit();
    },

    /**
     * Commits the suggestion, optionally firing the commit event.
     *
     * @param {boolean=} opt_silent Allows for silent committing of an
     *     autocomplete suggestion in order to handle cases like tab-to-complete
     *     without firing the commit event.
     */
    _commit(opt_silent) {
      // Allow values that are not in suggestion list iff suggestions are empty.
      if (this._suggestions.length > 0) {
        this._updateValue(this._selected, this._suggestions);
      } else {
        this.value = this.text || '';
      }

      const value = this.value;

      // Value and text are mirrors of each other in multi mode.
      if (this.multi) {
        this.setText(this.value);
      } else {
        if (!this.clearOnCommit && this._selected) {
          this.setText(this._suggestions[this._selected.dataset.index].name);
        } else {
          this.clear();
        }
      }

      this._suggestions = [];
      if (!opt_silent) {
        this.fire('commit', {value});
      }

      this._textChangedSinceCommit = false;
    },

    _computeShowSearchIconClass(showSearchIcon) {
      return showSearchIcon ? 'showSearchIcon' : '';
    },
  });
})();
