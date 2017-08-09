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

  const DiffSides = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  const DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };

  const ScrollBehavior = {
    KEEP_VISIBLE: 'keep-visible',
    NEVER: 'never',
  };

  const LEFT_SIDE_CLASS = 'target-side-left';
  const RIGHT_SIDE_CLASS = 'target-side-right';

  Polymer({
    is: 'gr-diff-cursor',

    properties: {
      /**
       * Either DiffSides.LEFT or DiffSides.RIGHT.
       */
      side: {
        type: String,
        value: DiffSides.RIGHT,
      },
      /** @type {!HTMLElement|undefined} */
      diffRow: {
        type: Object,
        notify: true,
        observer: '_rowChanged',
      },

      /**
       * The diff views to cursor through and listen to.
       */
      diffs: {
        type: Array,
        value() { return []; },
      },

      /**
       * If set, the cursor will attempt to move to the line number (instead of
       * the first chunk) the next time the diff renders. It is set back to null
       * when used.
       *
       * @type (?number)
       */
      initialLineNumber: {
        type: Number,
        value: null,
      },

      /**
       * The scroll behavior for the cursor. Values are 'never' and
       * 'keep-visible'. 'keep-visible' will only scroll if the cursor is beyond
       * the viewport.
       */
      _scrollBehavior: {
        type: String,
        value: ScrollBehavior.KEEP_VISIBLE,
      },

      _listeningForScroll: Boolean,
    },

    observers: [
      '_updateSideClass(side)',
      '_diffsChanged(diffs.splices)',
    ],

    attached() {
      // Catch when users are scrolling as the view loads.
      this.listen(window, 'scroll', '_handleWindowScroll');
    },

    detached() {
      this.unlisten(window, 'scroll', '_handleWindowScroll');
    },

    moveLeft() {
      this.side = DiffSides.LEFT;
      if (this._isTargetBlank()) {
        this.moveUp();
      }
    },

    moveRight() {
      this.side = DiffSides.RIGHT;
      if (this._isTargetBlank()) {
        this.moveUp();
      }
    },

    moveDown() {
      if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE) {
        this.$.cursorManager.next(this._rowHasSide.bind(this));
      } else {
        this.$.cursorManager.next();
      }
    },

    moveUp() {
      if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE) {
        this.$.cursorManager.previous(this._rowHasSide.bind(this));
      } else {
        this.$.cursorManager.previous();
      }
    },

    moveToNextChunk() {
      this.$.cursorManager.next(this._isFirstRowOfChunk.bind(this),
          target => {
            return target.parentNode.scrollHeight;
          });
      this._fixSide();
    },

    moveToPreviousChunk() {
      this.$.cursorManager.previous(this._isFirstRowOfChunk.bind(this));
      this._fixSide();
    },

    moveToNextCommentThread() {
      this.$.cursorManager.next(this._rowHasThread.bind(this));
      this._fixSide();
    },

    moveToPreviousCommentThread() {
      this.$.cursorManager.previous(this._rowHasThread.bind(this));
      this._fixSide();
    },

    /**
     * @param {number} number
     * @param {string} side
     * @param {string=} opt_path
     */
    moveToLineNumber(number, side, opt_path) {
      const row = this._findRowByNumberAndFile(number, side, opt_path);
      if (row) {
        this.side = side;
        this.$.cursorManager.setCursor(row);
      }
    },

    /**
     * Get the line number element targeted by the cursor row and side.
     * @return {?Element|undefined}
     */
    getTargetLineElement() {
      let lineElSelector = '.lineNum';

      if (!this.diffRow) {
        return;
      }

      if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE) {
        lineElSelector += this.side === DiffSides.LEFT ? '.left' : '.right';
      }

      return this.diffRow.querySelector(lineElSelector);
    },

    getTargetDiffElement() {
      // Find the parent diff element of the cursor row.
      for (let diff = this.diffRow; diff; diff = diff.parentElement) {
        if (diff.tagName === 'GR-DIFF') { return diff; }
      }
      return null;
    },

    moveToFirstChunk() {
      this.$.cursorManager.moveToStart();
      this.moveToNextChunk();
    },

    reInitCursor() {
      this._updateStops();
      if (this.initialLineNumber) {
        this.moveToLineNumber(this.initialLineNumber, this.side);
        this.initialLineNumber = null;
      } else {
        this.moveToFirstChunk();
      }
    },

    _handleWindowScroll() {
      if (this._listeningForScroll) {
        this._scrollBehavior = ScrollBehavior.NEVER;
        this._listeningForScroll = false;
      }
    },

    handleDiffUpdate() {
      this._updateStops();

      if (!this.diffRow) {
        this.reInitCursor();
      }
      this._scrollBehavior = ScrollBehavior.KEEP_VISIBLE;
      this._listeningForScroll = false;
    },

    _handleDiffRenderStart() {
      this._listeningForScroll = true;
    },

    /**
     * Get a short address for the location of the cursor. Such as '123' for
     * line 123 of the revision, or 'b321' for line 321 of the base patch.
     * Returns an empty string if an address is not available.
     * @return {string}
     */
    getAddress() {
      if (!this.diffRow) { return ''; }

      // Get the line-number cell targeted by the cursor. If the mode is unified
      // then prefer the revision cell if available.
      let cell;
      if (this._getViewMode() === DiffViewMode.UNIFIED) {
        cell = this.diffRow.querySelector('.lineNum.right');
        if (!cell) {
          cell = this.diffRow.querySelector('.lineNum.left');
        }
      } else {
        cell = this.diffRow.querySelector('.lineNum.' + this.side);
      }
      if (!cell) { return ''; }

      const number = cell.getAttribute('data-value');
      if (!number || number === 'FILE') { return ''; }

      return (cell.matches('.left') ? 'b' : '') + number;
    },

    _getViewMode() {
      if (!this.diffRow) {
        return null;
      }

      if (this.diffRow.classList.contains('side-by-side')) {
        return DiffViewMode.SIDE_BY_SIDE;
      } else {
        return DiffViewMode.UNIFIED;
      }
    },

    _rowHasSide(row) {
      const selector = (this.side === DiffSides.LEFT ? '.left' : '.right') +
          ' + .content';
      return !!row.querySelector(selector);
    },

    _isFirstRowOfChunk(row) {
      const parentClassList = row.parentNode.classList;
      return parentClassList.contains('section') &&
          parentClassList.contains('delta') &&
          !row.previousSibling;
    },

    _rowHasThread(row) {
      return row.querySelector('gr-diff-comment-thread');
    },

    /**
     * If we jumped to a row where there is no content on the current side then
     * switch to the alternate side.
     */
    _fixSide() {
      if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE &&
          this._isTargetBlank()) {
        this.side = this.side === DiffSides.LEFT ?
            DiffSides.RIGHT : DiffSides.LEFT;
      }
    },

    _isTargetBlank() {
      if (!this.diffRow) {
        return false;
      }

      const actions = this._getActionsForRow();
      return (this.side === DiffSides.LEFT && !actions.left) ||
          (this.side === DiffSides.RIGHT && !actions.right);
    },

    _rowChanged(newRow, oldRow) {
      if (oldRow) {
        oldRow.classList.remove(LEFT_SIDE_CLASS, RIGHT_SIDE_CLASS);
      }
      this._updateSideClass();
    },

    _updateSideClass() {
      if (!this.diffRow) {
        return;
      }
      this.toggleClass(LEFT_SIDE_CLASS, this.side === DiffSides.LEFT,
          this.diffRow);
      this.toggleClass(RIGHT_SIDE_CLASS, this.side === DiffSides.RIGHT,
          this.diffRow);
    },

    _isActionType(type) {
      return type !== 'blank' && type !== 'contextControl';
    },

    _getActionsForRow() {
      const actions = {left: false, right: false};
      if (this.diffRow) {
        actions.left = this._isActionType(
            this.diffRow.getAttribute('left-type'));
        actions.right = this._isActionType(
            this.diffRow.getAttribute('right-type'));
      }
      return actions;
    },

    _getStops() {
      return this.diffs.reduce(
          (stops, diff) => {
            return stops.concat(diff.getCursorStops());
          }, []);
    },

    _updateStops() {
      this.$.cursorManager.stops = this._getStops();
    },

    /**
     * Setup and tear down on-render listeners for any diffs that are added or
     * removed from the cursor.
     * @private
     */
    _diffsChanged(changeRecord) {
      if (!changeRecord) { return; }

      this._updateStops();

      let splice;
      let i;
      for (let spliceIdx = 0;
        changeRecord.indexSplices &&
            spliceIdx < changeRecord.indexSplices.length;
        spliceIdx++) {
        splice = changeRecord.indexSplices[spliceIdx];

        for (i = splice.index;
            i < splice.index + splice.addedCount;
            i++) {
          this.listen(this.diffs[i], 'render-start', '_handleDiffRenderStart');
          this.listen(this.diffs[i], 'render-content', 'handleDiffUpdate');
        }

        for (i = 0;
            i < splice.removed && splice.removed.length;
            i++) {
          this.unlisten(splice.removed[i],
              'render-start', '_handleDiffRenderStart');
          this.unlisten(splice.removed[i],
              'render-content', 'handleDiffUpdate');
        }
      }
    },

    _findRowByNumberAndFile(targetNumber, side, opt_path) {
      let stops;
      if (opt_path) {
        const diff = this.diffs.filter(diff => diff.path === opt_path)[0];
        stops = diff.getCursorStops();
      } else {
        stops = this.$.cursorManager.stops;
      }
      let selector;
      for (let i = 0; i < stops.length; i++) {
        selector = '.lineNum.' + side + '[data-value="' + targetNumber + '"]';
        if (stops[i].querySelector(selector)) {
          return stops[i];
        }
      }
    },
  });
})();
