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

  var RANGE_HIGHLIGHT = 'range';
  var HOVER_HIGHLIGHT = 'rangeHighlight';

  Polymer({
    is: 'gr-diff-highlight',

    properties: {
      comments: Object,
      loggedIn: Boolean,
      _cachedDiffBuilder: Object,
    },

    listeners: {
      'comment-discard': '_handleCommentDiscard',
      'comment-mouse-out': '_handleCommentMouseOut',
      'comment-mouse-over': '_handleCommentMouseOver',
      'create-comment': '_createComment',
      'render': '_handleRender',
      'show-context': '_handleShowContext',
      'thread-discard': '_handleThreadDiscard',
    },

    get diffBuilder() {
      if (!this._cachedDiffBuilder) {
        this._cachedDiffBuilder =
            Polymer.dom(this).querySelector('gr-diff-builder');
      }
      return this._cachedDiffBuilder;
    },

    attached: function() {
      this.listen(document, 'selectionchange', '_handleSelectionChange');
    },

    detached: function() {
      this.unlisten(document, 'selectionchange', '_handleSelectionChange');
    },

    isRangeSelected: function() {
      return !!this.$$('gr-selection-action-box');
    },

    _handleThreadDiscard: function(e) {
      var comment = e.detail.lastComment;
      // Comment Element was removed from DOM already.
      if (comment.range) {
        this._renderCommentRange(comment, e.target);
      }
    },

    _handleCommentDiscard: function(e) {
      var comment = e.detail.comment;
      if (comment.range) {
        this._renderCommentRange(comment, e.target);
      }
    },

    _handleSelectionChange: function() {
      // Can't use up or down events to handle selection started and/or ended in
      // in comment threads or outside of diff.
      // Debounce removeActionBox to give it a chance to react to click/tap.
      this._removeActionBoxDebounced();
      this.debounce('selectionChange', this._handleSelection, 200);
    },

    _handleRender: function() {
      this._applyAllHighlights();
    },

    _handleShowContext: function() {
      // TODO (viktard): Re-render expanded sections only.
      this._applyAllHighlights();
    },

    _handleCommentMouseOver: function(e) {
      var comment = e.detail.comment;
      var range = comment.range;
      if (!range) {
        return;
      }
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      this._applyRangedHighlight(
          HOVER_HIGHLIGHT, range.start_line, range.start_character,
          range.end_line, range.end_character, side);
    },

    _handleCommentMouseOut: function(e) {
      var comment = e.detail.comment;
      var range = comment.range;
      if (!range) {
        return;
      }
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var contentEls = this.diffBuilder.getContentsByLineRange(
          range.start_line, range.end_line, side);
      contentEls.forEach(function(content) {
        Polymer.dom(content).querySelectorAll('.' + HOVER_HIGHLIGHT).forEach(
            function(el) {
              el.classList.remove(HOVER_HIGHLIGHT);
              el.classList.add(RANGE_HIGHLIGHT);
            });
      }, this);
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
      var selection = window.getSelection();
      if (selection.rangeCount != 1) {
        return;
      }
      var range = selection.getRangeAt(0);
      if (range.collapsed) {
        return;
      }
      var start =
          this._normalizeSelectionSide(range.startContainer, range.startOffset);
      if (!start) {
        return;
      }
      var end =
          this._normalizeSelectionSide(range.endContainer, range.endOffset);
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
        actionBox.placeAbove(range);
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

    _renderCommentRange: function(comment, el) {
      var lineEl = this.diffBuilder.getLineElByChild(el);
      if (!lineEl) {
        return;
      }
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      this._rerenderByLines(
          comment.range.start_line, comment.range.end_line, side);
    },

    _createComment: function(e) {
      this._removeActionBox();
      var side = e.detail.side;
      var range = e.detail.range;
      if (!range) {
        return;
      }
      this._applyRangedHighlight(
          RANGE_HIGHLIGHT, range.startLine, range.startChar,
          range.endLine, range.endChar, side);
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

    /**
     * Creates hl tag with cssClass for starting side of range highlight.
     *
     * @param {!Element} startContent Range start diff content
     *     aka div.contentText.
     * @param {!Element} endContent Range end diff content
     *     aka div.contentText.
     * @param {number} startOffset Range start within start content.
     * @param {number} endOffset Range end within end content.
     * @param {string} cssClass
     * @return {!Element} Range start node.
     */
    _normalizeStart: function(
        startContent, endContent, startOffset, endOffset, cssClass) {
      var isOneLine = startContent === endContent;
      var startNode = startContent.firstChild;
      var length = endOffset - startOffset;

      if (!startNode) {
        return startNode;
      }

      // Skip nodes before startOffset.
      var nodeLength = this._getLength(startNode);
      while (startNode && (nodeLength <= startOffset || nodeLength === 0)) {
        startOffset -= nodeLength;
        startNode = startNode.nextSibling;
        nodeLength = startNode && this._getLength(startNode);
      }
      if (!startNode) { return null; }

      // Split Text node.
      if (startNode instanceof Text) {
        startNode = GrAnnotation.splitAndWrapInHighlight(
            startNode, startOffset, cssClass);
        // Edge case: single line, text node wraps the highlight.
        if (isOneLine && this._getLength(startNode) > length) {
          var extra = GrAnnotation.splitTextNode(startNode.firstChild, length);
          startContent.insertBefore(extra, startNode.nextSibling);
          startContent.normalize();
        }
      } else if (startNode.tagName == 'HL') {
        if (!startNode.classList.contains(cssClass)) {
          // Edge case: single line, <hl> wraps the highlight.
          // Should leave wrapping HL's content after the highlight.
          if (isOneLine && startOffset + length < this._getLength(startNode)) {
            GrAnnotation.splitNode(startNode, startOffset + length);
          }
          startNode = GrAnnotation.splitAndWrapInHighlight(
              startNode, startOffset, cssClass);
        }
      } else if (startNode.tagName == 'SPAN') {
        startNode = GrAnnotation.splitAndWrapInHighlight(
            startNode, startOffset, cssClass);
      } else {
        startNode = null;
      }
      return startNode;
    },

    /**
     * Creates hl tag with cssClass for ending side of range highlight.
     *
     * @param {!Element} startContent Range start diff content
     *     aka div.contentText.
     * @param {!Element} endContent Range end diff content
     *     aka div.contentText.
     * @param {number} startOffset Range start within start content.
     * @param {number} endOffset Range end within end content.
     * @param {string} cssClass
     * @return {!Element} Range start node.
     */
    _normalizeEnd: function(
        startContent, endContent, startOffset, endOffset, cssClass) {
      var endNode = endContent.firstChild;

      if (!endNode) {
        return endNode;
      }

      // Find the node where endOffset points at.
      var nodeLength = this._getLength(endNode);
      while (endNode && (nodeLength < endOffset || nodeLength === 0)) {
        endOffset -= nodeLength;
        endNode = endNode.nextSibling;
        nodeLength = endNode && this._getLength(endNode);
      }
      if (!endNode) { return null; }

      if (endNode instanceof Text) {
        endNode = GrAnnotation.splitAndWrapInHighlight(
            endNode, endOffset, cssClass, true);
      } else if (endNode.tagName == 'HL') {
        if (!endNode.classList.contains(cssClass)) {
          // Split text inside HL.
          var hl = endNode;
          endNode = GrAnnotation.splitAndWrapInHighlight(
              endNode, endOffset, cssClass, true);
          if (hl.textContent.length === 0) {
            hl.remove();
          }
        }
      } else {
        endNode = null;
      }
      return endNode;
    },

    /**
     * Applies highlight to first and last lines in range.
     *
     * @param {!Element} startContent Range start diff content
     *     aka div.contentText.
     * @param {!Element} endContent Range end diff content
     *     aka div.contentText.
     * @param {number} startOffset Range start within start content.
     * @param {number} endOffset Range end within end content.
     * @param {string} cssClass
     */
    _highlightSides: function(
        startContent, endContent, startOffset, endOffset, cssClass) {
      var isOneLine = startContent === endContent;
      var startNode = this._normalizeStart(
          startContent, endContent, startOffset, endOffset, cssClass);
      var endNode = this._normalizeEnd(
          startContent, endContent, startOffset, endOffset, cssClass);

      // Grow starting highlight until endNode or end of line.
      if (startNode && startNode != endNode) {
        var growStartHl = function(node) {
          if (node instanceof Text || node.tagName === 'SPAN') {
            startNode.appendChild(node);
          } else if (node.tagName === 'HL') {
            this._traverseContentSiblings(node.firstChild, growStartHl);
            node.remove();
          }
          return node == endNode;
        }.bind(this);
        this._traverseContentSiblings(startNode.nextSibling, growStartHl);
        startNode.normalize();
      }

      if (!isOneLine && endNode) {
        var growEndHl = function(node) {
          if (node instanceof Text || node.tagName === 'SPAN') {
            endNode.insertBefore(node, endNode.firstChild);
          } else if (node.tagName === 'HL') {
            this._traverseContentSiblings(node.firstChild, growEndHl);
            node.remove();
          }
        }.bind(this);
        // Prepend text up to line start to the ending highlight.
        this._traverseContentSiblings(
          endNode.previousSibling, growEndHl, {left: true});
        endNode.normalize();
      }
    },

    /**
     * @param {string} cssClass
     * @param {number} startLine Range start code line number.
     * @param {number} startCol Range start column number.
     * @param {number} endLine Range end line number.
     * @param {number} endCol Range end column number.
     * @param {string=} opt_side Side selector (right or left).
     */
    _applyRangedHighlight: function(
        cssClass, startLine, startCol, endLine, endCol, opt_side) {
      var startEl = this.diffBuilder.getContentByLine(startLine, opt_side);
      var endEl = this.diffBuilder.getContentByLine(endLine, opt_side);
      this._highlightSides(startEl, endEl, startCol, endCol, cssClass);
      if (endLine - startLine > 1) {
        // There is at least one line in between.
        var contents = this.diffBuilder.getContentsByLineRange(
            startLine + 1, endLine - 1, opt_side);
        contents.forEach(function(content) {
          if (!content.firstChild) {
            return;
          }
          // Wrap contents in highlight.
          var hl = GrAnnotation.wrapInHighlight(content.firstChild, cssClass);
          var wrapInHl = function(node) {
            if (node instanceof Text || node.tagName === 'SPAN') {
              hl.appendChild(node);
            } else if (node.tagName === 'HL') {
              this._traverseContentSiblings(node.firstChild, wrapInHl);
              node.remove();
            }
            return node === content.lastChild;
          }.bind(this);
          this._traverseContentSiblings(hl.nextSibling, wrapInHl);
          hl.normalize();
        }, this);
      }
    },

    _applyAllHighlights: function() {
      var rangedLeft =
          this.comments.left.filter(function(item) { return !!item.range; });
      var rangedRight =
          this.comments.right.filter(function(item) { return !!item.range; });
      rangedLeft.forEach(function(item) {
        var range = item.range;
        this._applyRangedHighlight(
            RANGE_HIGHLIGHT, range.start_line, range.start_character,
            range.end_line, range.end_character, 'left');
      }, this);
      rangedRight.forEach(function(item) {
        var range = item.range;
        this._applyRangedHighlight(
            RANGE_HIGHLIGHT, range.start_line, range.start_character,
            range.end_line, range.end_character, 'right');
      }, this);
    },

    _rerenderByLines: function(startLine, endLine, opt_side) {
      this.async(function() {
        this.diffBuilder.renderLineRange(startLine, endLine, opt_side);
      }, 1);
    },
  });
})();
