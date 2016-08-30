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

  var ScrollBehavior = {
    ALWAYS: 'always',
    NEVER: 'never',
    KEEP_VISIBLE: 'keep-visible',
  };

  Polymer({
    is: 'gr-cursor-manager',

    properties: {
      stops: {
        type: Array,
        value: function() {
          return [];
        },
        observer: '_updateIndex',
      },
      target: {
        type: Object,
        notify: true,
        observer: '_scrollToTarget',
      },

      /**
       * The index of the current target (if any). -1 otherwise.
       */
      index: {
        type: Number,
        value: -1,
        notify: true,
      },

      /**
       * The class to apply to the current target. Use null for no class.
       */
      cursorTargetClass: {
        type: String,
        value: null,
      },

      /**
       * The scroll behavior for the cursor. Values are 'never', 'always' and
       * 'keep-visible'. 'keep-visible' will only scroll if the cursor is beyond
       * the viewport.
       */
      scroll: {
        type: String,
        value: ScrollBehavior.NEVER,
      },

      /**
       * When using the 'keep-visible' scroll behavior, set an offset to the top
       * of the window for what is considered above the upper fold.
       */
      foldOffsetTop: {
        type: Number,
        value: 0,
      },
    },

    detached: function() {
      this.unsetCursor();
    },

    next: function(opt_condition) {
      this._moveCursor(1, opt_condition);
    },

    previous: function(opt_condition) {
      this._moveCursor(-1, opt_condition);
    },

    /**
     * Set the cursor to an arbitrary element.
     * @param {DOMElement} element
     */
    setCursor: function(element) {
      this.unsetCursor();
      this.target = element;
      this._updateIndex();
      this._decorateTarget();
    },

    unsetCursor: function() {
      this._unDecorateTarget();
      this.index = -1;
      this.target = null;
    },

    isAtStart: function() {
      return this.index === 0;
    },

    isAtEnd: function() {
      return this.index === this.stops.length - 1;
    },

    moveToStart: function() {
      if (this.stops.length) {
        this.setCursor(this.stops[0]);
      }
    },

    /**
     * Move the cursor forward or backward by delta. Noop if moving past either
     * end of the stop list.
     * @param {Number} delta either -1 or 1.
     * @param {Function} opt_condition Optional stop condition. If a condition
     *    is passed the cursor will continue to move in the specified direction
     *    until the condition is met.
     * @private
     */
    _moveCursor: function(delta, opt_condition) {
      if (!this.stops.length) {
        this.unsetCursor();
        return;
      }

      this._unDecorateTarget();

      var newIndex = this._getNextindex(delta, opt_condition);

      var newTarget = null;
      if (newIndex != -1) {
        newTarget = this.stops[newIndex];
      }

      this.index = newIndex;
      this.target = newTarget;

      this._decorateTarget();
    },

    _decorateTarget: function() {
      if (this.target && this.cursorTargetClass) {
        this.target.classList.add(this.cursorTargetClass);
      }
    },

    _unDecorateTarget: function() {
      if (this.target && this.cursorTargetClass) {
        this.target.classList.remove(this.cursorTargetClass);
      }
    },

    /**
     * Get the next stop index indicated by the delta direction.
     * @param {Number} delta either -1 or 1.
     * @param {Function} opt_condition Optional stop condition.
     * @return {Number} the new index.
     * @private
     */
    _getNextindex: function(delta, opt_condition) {
      if (!this.stops.length || this.index === -1) {
        return -1;
      }

      var newIndex = this.index;
      do {
        newIndex = newIndex + delta;
      } while (newIndex > 0 &&
               newIndex < this.stops.length - 1 &&
               opt_condition && !opt_condition(this.stops[newIndex]));

      newIndex = Math.max(0, Math.min(this.stops.length - 1, newIndex));

      // If we failed to satisfy the condition:
      if (opt_condition && !opt_condition(this.stops[newIndex])) {
        return this.index;
      }

      return newIndex;
    },

    _updateIndex: function() {
      if (!this.target) {
        this.index = -1;
        return;
      }

      var newIndex = Array.prototype.indexOf.call(this.stops, this.target);
      if (newIndex === -1) {
        this.unsetCursor();
      } else {
        this.index = newIndex;
      }
    },

    _scrollToTarget: function() {
      if (!this.target || this.scroll === ScrollBehavior.NEVER) { return; }

      // Calculate where the element is relative to the window.
      var top = this.target.offsetTop;
      for (var offsetParent = this.target.offsetParent;
           offsetParent;
           offsetParent = offsetParent.offsetParent) {
        top += offsetParent.offsetTop;
      }

      if (this.scroll === ScrollBehavior.KEEP_VISIBLE &&
          top > window.pageYOffset + this.foldOffsetTop &&
          top < window.pageYOffset + window.innerHeight) { return; }

      // Scroll the element to the middle of the window. Dividing by a third
      // instead of half the inner height feels a bit better otherwise the
      // element appears to be below the center of the window even when it
      // isn't.
      window.scrollTo(0, top - (window.innerHeight / 3) +
          (this.target.offsetHeight / 2));
    },
  });
})();
