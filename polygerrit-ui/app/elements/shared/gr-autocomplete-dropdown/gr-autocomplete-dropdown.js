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

  Polymer({
    is: 'gr-autocomplete-dropdown',

    /**
     * Fired when the dropdown is closed.
     *
     * @event dropdown-closed
     */

    /**
     * Fired when item is selected.
     *
     * @event item-selected
     */

    properties: {
      index: Number,
      isHidden: {
        type: Boolean,
        value: true,
        reflectToAttribute: true,
      },
      verticalOffset: {
        type: Number,
        value: null,
      },
      horizontalOffset: {
        type: Number,
        value: null,
      },
      suggestions: {
        type: Array,
        value: () => [],
        observer: '_resetCursorStops',
      },
      _suggestionEls: {
        type: Array,
        observer: '_resetCursorIndex',
      },
    },

    behaviors: [
      Gerrit.FireBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Polymer.IronFitBehavior,
    ],

    keyBindings: {
      up: '_handleUp',
      down: '_handleDown',
      enter: '_handleEnter',
      esc: '_handleEscape',
      tab: '_handleTab',
    },

    close() {
      this.isHidden = true;
    },

    open() {
      this.isHidden = false;
      this.refit();
      this._resetCursorStops();
      this._resetCursorIndex();
    },

    getCurrentText() {
      return this.getCursorTarget().dataset.value;
    },

    _handleUp(e) {
      if (!this.isHidden) {
        e.preventDefault();
        e.stopPropagation();
        this.cursorUp();
      }
    },

    _handleDown(e) {
      if (!this.isHidden) {
        e.preventDefault();
        e.stopPropagation();
        this.cursorDown();
      }
    },

    cursorDown() {
      if (!this.isHidden) {
        this.$.cursor.next();
      }
    },

    cursorUp() {
      if (!this.isHidden) {
        this.$.cursor.previous();
      }
    },

    _handleTab(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('item-selected', {
        trigger: 'tab',
        selected: this.$.cursor.target,
      });
    },

    _handleEnter(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('item-selected', {
        trigger: 'enter',
        selected: this.$.cursor.target,
      });
    },

    _handleEscape() {
      this._fireClose();
      this.close();
    },

    _handleClickItem(e) {
      e.preventDefault();
      e.stopPropagation();
      let selected = e.target;
      while (!selected.classList.contains('autocompleteOption')) {
        if (!selected || selected === this) { return; }
        selected = selected.parentElement;
      }
      this.fire('item-selected', {
        trigger: 'click',
        selected,
      });
    },

    _fireClose() {
      this.fire('dropdown-closed');
    },

    getCursorTarget() {
      return this.$.cursor.target;
    },

    _resetCursorStops() {
      if (this.suggestions.length > 0) {
        Polymer.dom.flush();
        // Polymer2: querySelectorAll returns NodeList instead of Array.
        this._suggestionEls = Array.from(
            this.$.suggestions.querySelectorAll('li'));
      } else {
        this._suggestionEls = [];
      }
    },

    _resetCursorIndex() {
      this.$.cursor.setCursorAtIndex(0);
    },

    _computeLabelClass(item) {
      return item.label ? '' : 'hide';
    },
  });
})();
