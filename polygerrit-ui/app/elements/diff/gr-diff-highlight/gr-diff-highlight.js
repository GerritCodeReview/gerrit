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
    },

    listeners: {
      'comment-mouse-out': '_handleCommentMouseOut',
      'comment-mouse-over': '_handleCommentMouseOver',
      'create-comment': '_createComment',
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
