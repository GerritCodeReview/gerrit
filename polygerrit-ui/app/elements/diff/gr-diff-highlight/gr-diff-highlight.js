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
      _enabledListeners: {
        type: Object,
        value: function() {
          return {
            'comment-discard': '_handleCommentDiscard',
            'comment-mouse-out': '_handleCommentMouseOut',
            'comment-mouse-over': '_handleCommentMouseOver',
            'create-comment': '_createComment',
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

    detached: function() {
      this.enabled = false;
    },

    _enabledChanged: function() {
      for (var eventName in this._enabledListeners) {
        var methodName = this._enabledListeners[eventName];
        if (this.enabled) {
          this.listen(this, eventName, methodName);
        } else {
          this.unlisten(this, eventName, methodName);
        }
      }
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

    _removeActionBox: function() {
      var actionBox = this.$$('gr-selection-action-box');
      if (actionBox) {
        Polymer.dom(this.root).removeChild(actionBox);
      }
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
      }
    },

    _getLength: function(node) {
      // DOM API unicode length is broken:
      // https://mathiasbynens.be/notes/javascript-unicode
      // TODO (viktard): Polyfill Array.from for IE10.
      if (node instanceof Element && node.classList.contains('content')) {
        node = node.firstChild;
        var length = 0;
        while (node) {
          // Only measure Text nodes and <hl>
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

    _wrapInHighlight: function(node, cssClass) {
      var hl = document.createElement('hl');
      hl.className = cssClass;
      Polymer.dom(node.parentElement).replaceChild(hl, node);
      hl.appendChild(node);
      return hl;
    },

    _splitText: function(node, offset) {
      if (node.textContent.match(/[\uD800-\uDBFF][\uDC00-\uDFFF]/)) {
        // DOM Api is broken for Unicode:
        // https://mathiasbynens.be/notes/javascript-unicode
        // TODO (viktard): Polyfill Array.from for IE10.
        var head = Array.from(node.textContent);
        var tail = head.splice(offset);
        var parent = node.parentElement;
        var headNode = document.createTextNode(head.join(''));
        parent.replaceChild(headNode, node);
        var tailNode = document.createTextNode(tail.join(''));
        parent.insertBefore(tailNode, headNode.nextSibling);
        return tailNode;
      } else {
        return node.splitText(offset);
      }
    },

    _splitAndWrapInHighlight: function(node, offset, cssClass, opt_firstPart) {
      if (this._getLength(node) === offset || offset === 0) {
        return this._wrapInHighlight(node, cssClass);
      } else {
        if (opt_firstPart) {
          this._splitText(node, offset);
          // Node points to first part of the Text, second one is sibling.
        } else {
          node = this._splitText(node, offset);
        }
        return this._wrapInHighlight(node, cssClass);
      }
    },

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
             this._getLength(startNode) <= startOffset ||
             this._getLength(startNode) === 0) {
        startOffset -= this._getLength(startNode);
        startNode = startNode.nextSibling;
      }

      // Split Text node.
      if (startNode instanceof Text) {
        startNode =
            this._splitAndWrapInHighlight(startNode, startOffset, cssClass);
        startContent.insertBefore(startNode, startNode.nextSibling);
        // Edge case: single line, text node wraps the highlight.
        if (isOneLine && this._getLength(startNode) > length) {
          var extra = this._splitText(startNode.firstChild, length);
          startContent.insertBefore(extra, startNode.nextSibling);
          startContent.normalize();
        }
      } else if (startNode.tagName == 'HL') {
        if (!startNode.classList.contains(cssClass)) {
          var hl = startNode;
          startNode = this._splitAndWrapInHighlight(
              startNode.firstChild, startOffset, cssClass);
          startContent.insertBefore(startNode, hl.nextSibling);
          // Edge case: sinle line, <hl> wraps the highlight.
          if (isOneLine && this._getLength(startNode) > length) {
            var trailingHl = hl.cloneNode(false);
            trailingHl.appendChild(
                this._splitText(startNode.firstChild, length));
            startContent.insertBefore(trailingHl, startNode.nextSibling);
          }
          if (hl.textContent.length === 0) {
            hl.remove();
          }
        }
      } else {
        startNode = null;
      }
      return startNode;
    },

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
        endNode =
            this._splitAndWrapInHighlight(endNode, endOffset, cssClass, true);
      } else if (endNode.tagName == 'HL') {
        if (!endNode.classList.contains(cssClass)) {
          // Split text inside HL.
          var hl = endNode;
          endNode = this._splitAndWrapInHighlight(
              endNode.firstChild, endOffset, cssClass, true);
          endContent.insertBefore(endNode, hl);
          if (hl.textContent.length === 0) {
            hl.remove();
          }
        }
      } else {
        endNode = null;
      }
      return endNode;
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
      if (endLine - startLine > 1) {
        // There is at least one line in between.
        var contents = this.diffBuilder.getContentsByLineRange(
            startLine + 1, endLine - 1, opt_side);
        // Wrap contents in highlight.
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

    _rerenderByLines: function(startLine, endLine, opt_side) {
      this.async(function() {
        this.diffBuilder.renderLineRange(startLine, endLine, opt_side);
      }, 1);
    },
  });
})();
