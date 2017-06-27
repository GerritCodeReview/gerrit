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
      _selectionNormalizer: Object,
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

    get selectionNormalizer() {
      if (!this._selectionNormalizer) {
        this._selectionNormalizer = new GrSelectionNormalizer(
          this, this.diffBuilder);
      }
      return this._selectionNormalizer;
    },

    _enableSelectionObserver(loggedIn, isAttached) {
      if (loggedIn && isAttached) {
        this.listen(document, 'selectionchange', '_handleSelectionChange');
      } else {
        this.unlisten(document, 'selectionchange', '_handleSelectionChange');
      }
    },

    isRangeSelected() {
      return !!this.$$('gr-selection-action-box');
    },

    _handleSelectionChange() {
      // Can't use up or down events to handle selection started and/or ended in
      // in comment threads or outside of diff.
      // Debounce removeActionBox to give it a chance to react to click/tap.
      this._removeActionBoxDebounced();
      this.debounce('selectionChange', this._handleSelection, 200);
    },

    _handleCommentMouseOver(e) {
      const comment = e.detail.comment;
      if (!comment.range) { return; }
      const lineEl = this.diffBuilder.getLineElByChild(e.target);
      const side = this.diffBuilder.getSideByLineEl(lineEl);
      const index = this._indexOfComment(side, comment);
      if (index !== undefined) {
        this.set(['comments', side, index, '__hovering'], true);
      }
    },

    _handleCommentMouseOut(e) {
      const comment = e.detail.comment;
      if (!comment.range) { return; }
      const lineEl = this.diffBuilder.getLineElByChild(e.target);
      const side = this.diffBuilder.getSideByLineEl(lineEl);
      const index = this._indexOfComment(side, comment);
      if (index !== undefined) {
        this.set(['comments', side, index, '__hovering'], false);
      }
    },

    _indexOfComment(side, comment) {
      const idProp = comment.id ? 'id' : '__draftID';
      for (let i = 0; i < this.comments[side].length; i++) {
        if (comment[idProp] &&
            this.comments[side][i][idProp] === comment[idProp]) {
          return i;
        }
      }
    },

    _handleSelection() {
      const normalizedRange = this.selectionNormalizer.getNormalizedRange();
      if (!normalizedRange) {
        return;
      }
      const domRange = window.getSelection().getRangeAt(0);
      const start = normalizedRange.start;
      if (!start) {
        return;
      }
      const end = normalizedRange.end;
      if (!end) {
        return;
      }
      if (start.side !== end.side ||
          end.line < start.line ||
          (start.line === end.line && start.column === end.column)) {
        return;
      }

      // TODO (viktard): Drop empty first and last lines from selection.

      const actionBox = document.createElement('gr-selection-action-box');
      Polymer.dom(this.root).appendChild(actionBox);
      actionBox.range = {
        startLine: start.line,
        startChar: start.column,
        endLine: end.line,
        endChar: end.column,
      };
      actionBox.side = start.side;
      if (start.line === end.line) {
        actionBox.placeAbove(domRange);
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

    _createComment(e) {
      this._removeActionBox();
    },

    _removeActionBoxDebounced() {
      this.debounce('removeActionBox', this._removeActionBox, 10);
    },

    _removeActionBox() {
      const actionBox = this.$$('gr-selection-action-box');
      if (actionBox) {
        Polymer.dom(this.root).removeChild(actionBox);
      }
    },
  });
})();
