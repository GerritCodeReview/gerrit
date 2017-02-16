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

  /**
   * Possible CSS classes indicating the state of selection. Dynamically added/
   * removed based on where the user clicks within the diff.
   */
  var SelectionClass = {
    COMMENT: 'selected-comment',
    LEFT: 'selected-left',
    RIGHT: 'selected-right',
  };

  var getNewCache = function() { return {left: null, right: null}; };

  Polymer({
    is: 'gr-diff-selection',

    properties: {
      diff: Object,
      _cachedDiffBuilder: Object,
      _linesCache: {
        type: Object,
        value: getNewCache(),
      },
    },

    observers: [
      '_diffChanged(diff)',
    ],

    listeners: {
      'copy': '_handleCopy',
      'down': '_handleDown',
    },

    attached: function() {
      this.classList.add(SelectionClass.RIGHT);
    },

    get diffBuilder() {
      if (!this._cachedDiffBuilder) {
        this._cachedDiffBuilder =
            Polymer.dom(this).querySelector('gr-diff-builder');
      }
      return this._cachedDiffBuilder;
    },

    _diffChanged: function() {
      this._linesCache = getNewCache();
    },

    _handleDown: function(e) {
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      if (!lineEl) {
        return;
      }
      var commentSelected =
          this._elementDescendedFromClass(e.target, 'gr-diff-comment');
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var targetClasses = [];
      targetClasses.push(side === 'left' ?
          SelectionClass.LEFT :
          SelectionClass.RIGHT);

      if (commentSelected) {
        targetClasses.push(SelectionClass.COMMENT);
      }
      // Remove any selection classes that do not belong.
      for (var key in SelectionClass) {
        if (SelectionClass.hasOwnProperty(key)) {
          var className = SelectionClass[key];
          if (targetClasses.indexOf(className) === -1) {
            this.classList.remove(SelectionClass[key]);
          }
        }
      }
      // Add new selection classes iff they are not already present.
      for (var i = 0; i < targetClasses.length; i++) {
        if (!this.classList.contains(targetClasses[i])) {
          this.classList.add(targetClasses[i]);
        }
      }
    },

    _getCopyEventTarget: function(e) {
      return Polymer.dom(e).rootTarget;
    },

    /**
     * Utility function to determine whether an element is a descendant of
     * another element with the particular className.
     *
     * @param {!Element} element
     * @param {!string} className
     * @return {boolean}
     */
    _elementDescendedFromClass: function(element, className) {
      while (!element.classList.contains(className)) {
        if (!element.parentElement ||
            element === this.diffBuilder.diffElement) {
          return false;
        }
        element = element.parentElement;
      }
      return true;
    },

    _handleCopy: function(e) {
      var commentSelected = false;
      var target = this._getCopyEventTarget(e);
      if (target.type === 'textarea') { return; }
      if (!this._elementDescendedFromClass(target, 'diff-row')) { return; }
      if (this.classList.contains(SelectionClass.COMMENT)) {
        commentSelected = true;
      }
      var lineEl = this.diffBuilder.getLineElByChild(target);
      if (!lineEl) {
        return;
      }
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var text = this._getSelectedText(side, commentSelected);
      if (text) {
        e.clipboardData.setData('Text', text);
        e.preventDefault();
      }
    },

    /**
     * Get the text of the current window selection. If commentSelected is
     * true, it returns only the text of comments within the selection.
     * Otherwise it returns the text of the selected diff region.
     *
     * @param {!string} The side that is selected.
     * @param {boolean} Whether or not a comment is selected.
     * @return {string} The selected text.
     */
    _getSelectedText: function(side, commentSelected) {
      var sel = window.getSelection();
      if (sel.rangeCount != 1) {
        return; // No multi-select support yet.
      }
      if (commentSelected) {
        return this._getCommentLines(sel, side);
      }
      var range = GrRangeNormalizer.normalize(sel.getRangeAt(0));
      var startLineEl = this.diffBuilder.getLineElByChild(range.startContainer);
      var endLineEl = this.diffBuilder.getLineElByChild(range.endContainer);
      var startLineNum = parseInt(startLineEl.getAttribute('data-value'), 10);
      var endLineNum = parseInt(endLineEl.getAttribute('data-value'), 10);

      return this._getRangeFromDiff(startLineNum, range.startOffset, endLineNum,
          range.endOffset, side);
    },

    /**
     * Query the diff object for the selected lines.
     *
     * @param {int} startLineNum
     * @param {int} startOffset
     * @param {int} endLineNum
     * @param {int} endOffset
     * @param {!string} side The side that is currently selected.
     * @return {string} The selected diff text.
     */
    _getRangeFromDiff: function(startLineNum, startOffset, endLineNum,
        endOffset, side) {
      var lines = this._getDiffLines(side).slice(startLineNum - 1, endLineNum);
      if (lines.length) {
        lines[lines.length - 1] = lines[lines.length - 1]
            .substring(0, endOffset);
        lines[0] = lines[0].substring(startOffset);
      }
      return lines.join('\n');
    },

    /**
     * Query the diff object for the lines from a particular side.
     *
     * @param {!string} side The side that is currently selected.
     * @return {string[]} An array of strings indexed by line number.
     */
    _getDiffLines: function(side) {
      if (this._linesCache[side]) {
        return this._linesCache[side];
      }
      var lines = [];
      var chunk;
      var key = side === 'left' ? 'a' : 'b';
      for (var chunkIndex = 0;
          chunkIndex < this.diff.content.length;
          chunkIndex++) {
        chunk = this.diff.content[chunkIndex];
        if (chunk.ab) {
          lines = lines.concat(chunk.ab);
        } else if (chunk[key]) {
          lines = lines.concat(chunk[key]);
        }
      }
      this._linesCache[side] = lines;
      return lines;
    },

    /**
     * Query the diffElement for comments and check whether they lie inside the
     * selection range.
     *
     * @param {!Selection} sel The selection of the window.
     * @param {!string} side The side that is currently selected.
     * @return {string} The selected comment text.
     */
    _getCommentLines: function(sel, side) {
      var range = GrRangeNormalizer.normalize(sel.getRangeAt(0));
      var content = [];
      // Query the diffElement for comments.
      var messages = this.diffBuilder.diffElement.querySelectorAll(
          '.side-by-side [data-side="' + side +
          '"] .message *, .unified .message *');

      for (var i = 0; i < messages.length; i++) {
        var el = messages[i];
        // Check if the comment element exists inside the selection.
        if (sel.containsNode(el, true)) {
          // Padded elements require newlines for accurate spacing.
          if (el.parentElement.id === 'container' ||
              el.parentElement.nodeName === 'BLOCKQUOTE') {
            if (content.length && content[content.length - 1] !== '') {
              content.push('');
            }
          }

          if (el.id === 'output' &&
              !this._elementDescendedFromClass(el, 'collapsed')) {
            content.push(this._getTextContentForRange(el, sel, range));
          }
        }
      }

      return content.join('\n');
    },

    /**
     * Given a DOM node, a selection, and a selection range, recursively get all
     * of the text content within that selection.
     * Using a domNode that isn't in the selection returns an empty string.
     *
     * @param {Element} domNode The root DOM node.
     * @param {Selection} sel The selection.
     * @param {Range} range The normalized selection range.
     * @return {string} The text within the selection.
     */
    _getTextContentForRange: function(domNode, sel, range) {
      if (!sel.containsNode(domNode, true)) { return ''; }

      var text = '';
      if (domNode instanceof Text) {
        text = domNode.textContent;
        if (domNode === range.endContainer) {
          text = text.substring(0, range.endOffset);
        }
        if (domNode === range.startContainer) {
          text = text.substring(range.startOffset);
        }
      } else {
        for (var i = 0; i < domNode.childNodes.length; i++) {
          text += this._getTextContentForRange(domNode.childNodes[i],
              sel, range);
        }
      }
      return text;
    },
  });
})();
