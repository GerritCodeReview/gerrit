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
    is: 'gr-autocomplete-dropdown',

    /**
     * Fired when the dropdown is closed
     *
     * @event dropdown-closed
     */

    /**
     * Fired when item is selected
     *
     * @event item-selected
     */

    properties: {
      isOpen: {
        type: Boolean,
        value: false,
      },
      moveToRoot: Boolean,
      suggestions: {
        type: Array,
        observer: 'resetCursorStops',
      },
      suggestionEls: {
        type: Array,
        observer: 'resetCursorIndex',
      },
    },

    behaviors: [
      Gerrit.BaseElementBehavior,
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      'up': 'up',
      'down': 'down',
      'enter': '_handleEnter',
      'esc': '_handleEscapeKey',
      'tab': '_handleTab',
    },

    close: function() {
      if (this.moveToRoot) {
        this.getBaseElement().removeChild(this);
      }
      this.isOpen = false;
    },

    open: function() {
      if (this.moveToRoot) {
        this.getBaseElement().appendChild(this);
      }
      this.resetCursorStops();
      this.resetCursorIndex();
      this.isOpen = true;
    },

    setPosition: function(top, left) {
      this.style.top = top;
      this.style.left = left;
    },

    // TODO @beckysiegel make this work with shadow dom.
    down: function(e) {
      if (this.isOpen) {
        e.preventDefault();
        e.stopPropagation();
        this.$.cursor.next();
      }
    },

    // TODO @beckysiegel make this work with shadow dom.
    up: function(e) {
      if (this.isOpen) {
        e.preventDefault();
        e.stopPropagation();
        this.$.cursor.previous();
      }
    },

    _handleTab: function(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('item-selected', {key: 'tab', selected: this.$.cursor.target});
    },

    _handleEnter: function(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('item-selected', {selected: this.$.cursor.target});
    },

    _handleTapItem: function(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('item-selected', {selected: e.target});
    },

    _resetFocus: function() {
      this.fire('dropdown-closed');
    },

    _handleEscapeKey: function() {
      this._resetFocus();
      this.close();
    },

    getCursorTarget: function() {
      return this.$.cursor.target;
    },

    resetCursorStops: function() {
      Polymer.dom.flush();
      this.suggestionEls = this.$.suggestions.querySelectorAll('li');
    },

    resetCursorIndex: function() {
      this.$.cursor.setCursorAtIndex(0);
    },
  });
})();
