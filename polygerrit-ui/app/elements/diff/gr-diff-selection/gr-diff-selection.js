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

  Polymer({
    is: 'gr-diff-selection',

    properties: {
      diff: Object,
      _cachedDiffBuilder: Object,
      _linesCache: {
        type: Object,
        value: function() { return {left: null, right: null}; },
      },
    },

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

    _handleDown: function(e) {
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      if (!lineEl) {
        return;
      }
      var commentSelected =
          e.target.parentNode.classList.contains('gr-diff-comment');
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var targetClass = side === 'left' ?
          SelectionClass.LEFT :
          SelectionClass.RIGHT;

      for (var key in SelectionClass) {
        if (SelectionClass.hasOwnProperty(key)) {
          this.classList.remove(SelectionClass[key]);
        }
      }
      this.classList.add(targetClass);
      if (commentSelected) {
        this.classList.add(SelectionClass.COMMENT);
      }
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
        if (!element.parentElement) {
          return false;
        }
        element = element.parentElement;
      }
      return true;
    },

    _handleCopy: function(e) {
      var commentSelected = false;
      if (this._elementDescendedFromClass(e.target, SelectionClass.COMMENT)) {
        commentSelected = true;
      } else {
        if (!this._elementDescendedFromClass(e.target, 'content')) {
          return;
        }
      }
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
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
     * This function has two different codepaths:
     * - If a comment is selected, query the diffElement for comments and check
     *   whether they lie inside the selection range.
     * - If diff lines are being copied, query the diff object.
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

    _getRangeFromDiff: function(startLineNum, startOffset, endLineNum,
        endOffset, side) {
      var lines = this._getDiffLines(side).slice(startLineNum - 1, endLineNum);
      if (lines.length) {
        lines[0] = lines[0].substring(startOffset);
        lines[lines.length - 1] = lines[lines.length - 1]
            .substring(0, endOffset);
      }
      return lines.join('\n');
    },

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

    _getCommentLines: function(sel, side) {
      var range = sel.getRangeAt(0);
      var content = [];
      // Fall back to default copy behavior if the selection lies within one
      // comment body.
      if (this._elementDescendedFromClass(range.commonAncestorContainer,
          'message')) {
        return;
      }
      // Query the diffElement for comments.
      var messages = this.diffBuilder.diffElement.querySelectorAll(
          '.side-by-side [data-side="' + side +
          '"] .message *, .unified .message *');

      for (var i = 0; i < messages.length; i++) {
        var el = messages[i];
        // Check if the comment element exists inside the selection.
        if (sel.containsNode(el, true)) {
          content.push(el.textContent);
        }
      }
      // Deal with offsets.
      content[0] = content[0].substring(range.startOffset);
      if (range.endOffset) {
        content[content.length - 1] =
            content[content.length - 1].substring(0, range.endOffset);
      }
      return content.join('\n');
    },
  });
})();
