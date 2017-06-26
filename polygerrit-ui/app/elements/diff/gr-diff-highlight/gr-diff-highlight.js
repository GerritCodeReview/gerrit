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

    _enableSelectionObserver: function(loggedIn, isAttached) {
      if (loggedIn && isAttached) {
        this.listen(document, 'selectionchange', '_handleSelectionChange');
      } else {
        this.unlisten(document, 'selectionchange', '_handleSelectionChange');
      }
    },

    isRangeSelected: function() {
      return !!this.$$('gr-selection-action-box');
    },

    _handleSelectionChange: function() {
      // Can't use up or down events to handle selection started and/or ended in
      // in comment threads or outside of diff.
      // Debounce removeActionBox to give it a chance to react to click/tap.
      this._removeActionBoxDebounced();
      this.debounce('selectionChange', this._handleSelection, 200);
    },

    _handleCommentMouseOver: function(e) {
      var comment = e.detail.comment;
      if (!comment.range) { return; }
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var index = this._indexOfComment(side, comment);
      if (index !== undefined) {
        this.set(['comments', side, index, '__hovering'], true);
      }
    },

    _handleCommentMouseOut: function(e) {
      var comment = e.detail.comment;
      if (!comment.range) { return; }
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var index = this._indexOfComment(side, comment);
      if (index !== undefined) {
        this.set(['comments', side, index, '__hovering'], false);
      }
    },

    _indexOfComment: function(side, comment) {
      var idProp = comment.id ? 'id' : '__draftID';
      for (var i = 0; i < this.comments[side].length; i++) {
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
     * @return {{
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
     * }}
     */
    _getNormalizedRange: function() {
      var selection = window.getSelection();
      var rangeCount = selection.rangeCount;
      if (rangeCount === 0) {
        return null;
      } else if (rangeCount === 1) {
        return this._normalizeRange(selection.getRangeAt(0));
      } else {
        var startRange = this._normalizeRange(selection.getRangeAt(0));
        var endRange = this._normalizeRange(
            selection.getRangeAt(rangeCount - 1));
        return {
          start: startRange.start,
          end: endRange.end,
        };
      }
    },

    /**
     * Normalize a specific DOM Range.
     */
    _normalizeRange: function(domRange) {
      var range = GrRangeNormalizer.normalize(domRange);
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
    _fixTripleClickSelection: function(range, domRange) {
      if (!range.start) {
        // Selection outside of current diff.
        return range;
      }
      var start = range.start;
      var end = range.end;
      var endsAtOtherSideLineNum =
          domRange.endOffset === 0 &&
          domRange.endContainer.nodeName === 'TD' &&
          (domRange.endContainer.classList.contains('left') ||
              domRange.endContainer.classList.contains('right'));
      var endsOnOtherSideStart = endsAtOtherSideLineNum ||
          end &&
          end.column === 0 &&
          end.line === start.line &&
          end.side != start.side;
      if (endsOnOtherSideStart || endsAtOtherSideLineNum) {
        // Selection ends at the beginning of the next line.
        // Move the selection to the end of the previous line.
        range.end = {
          node: start.node,
          column: this._getLength(
              domRange.cloneContents().querySelector('.contentText')),
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
     * @return {{
     *   node: Node,
     *   side: string,
     *   line: Number,
     *   column: Number
     * }}
     */
    _normalizeSelectionSide: function(node, offset) {
      var column;
      if (!this.contains(node)) {
        return;
      }
      var lineEl = this.diffBuilder.getLineElByChild(node);
      if (!lineEl) {
        return;
      }
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      if (!side) {
        return;
      }
      var line = this.diffBuilder.getLineNumberByChild(lineEl);
      if (!line) {
        return;
      }
      var contentText = this.diffBuilder.getContentByLineEl(lineEl);
      if (!contentText) {
        return;
      }
      var contentTd = contentText.parentElement;
      if (!contentTd.contains(node)) {
        node = contentText;
        column = 0;
      } else {
        var thread = contentTd.querySelector('gr-diff-comment-thread');
        if (thread && thread.contains(node)) {
          column = this._getLength(contentText);
          node = contentText;
        } else {
          column = this._convertOffsetToColumn(node, offset);
        }
      }

      return {
        node: node,
        side: side,
        line: line,
        column: column,
      };
    },

    _handleSelection: function() {
      var normalizedRange = this._getNormalizedRange();
      if (!normalizedRange) {
        return;
      }
      var domRange = window.getSelection().getRangeAt(0);
      var start = normalizedRange.start;

      if (!start) {
        return;
      }
      var end = normalizedRange.end;
      if (!end) {
        return;
      }
      if (start.side !== end.side ||
          end.line < start.line ||
          (start.line === end.line && start.column === end.column)) {
        return;
      }

      // TODO (viktard): Drop empty first and last lines from selection.

      var actionBox = document.createElement('gr-selection-action-box');
      Polymer.dom(this.root).appendChild(actionBox);
      actionBox.range = {
        startLine: start.line,
        startChar: start.column,
        endLine: end.line,
        endChar: end.column,
      };
      actionBox.side = start.side;
      if (start.line === end.line) {
        actionBox.placeAbove(domRange);
      } else if (start.node instanceof Text) {
        actionBox.placeAbove(start.node.splitText(start.column));
        start.node.parentElement.normalize(); // Undo splitText from above.
      } else if (start.node.classList.contains('content') &&
                 start.node.firstChild) {
        actionBox.placeAbove(start.node.firstChild);
      } else {
        actionBox.placeAbove(start.node);
      }
    },

    _createComment: function(e) {
      this._removeActionBox();
    },

    _removeActionBoxDebounced: function() {
      this.debounce('removeActionBox', this._removeActionBox, 10);
    },

    _removeActionBox: function() {
      var actionBox = this.$$('gr-selection-action-box');
      if (actionBox) {
        Polymer.dom(this.root).removeChild(actionBox);
      }
    },

    _convertOffsetToColumn: function(el, offset) {
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
     * @param {!Node} startNode
     * @param {function(Node):boolean} callback
     * @param {Object=} opt_flags If flags.left is true, traverse left.
     */
    _traverseContentSiblings: function(startNode, callback, opt_flags) {
      var travelLeft = opt_flags && opt_flags.left;
      var node = startNode;
      while (node) {
        if (node instanceof Element &&
            node.tagName !== 'HL' &&
            node.tagName !== 'SPAN') {
          break;
        }
        var nextNode = travelLeft ? node.previousSibling : node.nextSibling;
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
     * @param {!Node} node
     * @return {number}
     */
    _getLength: function(node) {
      if (node instanceof Element && node.classList.contains('content')) {
        return this._getLength(node.querySelector('.contentText'));
      } else {
        return GrAnnotation.getLength(node);
      }
    },
  });
})();
