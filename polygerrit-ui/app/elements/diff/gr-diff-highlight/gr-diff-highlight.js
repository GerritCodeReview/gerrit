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
      enabled: {
        type: Boolean,
        observer: '_enabledChanged',
      },
      loggedIn: Boolean,
      _cachedDiffBuilder: Object,
      _diffElement: Object,
      _enabledListeners: {
        type: Object,
        value: function() {
          return {
            'comment-discard': '_handleCommentDiscard',
            'comment-mouse-out': '_handleCommentMouseOut',
            'comment-mouse-over': '_handleCommentMouseOver',
            'create-comment': '_createComment',
            'render': '_handleRender',
            'show-context': '_handleShowContext',
            'thread-discard': '_handleThreadDiscard',
          };
        },
      },
    },

    get diffBuilder() {
      if (!this._cachedDiffBuilder) {
        this._cachedDiffBuilder =
            Polymer.dom(this).querySelector('gr-diff-builder');
      }
      return this._cachedDiffBuilder;
    },

    get diffElement() {
      if (!this._cachedDiffElement) {
        this._cachedDiffElement = Polymer.dom(this).querySelector('#diffTable');
      }
      return this._cachedDiffElement;
    },

    detached: function() {
      this.enabled = false;
    },

    _enabledChanged: function() {
      if (this.enabled) {
        this.listen(document, 'selectionchange', '_handleSelectionChange');
      } else {
        this.unlisten(document, 'selectionchange', '_handleSelectionChange');
      };
      for (var eventName in this._enabledListeners) {
        var methodName = this._enabledListeners[eventName];
        if (this.enabled) {
          this.listen(this, eventName, methodName);
        } else {
          this.unlisten(this, eventName, methodName);
        };
      };
    },

    isRangeSelected: function() {
      return !!this.$$('gr-selection-action-box');
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
      // TODO (viktard): re-render expanded sections only.
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

    _handleSelection: function() {
      var selection = window.getSelection();
      if (selection.rangeCount != 1) {
        return;
      }
      var range = selection.getRangeAt(0);
      var startEl = range.startContainer;
      var startChar = range.startOffset;
      var endEl = range.endContainer;
      var endChar = range.endOffset;
      if (!this.contains(startEl) || !this.contains(endEl) || range.collapsed) {
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

      var startLineEl = this.diffBuilder.getLineElByChild(startEl);
      var endLineEl = this.diffBuilder.getLineElByChild(endEl);

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

      var startLine = this.diffBuilder.getLineNumberByChild(startLineEl);
      var endLine = this.diffBuilder.getLineNumberByChild(endLineEl);
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

    _renderCommentRange: function(comment, el) {
      var lineEl = this.diffBuilder.getLineElByChild(el);
      if (!lineEl) {
        return;
      }
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      this._rerenderByLines(
          comment.range.start_line, comment.range.end_line, side);
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

    _traverseContent: function(startNode, callback, flags) {
      var travelLeft = flags && flags.left;
      var node = startNode;
      while (node) {
        if (node instanceof Element && node.tagName !== 'HL') {
          break;
        }
        var nextNode = travelLeft ? node.previousSibling : node.nextSibling;
        var shouldBreak = callback(node);
        if (shouldBreak) {
          break;
        } else {
          node = nextNode;
        }
      };
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
        this._traverseContent(startNode.nextSibling, function(node) {
          startNode.textContent += node.textContent;
          node.remove();
          return node == endNode;
        });
      }

      if (!isOneLine && endNode) {
        // Prepend text up line start to the ending highlight.
        this._traverseContent(endNode.previousSibling, function(node) {
          endNode.textContent = node.textContent + endNode.textContent;
          node.remove();
        }, {left: true});
      }
    },

    _applyRangedHighlight: function(
        cssClass, startLine, startCol, endLine, endCol, opt_side) {
      var side = opt_side;
      var startEl = this.diffBuilder.getContentByLine(startLine, opt_side);
      var endEl = this.diffBuilder.getContentByLine(endLine, opt_side);
      this._highlightSides(startEl, endEl, startCol, endCol, cssClass);
      if (endLine - startLine > 1) { // there is at least one line in between
        var contents = this.diffBuilder.getContentsByLineRange(
            startLine + 1, endLine - 1, opt_side);
        // Wrap contents in highlight
        contents.forEach(function(content) {
          if (content.textContent.length === 0) {
            return;
          }
          var lineEl = this.diffBuilder.getLineElByChild(content);
          var line = lineEl.getAttribute('data-value');
          var threadEl =
                this.diffBuilder.getCommentThreadByContentEl(content);
          if (threadEl) {
            threadEl.remove();
          }
          var text = document.createTextNode(content.textContent);
          while (content.firstChild) {
            content.removeChild(content.firstChild);
          }
          content.appendChild(text);
          if (threadEl) {
            content.appendChild(threadEl);
          }
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

    _applyAllHighlights: function() {
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

    _rerenderByLines: function(startLine, endLine, opt_side) {
      this.async(function() {
        this.diffBuilder.renderLineRange(startLine, endLine, opt_side);
      }, 1);
    },
  });
})();
