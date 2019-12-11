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

  const ScrollBehavior = {
    NEVER: 'never',
    KEEP_VISIBLE: 'keep-visible',
  };

  class GrCursorManager extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-cursor-manager'; }

    static get properties() {
      return {
        stops: {
          type: Array,
          value() {
            return [];
          },
          observer: '_updateIndex',
        },
        /**
         * @type {?Object}
         */
        target: {
          type: Object,
          notify: true,
          observer: '_scrollToTarget',
        },
        /**
         * The height of content intended to be included with the target.
         *
         * @type {?number}
         */
        _targetHeight: Number,

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
         * The scroll behavior for the cursor. Values are 'never' and
         * 'keep-visible'. 'keep-visible' will only scroll if the cursor is beyond
         * the viewport.
         * TODO (beckysiegel) figure out why it can be undefined
         *
         * @type {string|undefined}
         */
        scrollBehavior: {
          type: String,
          value: ScrollBehavior.NEVER,
        },

        /**
         * When true, will call element.focus() during scrolling.
         */
        focusOnMove: {
          type: Boolean,
          value: false,
        },
      };
    }

    detached() {
      super.detached();
      this.unsetCursor();
    }

    /**
     * Move the cursor forward. Clipped to the ends of the stop list.
     *
     * @param {!Function=} opt_condition Optional stop condition. If a condition
     *    is passed the cursor will continue to move in the specified direction
     *    until the condition is met.
     * @param {!Function=} opt_getTargetHeight Optional function to calculate the
     *    height of the target's 'section'. The height of the target itself is
     *    sometimes different, used by the diff cursor.
     * @param {boolean=} opt_clipToTop When none of the next indices match, move
     *     back to first instead of to last.
     * @private
     */

    next(opt_condition, opt_getTargetHeight, opt_clipToTop) {
      this._moveCursor(1, opt_condition, opt_getTargetHeight, opt_clipToTop);
    }

    previous(opt_condition) {
      this._moveCursor(-1, opt_condition);
    }

    /**
     * Set the cursor to an arbitrary element.
     *
     * @param {!HTMLElement} element
     * @param {boolean=} opt_noScroll prevent any potential scrolling in response
     *   setting the cursor.
     */
    setCursor(element, opt_noScroll) {
      let behavior;
      if (opt_noScroll) {
        behavior = this.scrollBehavior;
        this.scrollBehavior = ScrollBehavior.NEVER;
      }

      this.unsetCursor();
      this.target = element;
      this._updateIndex();
      this._decorateTarget();

      if (opt_noScroll) { this.scrollBehavior = behavior; }
    }

    unsetCursor() {
      this._unDecorateTarget();
      this.index = -1;
      this.target = null;
      this._targetHeight = null;
    }

    isAtStart() {
      return this.index === 0;
    }

    isAtEnd() {
      return this.index === this.stops.length - 1;
    }

    moveToStart() {
      if (this.stops.length) {
        this.setCursor(this.stops[0]);
      }
    }

    setCursorAtIndex(index, opt_noScroll) {
      this.setCursor(this.stops[index], opt_noScroll);
    }

    /**
     * Move the cursor forward or backward by delta. Clipped to the beginning or
     * end of stop list.
     *
     * @param {number} delta either -1 or 1.
     * @param {!Function=} opt_condition Optional stop condition. If a condition
     *    is passed the cursor will continue to move in the specified direction
     *    until the condition is met.
     * @param {!Function=} opt_getTargetHeight Optional function to calculate the
     *    height of the target's 'section'. The height of the target itself is
     *    sometimes different, used by the diff cursor.
     * @param {boolean=} opt_clipToTop When none of the next indices match, move
     *     back to first instead of to last.
     * @private
     */
    _moveCursor(delta, opt_condition, opt_getTargetHeight, opt_clipToTop) {
      if (!this.stops.length) {
        this.unsetCursor();
        return;
      }

      this._unDecorateTarget();

      const newIndex = this._getNextindex(delta, opt_condition, opt_clipToTop);

      let newTarget = null;
      if (newIndex !== -1) {
        newTarget = this.stops[newIndex];
      }

      this.index = newIndex;
      this.target = newTarget;

      if (!this.target) { return; }

      if (opt_getTargetHeight) {
        this._targetHeight = opt_getTargetHeight(newTarget);
      } else {
        this._targetHeight = newTarget.scrollHeight;
      }

      if (this.focusOnMove) { this.target.focus(); }

      this._decorateTarget();
    }

    _decorateTarget() {
      if (this.target && this.cursorTargetClass) {
        this.target.classList.add(this.cursorTargetClass);
      }
    }

    _unDecorateTarget() {
      if (this.target && this.cursorTargetClass) {
        this.target.classList.remove(this.cursorTargetClass);
      }
    }

    /**
     * Get the next stop index indicated by the delta direction.
     *
     * @param {number} delta either -1 or 1.
     * @param {!Function=} opt_condition Optional stop condition.
     * @param {boolean=} opt_clipToTop When none of the next indices match, move
     *     back to first instead of to last.
     * @return {number} the new index.
     * @private
     */
    _getNextindex(delta, opt_condition, opt_clipToTop) {
      if (!this.stops.length || this.index === -1) {
        return -1;
      }

      let newIndex = this.index;
      do {
        newIndex = newIndex + delta;
      } while (newIndex > 0 &&
               newIndex < this.stops.length - 1 &&
               opt_condition && !opt_condition(this.stops[newIndex]));

      newIndex = Math.max(0, Math.min(this.stops.length - 1, newIndex));

      // If we failed to satisfy the condition:
      if (opt_condition && !opt_condition(this.stops[newIndex])) {
        if (delta < 0 || opt_clipToTop) {
          return 0;
        } else if (delta > 0) {
          return this.stops.length - 1;
        }
        return this.index;
      }

      return newIndex;
    }

    _updateIndex() {
      if (!this.target) {
        this.index = -1;
        return;
      }

      const newIndex = Array.prototype.indexOf.call(this.stops, this.target);
      if (newIndex === -1) {
        this.unsetCursor();
      } else {
        this.index = newIndex;
      }
    }

    /**
     * Calculate where the element is relative to the window.
     *
     * @param {!Object} target Target to scroll to.
     * @return {number} Distance to top of the target.
     */
    _getTop(target) {
      let top = target.offsetTop;
      for (let offsetParent = target.offsetParent;
        offsetParent;
        offsetParent = offsetParent.offsetParent) {
        top += offsetParent.offsetTop;
      }
      return top;
    }

    /**
     * @return {boolean}
     */
    _targetIsVisible(top) {
      const dims = this._getWindowDims();
      return this.scrollBehavior === ScrollBehavior.KEEP_VISIBLE &&
          top > dims.pageYOffset &&
          top < dims.pageYOffset + dims.innerHeight;
    }

    _calculateScrollToValue(top, target) {
      const dims = this._getWindowDims();
      return top - (dims.innerHeight / 3) + (target.offsetHeight / 2);
    }

    _scrollToTarget() {
      if (!this.target || this.scrollBehavior === ScrollBehavior.NEVER) {
        return;
      }

      const dims = this._getWindowDims();
      const top = this._getTop(this.target);
      const bottomIsVisible = this._targetHeight ?
        this._targetIsVisible(top + this._targetHeight) : true;
      const scrollToValue = this._calculateScrollToValue(top, this.target);

      if (this._targetIsVisible(top)) {
        // Don't scroll if either the bottom is visible or if the position that
        // would get scrolled to is higher up than the current position. this
        // woulld cause less of the target content to be displayed than is
        // already.
        if (bottomIsVisible || scrollToValue < dims.scrollY) {
          return;
        }
      }

      // Scroll the element to the middle of the window. Dividing by a third
      // instead of half the inner height feels a bit better otherwise the
      // element appears to be below the center of the window even when it
      // isn't.
      window.scrollTo(dims.scrollX, scrollToValue);
    }

    _getWindowDims() {
      return {
        scrollX: window.scrollX,
        scrollY: window.scrollY,
        innerHeight: window.innerHeight,
        pageYOffset: window.pageYOffset,
      };
    }
  }

  customElements.define(GrCursorManager.is, GrCursorManager);
})();
