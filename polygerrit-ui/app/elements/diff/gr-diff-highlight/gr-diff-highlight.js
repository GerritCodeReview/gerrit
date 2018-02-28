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
    is: 'gr-diff-highlight',

    properties: {
      comments: Object,
      loggedIn: Boolean,
      /**
       * querySelector can return null, so needs to be nullable.
       *
       * @type {?HTMLElement}
       * */
      _cachedDiffBuilder: Object,
      isAttached: Boolean,
    },

    listeners: {
      'comment-mouse-out': '_handleCommentMouseOut',
      'comment-mouse-over': '_handleCommentMouseOver',
      'create-comment': '_createComment',
    },

    observers: [
      '_enableSelectionObserver(loggedIn, isAttached)',
    ],

    get diffBuilder() {
      if (!this._cachedDiffBuilder) {
        this._cachedDiffBuilder =
            Polymer.dom(this).querySelector('gr-diff-builder');
      }
      return this._cachedDiffBuilder;
    },

    _enableSelectionObserver(loggedIn, isAttached) {
      if (loggedIn && isAttached) {
        this.listen(document, 'selectionchange', '_handleSelectionChange');
      } else {
        this.unlisten(document, 'selectionchange', '_handleSelectionChange');
      }
    },

    isRangeSelected() {
      return !!this.$$('gr-selection-action-box');
    },

    _handleSelectionChange() {
      // Can't use up or down events to handle selection started and/or ended in
      // in comment threads or outside of diff.
      // Debounce removeActionBox to give it a chance to react to click/tap.
      this._removeActionBoxDebounced();
      this.debounce('selectionChange', this._handleSelection, 200);
    },

    _handleCommentMouseOver(e) {
      const comment = e.detail.comment;
      if (!comment.range) { return; }
      const lineEl = this.diffBuilder.getLineElByChild(e.target);
      const side = this.diffBuilder.getSideByLineEl(lineEl);
      const index = this._indexOfComment(side, comment);
      if (index !== undefined) {
        this.set(['comments', side, index, '__hovering'], true);
      }
    },

    _handleCommentMouseOut(e) {
      const comment = e.detail.comment;
      if (!comment.range) { return; }
      const lineEl = this.diffBuilder.getLineElByChild(e.target);
      const side = this.diffBuilder.getSideByLineEl(lineEl);
      const index = this._indexOfComment(side, comment);
      if (index !== undefined) {
        this.set(['comments', side, index, '__hovering'], false);
      }
    },

    _indexOfComment(side, comment) {
      const idProp = comment.id ? 'id' : '__draftID';
      for (let i = 0; i < this.comments[side].length; i++) {
        if (comment[idProp] &&
            this.comments[side][i][idProp] === comment[idProp]) {
          return i;
        }
      }
    },

    /**
     * Get current normalized selection.
     * Merges multiple ranges, accounts for triple click, accounts for
     * syntax highligh, convert native DOM Range objects to Gerrit concepts
     * (line, side, etc).
     * @return {({
     *   start: {
     *     node: Node,
     *     side: string,
     *     line: Number,
     *     column: Number
     *   },
     *   end: {
     *     node: Node,
     *     side: string,
     *     line: Number,
     *     column: Number
     *   }
     * })|null|!Object}
     */
    _getNormalizedRange() {
      const selection = window.getSelection();
      const rangeCount = selection.rangeCount;
      if (rangeCount === 0) {
        return null;
      } else if (rangeCount === 1) {
        return this._normalizeRange(selection.getRangeAt(0));
      } else {
        const startRange = this._normalizeRange(selection.getRangeAt(0));
        const endRange = this._normalizeRange(
            selection.getRangeAt(rangeCount - 1));
        return {
          start: startRange.start,
          end: endRange.end,
        };
      }
    },

    /**
     * Normalize a specific DOM Range.
     * @return {!Object} fixed normalized range
     */
    _normalizeRange(domRange) {
      const range = GrRangeNormalizer.normalize(domRange);
      return this._fixTripleClickSelection({
        start: this._normalizeSelectionSide(
            range.startContainer, range.startOffset),
        end: this._normalizeSelectionSide(
            range.endContainer, range.endOffset),
      }, domRange);
    },

    /**
     * Adjust triple click selection for the whole line.
     * domRange.endContainer may be one of the following:
     * 1) 0 offset at right column's line number cell, or
     * 2) 0 offset at left column's line number at the next line.
     * Case 1 means left column was triple clicked.
     * Case 2 means right column or unified view triple clicked.
     * @param {!Object} range Normalized range, ie column/line numbers
     * @param {!Range} domRange DOM Range object
     * @return {!Object} fixed normalized range
     */
    _fixTripleClickSelection(range, domRange) {
      if (!range.start) {
        // Selection outside of current diff.
        return range;
      }
      const start = range.start;
      const end = range.end;
      const endsAtOtherSideLineNum =
          domRange.endOffset === 0 &&
          domRange.endContainer.nodeName === 'TD' &&
          (domRange.endContainer.classList.contains('left') ||
              domRange.endContainer.classList.contains('right'));
      const endsOnOtherSideStart = endsAtOtherSideLineNum ||
          end &&
          end.column === 0 &&
          end.line === start.line &&
          end.side != start.side;
      const content = domRange.cloneContents().querySelector('.contentText');
      const lineLength = content && this._getLength(content) || 0;
      if (lineLength && endsOnOtherSideStart || endsAtOtherSideLineNum) {
        // Selection ends at the beginning of the next line.
        // Move the selection to the end of the previous line.
        range.end = {
          node: start.node,
          column: lineLength,
          side: start.side,
          line: start.line,
        };
      }
      return range;
    },

    /**
     * Convert DOM Range selection to concrete numbers (line, column, side).
     * Moves range end if it's not inside td.content.
     * Returns null if selection end is not valid (outside of diff).
     *
     * @param {Node} node td.content child
     * @param {number} offset offset within node
     * @return {({
     *   node: Node,
     *   side: string,
     *   line: Number,
     *   column: Number
     * }|undefined)}
     */
    _normalizeSelectionSide(node, offset) {
      let column;
      if (!this.contains(node)) {
        return;
      }
      const lineEl = this.diffBuilder.getLineElByChild(node);
      if (!lineEl) {
        return;
      }
      const side = this.diffBuilder.getSideByLineEl(lineEl);
      if (!side) {
        return;
      }
      const line = this.diffBuilder.getLineNumberByChild(lineEl);
      if (!line) {
        return;
      }
      const contentText = this.diffBuilder.getContentByLineEl(lineEl);
      if (!contentText) {
        return;
      }
      const contentTd = contentText.parentElement;
      if (!contentTd.contains(node)) {
        node = contentText;
        column = 0;
      } else {
        const thread = contentTd.querySelector('gr-diff-comment-thread');
        if (thread && thread.contains(node)) {
          column = this._getLength(contentText);
          node = contentText;
        } else {
          column = this._convertOffsetToColumn(node, offset);
        }
      }

      return {
        node,
        side,
        line,
        column,
      };
    },

    /**
     * The only line in which add a comment tooltip is cut off is the first
     * line. Even if there is a collapsed section, The first visible line is
     * in the position where the second line would have been, if not for the
     * collapsed section, so don't need to worry about this case for
     * positioning the tooltip.
     */
    _positionActionBox(actionBox, startLine, range) {
      if (startLine > 1) {
        actionBox.placeAbove(range);
        return;
      }
      actionBox.positionBelow = true;
      actionBox.placeBelow(range);
    },

    _handleSelection() {
      const normalizedRange = this._getNormalizedRange();
      if (!normalizedRange) {
        return;
      }
      const domRange = window.getSelection().getRangeAt(0);
      /** @type {?} */
      const start = normalizedRange.start;
      if (!start) {
        return;
      }
      const end = normalizedRange.end;
      if (!end) {
        return;
      }
      if (start.side !== end.side ||
          end.line < start.line ||
          (start.line === end.line && start.column === end.column)) {
        return;
      }

      // TODO (viktard): Drop empty first and last lines from selection.

      const actionBox = document.createElement('gr-selection-action-box');
      const root = Polymer.dom(this.root);
      root.insertBefore(actionBox, root.firstElementChild);
      actionBox.range = {
        startLine: start.line,
        startChar: start.column,
        endLine: end.line,
        endChar: end.column,
      };
      actionBox.side = start.side;
      if (start.line === end.line) {
        this._positionActionBox(actionBox, start.line, domRange);
      } else if (start.node instanceof Text) {
        if (start.column) {
          this._positionActionBox(actionBox, start.line,
              start.node.splitText(start.column));
        }
        start.node.parentElement.normalize(); // Undo splitText from above.
      } else if (start.node.classList.contains('content') &&
          start.node.firstChild) {
        this._positionActionBox(actionBox, start.line, start.node.firstChild);
      } else {
        this._positionActionBox(actionBox, start.line, start.node);
      }
    },

    _createComment(e) {
      this._removeActionBox();
    },

    _removeActionBoxDebounced() {
      this.debounce('removeActionBox', this._removeActionBox, 10);
    },

    _removeActionBox() {
      const actionBox = this.$$('gr-selection-action-box');
      if (actionBox) {
        Polymer.dom(this.root).removeChild(actionBox);
      }
    },

    _convertOffsetToColumn(el, offset) {
      if (el instanceof Element && el.classList.contains('content')) {
        return offset;
      }
      while (el.previousSibling ||
          !el.parentElement.classList.contains('content')) {
        if (el.previousSibling) {
          el = el.previousSibling;
          offset += this._getLength(el);
        } else {
          el = el.parentElement;
        }
      }
      return offset;
    },

    /**
     * Traverse Element from right to left, call callback for each node.
     * Stops if callback returns true.
     *
     * @param {!Element} startNode
     * @param {function(Node):boolean} callback
     * @param {Object=} opt_flags If flags.left is true, traverse left.
     */
    _traverseContentSiblings(startNode, callback, opt_flags) {
      const travelLeft = opt_flags && opt_flags.left;
      let node = startNode;
      while (node) {
        if (node instanceof Element &&
            node.tagName !== 'SPAN') {
          break;
        }
        const nextNode = travelLeft ? node.previousSibling : node.nextSibling;
        if (callback(node)) {
          break;
        }
        node = nextNode;
      }
    },

    /**
     * Get length of a node. If the node is a content node, then only give the
     * length of its .contentText child.
     *
     * @param {?Element} node this is sometimes passed as null.
     * @return {number}
     */
    _getLength(node) {
      if (node instanceof Element && node.classList.contains('content')) {
        return this._getLength(node.querySelector('.contentText'));
      } else {
        return GrAnnotation.getLength(node);
      }
    },
  });
})();
