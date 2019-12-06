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

  class GrDiffCursor extends Polymer.mixinBehaviors([Gerrit.FireBehavior],
      Polymer.GestureEventListeners(
          Polymer.LegacyElementMixin(Polymer.Element))) {
    static get is() { return 'gr-diff-cursor'; }

    static get properties() {
      return {
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
       * when used. It should be only used if you want the line to be focused
       * after initialization of the component and page should scroll
       * to that position. This parameter should be set at most for one gr-diff
       * element in the page.
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

        _focusOnMove: {
          type: Boolean,
          value: true,
        },

        _listeningForScroll: Boolean,
      };
    }

    static get observers() {
      return [
        '_updateSideClass(side)',
        '_diffsChanged(diffs.splices)',
      ];
    }

    ready() {
      super.ready();
      Polymer.RenderStatus.afterNextRender(this, () => {
        /*
        This represents the diff cursor is ready for interaction coming from
        client components. It is more then Polymer "ready" lifecycle, as no
        "ready" events are automatically fired by Polymer, it means
        the cursor is completely interactable - in this case attached and
        painted on the page. We name it "ready" instead of "rendered" as the
        long-term goal is to make gr-diff-cursor a javascript class - not a DOM
        element with an actual lifecycle. This will be triggered only once
        per element.
        */
        this.fire('ready', null, {bubbles: false});
      });
    }

    attached() {
      super.attached();
      // Catch when users are scrolling as the view loads.
      this.listen(window, 'scroll', '_handleWindowScroll');
    }

    detached() {
      super.detached();
      this.unlisten(window, 'scroll', '_handleWindowScroll');
    }

    moveLeft() {
      this.side = DiffSides.LEFT;
      if (this._isTargetBlank()) {
        this.moveUp();
      }
    }

    moveRight() {
      this.side = DiffSides.RIGHT;
      if (this._isTargetBlank()) {
        this.moveUp();
      }
    }

    moveDown() {
      if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE) {
        this.$.cursorManager.next(this._rowHasSide.bind(this));
      } else {
        this.$.cursorManager.next();
      }
    }

    moveUp() {
      if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE) {
        this.$.cursorManager.previous(this._rowHasSide.bind(this));
      } else {
        this.$.cursorManager.previous();
      }
    }

    moveToNextChunk(opt_clipToTop) {
      this.$.cursorManager.next(this._isFirstRowOfChunk.bind(this),
          target => {
            return target.parentNode.scrollHeight;
          }, opt_clipToTop);
      this._fixSide();
    }

    moveToPreviousChunk() {
      this.$.cursorManager.previous(this._isFirstRowOfChunk.bind(this));
      this._fixSide();
    }

    moveToNextCommentThread() {
      this.$.cursorManager.next(this._rowHasThread.bind(this));
      this._fixSide();
    }

    moveToPreviousCommentThread() {
      this.$.cursorManager.previous(this._rowHasThread.bind(this));
      this._fixSide();
    }

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
    }

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
    }

    getTargetDiffElement() {
      if (!this.diffRow) return null;

      const hostOwner = Polymer.dom(/** @type {Node} */ (this.diffRow))
          .getOwnerRoot();
      if (hostOwner && hostOwner.host &&
          hostOwner.host.tagName === 'GR-DIFF') {
        return hostOwner.host;
      }
      return null;
    }

    moveToFirstChunk() {
      this.$.cursorManager.moveToStart();
      this.moveToNextChunk(true);
    }

    reInitCursor() {
      this._updateStops();
      if (this.initialLineNumber) {
        this.moveToLineNumber(this.initialLineNumber, this.side);
        this.initialLineNumber = null;
      } else {
        this.moveToFirstChunk();
      }
    }

    _handleWindowScroll() {
      if (this._listeningForScroll) {
        this._scrollBehavior = ScrollBehavior.NEVER;
        this._focusOnMove = false;
        this._listeningForScroll = false;
      }
    }

    handleDiffUpdate() {
      this._updateStops();
      if (!this.diffRow) {
        // does not scroll during init unless requested
        const scrollingBehaviorForInit = this.initialLineNumber ?
          ScrollBehavior.KEEP_VISIBLE :
          ScrollBehavior.NEVER;
        this._scrollBehavior = scrollingBehaviorForInit;
        this.reInitCursor();
      }
      this._scrollBehavior = ScrollBehavior.KEEP_VISIBLE;
      this._focusOnMove = true;
      this._listeningForScroll = false;
    }

    _handleDiffRenderStart() {
      this._listeningForScroll = true;
    }

    createCommentInPlace() {
      const diffWithRangeSelected = this.diffs.find(diff => {
        return diff.isRangeSelected();
      });
      if (diffWithRangeSelected) {
        diffWithRangeSelected.createRangeComment();
      } else {
        const line = this.getTargetLineElement();
        if (line) {
          this.getTargetDiffElement().addDraftAtLine(line);
        }
      }
    }

    /**
     * Get an object describing the location of the cursor. Such as
     * {leftSide: false, number: 123} for line 123 of the revision, or
     * {leftSide: true, number: 321} for line 321 of the base patch.
     * Returns null if an address is not available.
     * @return {?Object}
     */
    getAddress() {
      if (!this.diffRow) { return null; }

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
      if (!cell) { return null; }

      const number = cell.getAttribute('data-value');
      if (!number || number === 'FILE') { return null; }

      return {
        leftSide: cell.matches('.left'),
        number: parseInt(number, 10),
      };
    }

    _getViewMode() {
      if (!this.diffRow) {
        return null;
      }

      if (this.diffRow.classList.contains('side-by-side')) {
        return DiffViewMode.SIDE_BY_SIDE;
      } else {
        return DiffViewMode.UNIFIED;
      }
    }

    _rowHasSide(row) {
      const selector = (this.side === DiffSides.LEFT ? '.left' : '.right') +
          ' + .content';
      return !!row.querySelector(selector);
    }

    _isFirstRowOfChunk(row) {
      const parentClassList = row.parentNode.classList;
      return parentClassList.contains('section') &&
          parentClassList.contains('delta') &&
          !row.previousSibling;
    }

    _rowHasThread(row) {
      return row.querySelector('.thread-group');
    }

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
    }

    _isTargetBlank() {
      if (!this.diffRow) {
        return false;
      }

      const actions = this._getActionsForRow();
      return (this.side === DiffSides.LEFT && !actions.left) ||
          (this.side === DiffSides.RIGHT && !actions.right);
    }

    _rowChanged(newRow, oldRow) {
      if (oldRow) {
        oldRow.classList.remove(LEFT_SIDE_CLASS, RIGHT_SIDE_CLASS);
      }
      this._updateSideClass();
    }

    _updateSideClass() {
      if (!this.diffRow) {
        return;
      }
      this.toggleClass(LEFT_SIDE_CLASS, this.side === DiffSides.LEFT,
          this.diffRow);
      this.toggleClass(RIGHT_SIDE_CLASS, this.side === DiffSides.RIGHT,
          this.diffRow);
    }

    _isActionType(type) {
      return type !== 'blank' && type !== 'contextControl';
    }

    _getActionsForRow() {
      const actions = {left: false, right: false};
      if (this.diffRow) {
        actions.left = this._isActionType(
            this.diffRow.getAttribute('left-type'));
        actions.right = this._isActionType(
            this.diffRow.getAttribute('right-type'));
      }
      return actions;
    }

    _getStops() {
      return this.diffs.reduce(
          (stops, diff) => {
            return stops.concat(diff.getCursorStops());
          }, []);
    }

    _updateStops() {
      this.$.cursorManager.stops = this._getStops();
    }

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
    }

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
    }
  }

  customElements.define(GrDiffCursor.is, GrDiffCursor);
})();
