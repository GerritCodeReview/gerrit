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

    _getContentTextParent: function(target) {
      var element = target;
      if (element.nodeName === '#text') {
        element = element.parentElement;
      }
      while (!element.classList.contains('contentText')) {
        if (element.parentElement === null) {
          return target;
        }
        element = element.parentElement;
      }
      return element;
    },

    /**
     * Remap DOM range to whole lines of a diff if necessary. If the start or
     * end containers are DOM elements that are singular pieces of syntax
     * highlighting, the containers are remapped to the .contentText divs that
     * contain the entire line of code.
     *
     * @param  {Object} range - the standard DOM selector range.
     * @return {Object} A modified version of the range that correctly accounts
     *     for syntax highlighting.
     */
    _normalizeRange: function(range) {
      var startContainer = this._getContentTextParent(range.startContainer);
      var startOffset = range.startOffset + this._getTextOffset(startContainer,
          range.startContainer);
      var endContainer = this._getContentTextParent(range.endContainer);
      var endOffset = range.endOffset + this._getTextOffset(endContainer,
          range.endContainer);
      return {
        start: this._normalizeSelectionSide(startContainer, startOffset),
        end: this._normalizeSelectionSide(endContainer, endOffset),
      };
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
      var normalizedRange = this._normalizeRange(range);
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

    /**
     * Gets the character offset of the child within the parent.
     * Performs a synchronous in-order traversal from top to bottom of the node
     * element, counting the length of the syntax until child is found.
     *
     * @param {!Element} The root DOM element to be searched through.
     * @param {!Element} The child element being searched for.
     * @return {number}
     */
    _getTextOffset: function(node, child) {
      var count = 0;
      var stack = [node];
      while (stack.length) {
        var n = stack.pop();
        if (n === child) {
          break;
        }
        if (n.childNodes && n.childNodes.length !== 0) {
          var arr = [];
          for (var i = 0; i < n.childNodes.length; i++) {
            arr.push(n.childNodes[i]);
          }
          arr.reverse();
          stack = stack.concat(arr);
        } else {
          count += this._getLength(n);
        }
      }
      return count;
    },
  });
})();
