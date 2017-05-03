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
    is: 'gr-autocomplete-dropdown',

    /**
     * Fired when the dropdown is closed
     *
     * @event dropdown-closed
     */

    properties: {
      isOpen: {
         type: Boolean,
         value: false,
      },
      selected: {
        type: Object,
        notify: true,
      },
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
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      'up': 'up',
      'down': 'down',
      'enter': '_handleEnter',
      'esc': '_handleEscapeKey',
      'tab': '_handleEnter',
    },

    close: function() {
      Polymer.dom(document.body).removeChild(this);
      this.isOpen = false;
    },

    open: function() {
      Polymer.dom(document.body).appendChild(this);
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

    _handleEnter: function(e) {
      e.preventDefault();
      this.selected = this.$.cursor.target;
    },

    _handleTapItem: function(e) {
      e.preventDefault();
      this.selected = e.target;
    },

    _resetFocus: function() {
      this.fire('dropdown-closed');
    },

    _handleEscapeKey: function() {
      this._resetFocus();
      this.close();
    },

    select: function(e) {
      e.preventDefault();
      e.stopPropagation();
      this.selected = this.$.cursor.target;
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
