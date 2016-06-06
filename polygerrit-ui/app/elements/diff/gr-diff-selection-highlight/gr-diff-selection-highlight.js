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
      'comment-discard': '_handleCommentDiscard',
      'comment-mouse-out': '_handleCommentMouseOut',
      'comment-mouse-over': '_handleCommentMouseOver',
      'create-comment': '_createComment',
      'down': '_handleDown',
      'render': '_handleRender',
      'show-context': '_handleShowContext',
      'thread-discard': '_handleThreadDiscard',
      'up': '_handleUp',
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

    _handleDown: function(e) {
      var actionBox = this.$$('gr-selection-action-box');
      if (actionBox && !actionBox.contains(e.target)) {
        this._removeActionBox();
      }

      var lineEl = this._getLineElByChild(e.target);
      if (lineEl) {
        var side =
              lineEl.classList.contains('right') ? 'right' : 'left';
        this.classList.remove('selected-right', 'selected-left');
        this.classList.add('selected-' + side);
      }
    },

    _handleThreadDiscard: function(e) {
      var comment = e.detail.lastComment;
      // was removed from DOM already
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

    _renderCommentRange: function(comment, el) {
      var lineEl = this._getLineElByChild(el);
      if (!lineEl) {
        return;
      }
      var side = lineEl.classList.contains('right') ? 'right' : 'left';
      this._rerenderByLines(
        comment.range.start_line, comment.range.end_line, side);
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

    _handleUp: function(e) {
      var selection = window.getSelection();
      if (selection.rangeCount == 1) { // No multi-select support yet.
        this._handleSelection(selection.getRangeAt(0));
      }
    },

    _handleSelection: function(range) {
      var startEl = range.startContainer;
      var startChar = range.startOffset;
      var endEl = range.endContainer;
      var endChar = range.endOffset;
      if (!this.contains(startEl) || !this.contains(endEl)) {
        return;
      }

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

      var startContent = this.diffBuilder.getContentByLineEl(startLineEl);
      if (!startContent.contains(startEl)) {
        startEl = startContent;
        startChar = 0;
      }
      var endContent = this.diffBuilder.getContentByLineEl(endLineEl);
      if (!endContent.contains(endEl)) {
        endEl = endContent;
        endChar = 0;
      }
      var startSide =
            startLineEl.classList.contains('right') ? 'right' : 'left';
      var endSide =
            endLineEl.classList.contains('right') ? 'right' : 'left';

      if (startSide != endSide) {
        return; // Ranged comments over different diff sides.
      }

      var startLine = this._getLineNumByElement(startLineEl);
      var endLine = this._getLineNumByElement(endLineEl);
      if (!startLine || !endLine || range.collapsed) {
        return; // Not a valid selection
      }

      var thread = startContent.querySelector('gr-diff-comment-thread');
      if (thread && thread.contains(startEl)) {
        startChar = 0;
        startLine++;
        startEl = this.diffBuilder.getContentByLine(startLine, startSide);
      }

      startChar = this._convertOffsetToColumn(startEl, startChar);
      // Drop empty first lines from selection.
      while (startEl.textContent.length === 0) {
        startChar = 0;
        startLine++;
        startEl = this.diffBuilder.getContentByLine(startLine, startSide);
      }

      thread = endContent.querySelector('gr-diff-comment-thread');
      if (thread && thread.contains(endEl)) {
        endEl = this.diffBuilder.getContentByLine(endLine, endSide);
        endChar = this._getLength(endEl);
      }

      endChar = this._convertOffsetToColumn(endEl, endChar);
      while (endChar === 0) {
        // Drop empty last lines from selection.
        endLine--;
        endChar = this._getLength(
            this.diffBuilder.getContentByLine(endLine, startSide));
      }

      if (endLine < startLine) {
        return; // Empty selection.
      }

      if (startChar === 0 && startEl instanceof Element &&
          startEl.classList.contains('content')) {
        startEl = startEl.firstChild;
      }

      if (startLine === endLine && startChar === endChar) {
        return; // Empty selection.
      }

      if (endChar === 0 && endEl instanceof Element &&
          endEl.classList.contains('content')) {
        endEl = endEl.firstChild;
      }

      var actionBox = document.createElement('gr-selection-action-box');
      Polymer.dom(this.root).appendChild(actionBox);
      actionBox.range = {
        startLine: startLine,
        startChar: startChar,
        endLine: endLine,
        endChar: endChar,
      };
      actionBox.side = startSide;
      if (startLine == endLine) {
        actionBox.placeAbove(range);
      } else if (startEl instanceof Text) {
        actionBox.placeAbove(startEl.splitText(range.startOffset));
        actionBox.parentElement.normalize(); // Undo splitText from above.
      } else {
        actionBox.placeAbove(startEl);
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

      var length = endOffset - startOffset;

      // Skip nodes before startOffset.
      while (startNode &&
             this._getLength(startNode) < startOffset ||
             this._getLength(startNode) === 0) {
        startOffset -= this._getLength(startNode);
        startNode = startNode.nextSibling;
      }

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
      while (endNode &&
             this._getLength(endNode) < endOffset ||
             this._getLength(endNode) === 0) {
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
      }
      return endNode;
    },

    _getLength: function(node) {
      // DOM API unicode length is broken:
      // https://mathiasbynens.be/notes/javascript-unicode
      // TODO (viktard): Polyfill Array.from for IE10.
      if (node instanceof Element && node.classList.contains('content')) {
        // Exclude comments from length calculations.
        node = node.firstChild;
        var length = 0;
        while (node) {
          if (node instanceof Text || node.tagName == 'HL') {
            length += this._getLength(node);
          }
          node = node.nextSibling;
        }
        return length;
      } else {
        return Array.from(node.textContent).length;
      }
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
          if (node instanceof Element && node.tagName !== 'HL') {
            break;
          }
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
        // Wrap contents in highlight
        contents.forEach(function(content) {
          if (content.textContent.length === 0) {
            return;
          }
          var text = document.createTextNode(content.textContent);
          while (content.firstChild) {
            content.removeChild(content.firstChild);
          }
          content.appendChild(text);
          this._wrapInHighlight(text, cssClass);
        }, this);
      }
    },

    _createComment: function(e) {
      this._removeActionBox();
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
      // TODO (viktard): re-render expanded sections only.
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
      var lineEl = this._getLineElByChild(e.target);
      var side =
          lineEl.classList.contains('right') ? 'right' : 'left';
      var contentEls = this.diffBuilder.getContentsByLineRange(
        comment.range.start_line, comment.range.end_line, side);
      contentEls.forEach(function(content) {
        Polymer.dom(content).querySelectorAll('.' + HOVER_HIGHLIGHT).forEach(
          function(el) {
            el.classList.remove(HOVER_HIGHLIGHT);
            el.classList.add(RANGE_HIGHLIGHT);
          });
      }, this);
    },

    _rerenderByLines: function(startLine, endLine, opt_side) {
      this.async(function() {
        this.diffBuilder.renderLineRange(startLine, endLine, opt_side);
      }, 1);
    },
  });
})();
