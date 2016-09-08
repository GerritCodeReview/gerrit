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
    is: 'gr-diff-selection',

    properties: {
      diff: Object,
      _cachedDiffBuilder: Object,
    },

    listeners: {
      'copy': '_handleCopy',
      'down': '_handleDown',
    },

    attached: function() {
      this.classList.add('selected-right');
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
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var targetClass = 'selected-' + side;
      var alternateClass = 'selected-' + (side === 'left' ? 'right' : 'left');

      if (this.classList.contains(alternateClass)) {
        this.classList.remove(alternateClass);
      }
      if (!this.classList.contains(targetClass)) {
        this.classList.add(targetClass);
      }
    },

    _handleCopy: function(e) {
      if (!e.target.classList.contains('contentText') &&
          !e.target.classList.contains('gr-syntax')) {
        return;
      }
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      if (!lineEl) {
        return;
      }
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var text = this._getSelectedText(side);
      e.clipboardData.setData('Text', text);
      e.preventDefault();
    },

    _getSelectedText: function(side) {
      var sel = window.getSelection();
      if (sel.rangeCount != 1) {
        return; // No multi-select support yet.
      }
      var range = sel.getRangeAt(0);

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

    _linesCache: {left: null, right: null},
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
  });
})();
