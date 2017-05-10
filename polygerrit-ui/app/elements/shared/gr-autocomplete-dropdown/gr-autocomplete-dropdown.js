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
      moveToRoot: Boolean,
      fixedPosition: Boolean,
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
      'esc': '_handleEscape',
      'tab': '_handleTab',
    },

    attached: function() {
      if (this.fixedPosition) {
        this.customStyle['--position-dropdown'] = 'fixed';
        this.updateStyles();
      }
    },

    close: function() {
      if (this.moveToRoot) {
        Gerrit.getRootElement().removeChild(this);
      }
    },

    open: function() {
      if (this.moveToRoot) {
        Gerrit.getRootElement().appendChild(this);
      }
      this.resetCursorStops();
      this.resetCursorIndex();
    },

    setPosition: function(top, left) {
      this.style.top = top;
      this.style.left = left;
    },

    // TODO @beckysiegel make this work with shadow dom.
    down: function(e) {
      if (!this.hidden) {
        e.preventDefault();
        e.stopPropagation();
        this.$.cursor.next();
      }
    },

    // TODO @beckysiegel make this work with shadow dom.
    up: function(e) {
      if (!this.hidden) {
        e.preventDefault();
        e.stopPropagation();
        this.$.cursor.previous();
      }
    },

    _handleTab: function(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('item-selected', {
        trigger: 'tab',
        selected: this.$.cursor.target,
      });
    },

    _handleEnter: function(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('item-selected', {
        trigger: 'enter',
        selected: this.$.cursor.target,
      });
    },

    _handleEscape: function() {
      this._fireClose();
      this.close();
    },

    _handleTapItem: function(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('item-selected', {
        trigger: 'tap',
        selected: e.target,
      });
    },

    _fireClose: function() {
      this.fire('dropdown-closed');
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
