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

  var DiffSides = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  var DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };

  var LEFT_SIDE_CLASS = 'target-side-left';
  var RIGHT_SIDE_CLASS = 'target-side-right';

  Polymer({
    is: 'gr-diff-cursor',

    properties: {
      /**
       * Either DiffSides.LEFT or DiffSides.RIGHT.
       */
      side: {
        type: String,
        value: DiffSides.RIGHT,
        notify: true,
      },

      /**
       * Either DiffViewMode.SIDE_BY_SIDE or DiffViewMode.UNIFIED.
       */
      viewMode: String,

      diffRow: {
        type: Object,
        notify: true,
        observer: '_rowChanged',
      },

      cursorRoot: {
        type: Object,
        value: function() {
          return document;
        },
      },
    },

    observers: [
      '_updateSideClass(side, viewMode)',
      'reinitCursor(viewMode)',
    ],

    moveLeft: function() {
      this.side = DiffSides.LEFT;
      if (this._isTargetBlank()) {
        this.moveUp()
      }
    },

    moveRight: function() {
      this.side = DiffSides.RIGHT;
      if (this._isTargetBlank()) {
        this.moveUp()
      }
    },

    moveDown: function() {
      if (this.viewMode === DiffViewMode.SIDE_BY_SIDE) {
        this.$.cursorManager.next(this._rowHasSide.bind(this));
      } else {
        this.$.cursorManager.next();
      }
    },

    moveUp: function() {
      if (this.viewMode === DiffViewMode.SIDE_BY_SIDE) {
        this.$.cursorManager.previous(this._rowHasSide.bind(this));
      } else {
        this.$.cursorManager.previous();
      }
    },

    moveToNextChunk: function() {
      this.$.cursorManager.next(this._isFirstRowOfChunk.bind(this));
      this._fixSide();
    },

    moveToPreviousChunk: function() {
      this.$.cursorManager.previous(this._isFirstRowOfChunk.bind(this));
      this._fixSide();
    },

    moveToNextCommentThread: function() {
      this.$.cursorManager.next(this._rowHasThread.bind(this));
      this._fixSide();
    },

    moveToPreviousCommentThread: function() {
      this.$.cursorManager.previous(this._rowHasThread.bind(this));
      this._fixSide();
    },

    stopsChanged: function() {
      this.$.cursorManager.resetStops();
    },

    reinitCursor: function() {
      this.stopsChanged();
      this._moveToFirstChunk();
    },

    /**
     * Get the line number element targeted by the cursor row and side.
     * @return {DOMElement}
     */
    getTargetLineElement: function() {
      if (!this.diffRow) {
        return;
      }

      var lineElSelector = '.lineNum';

      if (this.viewMode === DiffViewMode.SIDE_BY_SIDE) {
        lineElSelector += this.side === DiffSides.LEFT ? '.left' : '.right';
      }

      return this.diffRow.querySelector(lineElSelector);
    },

    _rowHasSide: function(row) {
      var selector = '.content';
      selector += this.side === DiffSides.LEFT ? '.left' : '.right';
      return row.querySelector(selector);
    },

    _isFirstRowOfChunk: function(row) {
      var parentClassList = row.parentNode.classList;
      return parentClassList.contains('section') &&
          parentClassList.contains('delta') &&
          !row.previousSibling;
    },

    _rowHasThread: function(row) {
      return row.querySelector('gr-diff-comment-thread');
    },

    /**
     * If we jumped to a row where there is no content on the current side then
     * switch to the alternate side.
     */
    _fixSide: function() {
      if (this.viewMode === DiffViewMode.SIDE_BY_SIDE &&
          this._isTargetBlank()) {
        this.side = this.side === DiffSides.LEFT ?
            DiffSides.RIGHT : DiffSides.LEFT;
      }
    },

    _isTargetBlank: function() {
      if (!this.diffRow) {
        return false;
      }

      var actions = this._getActionsForRow();
      return (
        (this.side === DiffSides.LEFT && !actions.left) ||
        (this.side === DiffSides.RIGHT && !actions.right)
      );
    },

    _moveToFirstChunk: function() {
      this.$.cursorManager.moveToStart();
      this.moveToNextChunk();
    },

    _rowChanged: function(newRow, oldRow) {
      if (oldRow) {
        oldRow.classList.remove(LEFT_SIDE_CLASS, RIGHT_SIDE_CLASS);
      }
      this._updateSideClass();
    },

    _updateSideClass: function() {
      if (!this.diffRow) {
        return;
      }
      this.toggleClass(LEFT_SIDE_CLASS, this.side === DiffSides.LEFT,
          this.diffRow);
      this.toggleClass(RIGHT_SIDE_CLASS, this.side === DiffSides.RIGHT,
          this.diffRow);
    },

    _isActionType: function(type) {
      return type !== 'blank' && type !== 'contextControl';
    },

    _getActionsForRow: function() {
      var actions = { left: false, right: false };
      if (this.diffRow) {
        actions.left = this._isActionType(this.diffRow.dataset.leftType);
        actions.right = this._isActionType(this.diffRow.dataset.rightType);
      }
      return actions;
    },
  });
})();
