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

    properties: {

      /**
       * Query for requesting autocomplete suggestions. The function should
       * accept the input as a string parameter and return a promise. The
       * promise should yield an array of suggestion objects with "name" and
       * "value" properties. The "name" property will be displayed in the
       * suggestion entry. The "value" property will be emitted if that
       * suggestion is selected.
       *
       * @type {function(String): Promise<Array<Object>>}
       */
      query: {
        type: Function,
        value: function() {
          return function() {
            return Promise.resolve([]);
          };
        },
      },

      /**
       * The number of characters that must be typed before suggestions are
       * made.
       */
      threshold: {
        type: Number,
        value: 1,
      },

      borderless: Boolean,
      disabled: Boolean,

      text: {
        type: String,
        value: '',
        observer: '_updateSuggestions',
        notify: true,
      },

      placeholder: String,

      clearOnCommit: {
        type: Boolean,
        value: false,
      },

      value: Object,

      /**
       * Multi mode appends autocompleted entries to the value.
       * If false, autocompleted entries replace value.
       */
      multi: {
        type: Boolean,
        value: false,
      },

      _suggestions: {
        type: Array,
        value: function() { return []; },
      },

      _index: Number,

      _disableSuggestions: {
        type: Boolean,
        value: false,
      },

    },

    attached: function() {
      this.listen(document.body, 'click', '_handleBodyClick');
    },

    detached: function() {
      this.unlisten(document.body, 'click', '_handleBodyClick');
    },

    get focusStart() {
      return this.$.input;
    },

    focus: function() {
      this.$.input.focus();
    },

    clear: function() {
      this.text = '';
    },

    /**
     * Set the text of the input without triggering the suggestion dropdown.
     * @param {String} text The new text for the input.
     */
    setText: function(text) {
      this._disableSuggestions = true;
      this.text = text;
      this._disableSuggestions = false;
    },

    _updateSuggestions: function() {
      if (!this.text || this._disableSuggestions) { return; }
      if (this.text.length < this.threshold) {
        this._suggestions = [];
        this.value = null;
        return;
      }
      var text = this.text;

      this.query(text).then(function(suggestions) {
        if (text !== this.text) {
          // Late response.
          return;
        }
        this._suggestions = suggestions;
        this.$.cursor.moveToStart();
        if (this._index === -1) {
          this.value = null;
        }
      }.bind(this));
    },

    _computeSuggestionsHidden: function(suggestions) {
      return !suggestions.length;
    },

    _computeClass: function(borderless) {
      return borderless ? 'borderless' : '';
    },

    _getSuggestionElems: function() {
      Polymer.dom.flush();
      return this.$.suggestions.querySelectorAll('li');
    },

    _handleInputKeydown: function(e) {
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
        case 13: // Enter
          e.preventDefault();
          this._commit();
          this._suggestions = [];
          break;
      }
    },

    _cancel: function() {
      this._suggestions = [];
      this.fire('cancel');
    },

    _updateValue: function(suggestions, index) {
      if (!suggestions.length || index === -1) { return; }
      var completed = suggestions[index].value;
      if (this.multi) {
        // Append the completed text to the end of the string.
        var shortStr = this.text.substring(0, this.text.lastIndexOf(' ') + 1);
        this.value = shortStr + completed;
      } else {
        this.value = completed;
      }
    },

    _handleBodyClick: function(e) {
      var eventPath = Polymer.dom(e).path;
      for (var i = 0; i < eventPath.length; i++) {
        if (eventPath[i] == this) {
          return;
        }
      }
      this._suggestions = [];
    },

    _handleSuggestionTap: function(e) {
      this.$.cursor.setCursor(e.target);
      this._commit();
    },

    _commit: function() {
      // Allow values that are not in suggestion list iff suggestions are empty.
      if (this._suggestions.length > 0) {
        this._updateValue(this._suggestions, this._index);
      } else {
        this.value = this.text;
      }

      var value = this.value;

      // Value and text are mirrors of each other in multi mode.
      if (this.multi) {
        this.setText(this.value);
      } else {
        if (!this.clearOnCommit && this._suggestions[this._index]) {
          this.setText(this._suggestions[this._index].name);
        } else {
          this.clear();
        }
      }

      this.fire('commit', {value: value});
    },
  });
})();
