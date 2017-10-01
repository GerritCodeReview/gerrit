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

  const AWAIT_MAX_ITERS = 10;
  const AWAIT_STEP = 5;

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
        observer: '_resetCursorStops',
      },
      _suggestionEls: {
        type: Array,
        observer: '_resetCursorIndex',
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      up: '_handleUp',
      down: '_handleDown',
      enter: '_handleEnter',
      esc: '_handleEscape',
      tab: '_handleTab',
    },

    close() {
      this.$.dropdown.close();
    },

    open() {
      this._open().then(() => {
        this._resetCursorStops();
        this._resetCursorIndex();
        this.fire('open-complete');
      });
    },

    // TODO (beckysiegel) look into making this a behavior since it's used
    // 3 times now.
    _open(...args) {
      return new Promise(resolve => {
        Polymer.IronOverlayBehaviorImpl.open.apply(this.$.dropdown, args);
        this._awaitOpen(resolve);
      });
    },

    /**
     * NOTE: (wyatta) Slightly hacky way to listen to the overlay actually
     * opening. Eventually replace with a direct way to listen to the overlay.
     */
    _awaitOpen(fn) {
      let iters = 0;
      const step = () => {
        this.async(() => {
          if (this.style.display !== 'none') {
            fn.call(this);
          } else if (iters++ < AWAIT_MAX_ITERS) {
            step.call(this);
          }
        }, AWAIT_STEP);
      };
      step.call(this);
    },

    get isHidden() {
      return !this.$.dropdown.opened;
    },

    getCurrentText() {
      return this.getCursorTarget().dataset.value;
    },

    _handleUp(e) {
      if (!this.hidden) {
        e.preventDefault();
        e.stopPropagation();
        this.cursorUp();
      }
    },

    _handleDown(e) {
      if (!this.hidden) {
        e.preventDefault();
        e.stopPropagation();
        this.cursorDown();
      }
    },

    cursorDown() {
      if (!this.hidden) {
        this.$.cursor.next();
      }
    },

    cursorUp() {
      if (!this.hidden) {
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

    _handleTapItem(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('item-selected', {
        trigger: 'tap',
        selected: e.target,
      });
    },

    _fireClose() {
      this.fire('dropdown-closed');
    },

    getCursorTarget() {
      return this.$.cursor.target;
    },

    _resetCursorStops() {
      Polymer.dom.flush();
      this._suggestionEls = this.$.suggestions.querySelectorAll('li');
    },

    _resetCursorIndex() {
      this.$.cursor.setCursorAtIndex(0);
    },
  });
})();
