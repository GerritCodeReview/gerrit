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

  var SELECTION_DEBOUNCE_INTERVAL = 200;
  var RANGE_HIGHLIGHT = 'range';
  var HOVER_HIGHLIGHT = 'rangeHighlight';

  Polymer({
    is: 'gr-diff-selection-highlight',

    properties: {
      comments: Object,
    },

    listeners: {
      'comment-mouse-out': '_handleCommentMouseOut',
      'comment-mouse-over': '_handleCommentMouseOver',
      'copy': '_handleCopy',
      'create-comment': '_createComment',
      'mousedown': '_handleMouseDown',
      'render': '_handleRender',
      'show-context': '_handleShowContext',
      'thread-discard': '_handleThreadDiscard',
    },

    attached: function() {
      this.listen(document, 'selectionchange', '_selectionChanged');
    },

    detached: function() {
      this.unlisten(document, 'selectionchange', '_selectionChanged');
      this.cancelDebouncer('handleSelection');
    },

    get diffBuilder() {
      return Polymer.dom(this).querySelector('gr-diff-builder');
    },

    get diffElement() {
      return Polymer.dom(this).querySelector('#diffTable');
    },

    isRangeSelected: function() {
      return !!this.$$('gr-selection-action-box');
    },

    _handleMouseDown: function(e) {
      var lineEl = this._getLineElByChild(e.target);
      if (!lineEl) {
        return;
      }
      var side =
          lineEl.classList.contains('right') ? 'right' : 'left';
      this.classList.remove('selected-right', 'selected-left');
      this.classList.add('selected-' + side);
    },

    _handleThreadDiscard: function(e) {
      var comment = e.detail.lastComment;
      // was removed from DOM already
      if (comment.range) {
        var lineEl = this._getLineElByChild(e.target);
        var side =
            lineEl.classList.contains('right') ? 'right' : 'left';
        this._rerenderByLines(
            comment.range.start_line, comment.range.end_line, side);
      }
    },

    _handleCopy: function(e) {
      if (!e.target.classList.contains('content')) {
        return;
      }
      var lineEl = this._getLineElByChild(e.target);
      if (!lineEl) {
        return;
      }
      var side =
          lineEl.classList.contains('right') ? 'right' : 'left';
      var text = this._getSelectedText(side);
      e.clipboardData.setData('Text', text);
      e.preventDefault();
    },

    _getSelectedText: function(opt_side) {
      var sel = window.getSelection();
      if (sel.rangeCount != 1) {
        return; // No multi-select support yet.
      }
      var range = sel.getRangeAt(0);
      var doc = range.cloneContents();
      var selector = '.content,td.content:nth-of-type(1)';
      if (opt_side) {
        selector = '.' + opt_side + ' + ' + selector;
      }
      var contentEls = Polymer.dom(doc).querySelectorAll(selector);
      if (contentEls.length === 0) {
        return doc.textContent;
      }

      var text = '';
      for (var i = 0; i < contentEls.length; i++) {
        text += contentEls[i].textContent + '\n';
      }
      return text;
    },

    _selectionChanged: function() {
      this.debounce('handleSelection', this._handleSelection,
          SELECTION_DEBOUNCE_INTERVAL);
    },

    _handleSelection: function() {
      this._removeActionBox();

      var selection = window.getSelection();
      if (selection.rangeCount != 1) {
        return; // No multi-select support yet.
      }
      var range = selection.getRangeAt(0);
      var startEl = range.startContainer;
      var startChar = range.startOffset;
      var endEl = range.endContainer;
      var endChar = range.endOffset;
      if (this.contains(startEl) &&
          this.contains(endEl)) {

        if (endEl instanceof Element
            && endEl.classList.contains('contextLineNum')) {
          endEl = endEl.parentElement.parentElement.previousElementSibling
              .lastElementChild.querySelector('.content');
        }

        if (startEl instanceof Element
            && startEl.classList.contains('contextLineNum')) {
          startEl = startEl.parentElement.parentElement.nextElementSibling
              .querySelector('.content');
        }
        var startLineEl = this._getLineElByChild(startEl);
        var endLineEl = this._getLineElByChild(endEl);
        if (!startLineEl || !endLineEl) {
          return;
        }
        var startSide =
            startLineEl.classList.contains('right') ? 'right' : 'left';
        var endSide =
            endLineEl.classList.contains('right') ? 'right' : 'left';

        if (startSide != endSide) {
          console.log(
              'Not implemented: ranged comments over different diff sides');
          return;
        }

        var startLine = this._getLineNumByElement(startLineEl);
        var endLine = this._getLineNumByElement(endLineEl);
        if (!startLine || !endLine || range.collapsed) {
          return; // not a valid selection
        }

        var actionBox = document.createElement('gr-selection-action-box');
        Polymer.dom(this.root).appendChild(actionBox);
        actionBox.range = {
          startLine: startLine,
          startChar: this._convertOffsetToColumn(startEl, startChar),
          endLine: endLine,
          endChar: this._convertOffsetToColumn(endEl, endChar),
        };
        actionBox.side = startSide;
        if (startLine == endLine) {
          actionBox.placeAbove(range);
        } else {
          actionBox.placeAbove(startEl);
        }
      }
    },

    _handleRender: function() {
      this._applyAllHighlights();
    },

    _convertOffsetToColumn: function(el, offset) {
      if (el instanceof Element && el.classList.contains('content')) {
        return offset;
      }
      while (el.previousSibling ||
          !el.parentElement.classList.contains('content')) {
        if (el.previousSibling) {
          el = el.previousSibling;
          offset += el.textContent.length;
        } else {
          el = el.parentElement;
        }
      }
      return offset;
    },

    _wrapInHighlight: function(node, cssClass) {
      var hl = document.createElement('hl');
      hl.className = cssClass;
      Polymer.dom(node.parentElement).replaceChild(hl, node);
      hl.appendChild(node);
      return hl;
    },

    // expected to start in td.content, containing mix of Text and <hl>TEXT</hl>
    _normalizeStart: function(
        startContent, endContent, startOffset, endOffset, cssClass) {

      var isOneLine = startContent === endContent;
      var startNode;
      startNode = startContent.firstChild;

      if (!startNode) {
        return startNode;
      }

      // Skip nodes before startOffset.
      while (startNode && this._getLength(startNode) < startOffset) {
        startOffset -= this._getLength(startNode);
        startNode = startNode.nextSibling;
      }

      var length = endOffset - startOffset;

      // Split Text node.
      if (startNode instanceof Text) {
        startNode =
            this._wrapInHighlight(startNode.splitText(startOffset), cssClass);
        startContent.insertBefore(startNode, startNode.nextSibling);
        // Edge case: single line, text node wraps the highlight.
        if (isOneLine && this._getLength(startNode) > length) {
          var extra = startNode.firstChild.splitText(length);
          startContent.insertBefore(extra, startNode.nextSibling);
          startContent.normalize();
        }
      } else if (startNode.tagName == 'HL') {
        if (!startNode.classList.contains(cssClass)) {
          var hl = startNode;
          var text = startNode.firstChild;
          startNode =
              this._wrapInHighlight(text.splitText(startOffset), cssClass);
          startContent.insertBefore(startNode, hl.nextSibling);
          // Edge case: sinle line, <hl> wraps the highlight.
          if (isOneLine && this._getLength(startNode) > length) {
            var trailingHl = hl.cloneNode(false);
            trailingHl.appendChild(
                startNode.firstChild.splitText(
                    this._getLength(startNode) - length));
            startContent.insertBefore(trailingHl, startNode.nextSibling);
          }
        }
      } else {
        // throw new Error('Unexpected node while applying highlight.');
        startNode = null;
      }
      return startNode;
    },

    // expected to end in td.content, containing mix of Text and <hl>TEXT</hl>
    _normalizeEnd: function(
        startContent, endContent, startOffset, endOffset, cssClass) {
      var endNode = endContent.firstChild;

      if (!endNode) {
        return endNode;
      }

      // Find the node where endOffset points at.
      while (endNode && this._getLength(endNode) <= endOffset) {
        endOffset -= this._getLength(endNode);
        endNode = endNode.nextSibling;
      }

      if (endNode instanceof Text) {
        // Split Text node.
        var text = endNode;
        var textTail = text.splitText(endOffset);
        endNode = this._wrapInHighlight(text, cssClass);
      } else if (endNode.tagName == 'HL') {
        if (!endNode.classList.contains(cssClass)) {
          // Split text inside HL.
          var hl = endNode;
          var text = endNode.firstChild;
          var textTail = text.splitText(endOffset);
          endNode = this._wrapInHighlight(text, cssClass);
          endContent.insertBefore(endNode, hl);
        }
      } else {
        endNode = null;
        // throw new Error('Unexpected node while applying highlight.');
      }
      return endNode;
    },

    _getLength: function(node) {
      // DOM API unicode length is broken:
      // https://mathiasbynens.be/notes/javascript-unicode
      // TODO (viktard): Polyfill Array.from for IE10.
      return Array.from(node.textContent).length;
    },

    _highlightSides: function(
        startContent, endContent, startOffset, endOffset, cssClass) {

      var isOneLine = startContent === endContent;
      var startNode = this._normalizeStart(
          startContent, endContent, startOffset, endOffset, cssClass);
      var endNode = this._normalizeEnd(
          startContent, endContent, startOffset, endOffset, cssClass);

      // Grow starting highlight until endNode or end of line.
      if (startNode && startNode != endNode) {
        var node = startNode.nextSibling;
        while (node && node != endNode) {
          startNode.textContent += node.textContent;
          var prevNode = node;
          node = node.nextSibling;
          prevNode.remove();
        };
      }

      if (!isOneLine && endNode) {
        // Prepend to ending highlight until start of the line.
        var node = endNode.previousSibling;
        while (node) {
          endNode.textContent = node.textContent + endNode.textContent;
          var prevNode = node;
          node = node.previousSibling;
          prevNode.remove();
        };
      }
    },

    _applyRangedHighlight: function(
        cssClass, startLine, startCol, endLine, endCol, opt_side) {
      var side = opt_side;
      var startEl = this.diffBuilder.getContentByLine(startLine, opt_side);
      var endEl = this.diffBuilder.getContentByLine(endLine, opt_side);
      this._highlightSides(startEl, endEl, startCol, endCol, cssClass);
      if (endLine - startLine > 1) { // there is at least one line inbetween
        var contents = this.diffBuilder.getContentsByLineRange(
            startLine + 1, endLine - 1, opt_side);
        contents.forEach(function(content) {
          content.classList.add(cssClass);
        });
      }
    },

    _createComment: function(e) {
      var side = e.detail.side;
      var range = e.detail.range;
      if (!range) {
        return;
      }
      this.async(function() {
        this._applyRangedHighlight(
            RANGE_HIGHLIGHT, range.startLine, range.startChar,
            range.endLine, range.endChar, side);
      }, 1);
      this._removeActionBox();
    },

    _removeActionBox: function() {
      var actionBox = this.$$('gr-selection-action-box');
      if (actionBox) {
        Polymer.dom(this.root).removeChild(actionBox);
      }
    },

    _getLineElByChild: function(element) {
      // go up until .section or .lineNum
      while (element && (!(element instanceof Element) ||
          !(element.classList.contains('section') ||
          element.classList.contains('lineNum')))) {
        element = element.previousSibling || element.parentElement;
      };
      return (element && element.classList.contains('lineNum')) ?
          element : null;
    },

    _getLineNumByElement: function(element) {
      element = this._getLineElByChild(element);
      return element ? parseInt(element.getAttribute('data-value')) : null;
    },

    _handleShowContext: function() {
      this._applyAllHighlights();
    },

    _applyAllHighlights: function() {
      // TODO: find changed sections.
      var rangedLeft =
          this.comments.left.filter(function(item) { return !!item.range;});
      var rangedRight =
          this.comments.right.filter(function(item) { return !!item.range;});
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

    _handleCommentMouseOver: function(e) {
      var comment = e.detail.comment;
      if (!comment.range) {
        return;
      }
      var lineEl = this._getLineElByChild(e.target);
      var side =
          lineEl.classList.contains('right') ? 'right' : 'left';
      var range = comment.range;
      this._applyRangedHighlight(
          HOVER_HIGHLIGHT, range.start_line, range.start_character,
          range.end_line, range.end_character, side);
    },

    _handleCommentMouseOut: function(e) {
      var comment = e.detail.comment;
      if (!comment.range) {
        return;
      }
      console.log('mouse out, render');
      var lineEl = this._getLineElByChild(e.target);
      var side =
          lineEl.classList.contains('right') ? 'right' : 'left';
      this._rerenderByLines(
          comment.range.start_line, comment.range.end_line, side);
    },

    _rerenderByLines: function(startLine, endLine, opt_side) {
      this.async(function() {
        this.diffBuilder.renderLineRange(startLine, endLine, opt_side);
      }, 1);
    },
  });
})();
