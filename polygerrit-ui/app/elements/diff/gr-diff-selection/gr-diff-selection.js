/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  /**
   * Possible CSS classes indicating the state of selection. Dynamically added/
   * removed based on where the user clicks within the diff.
   */
  const SelectionClass = {
    COMMENT: 'selected-comment',
    LEFT: 'selected-left',
    RIGHT: 'selected-right',
    BLAME: 'selected-blame',
  };

  const getNewCache = () => { return {left: null, right: null}; };

  Polymer({
    is: 'gr-diff-selection',

    properties: {
      diff: Object,
      /** @type {?Object} */
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
      copy: '_handleCopy',
      down: '_handleDown',
    },

    behaviors: [
      Gerrit.DomUtilBehavior,
    ],

    attached() {
      this.classList.add(SelectionClass.RIGHT);
    },

    get diffBuilder() {
      if (!this._cachedDiffBuilder) {
        this._cachedDiffBuilder =
            Polymer.dom(this).querySelector('gr-diff-builder');
      }
      return this._cachedDiffBuilder;
    },

    _diffChanged() {
      this._linesCache = getNewCache();
    },

    _handleDownOnRangeComment(node) {
      // Keep the original behavior in polymer 1
      if (!window.POLYMER2) return false;
      if (node &&
          node.nodeName &&
          node.nodeName.toLowerCase() === 'gr-comment-thread') {
        this._setClasses([
          SelectionClass.COMMENT,
          node.commentSide === 'left' ?
          SelectionClass.LEFT :
          SelectionClass.RIGHT,
        ]);
        return true;
      }
      return false;
    },

    _handleDown(e) {
      // Handle the down event on comment thread in Polymer 2
      const handled = this._handleDownOnRangeComment(e.target);
      if (handled) return;

      const lineEl = this.diffBuilder.getLineElByChild(e.target);
      const blameSelected = this._elementDescendedFromClass(e.target, 'blame');
      if (!lineEl && !blameSelected) { return; }

      const targetClasses = [];

      if (blameSelected) {
        targetClasses.push(SelectionClass.BLAME);
      } else {
        const commentSelected =
            this._elementDescendedFromClass(e.target, 'gr-comment');
        const side = this.diffBuilder.getSideByLineEl(lineEl);

        targetClasses.push(side === 'left' ?
            SelectionClass.LEFT :
            SelectionClass.RIGHT);

        if (commentSelected) {
          targetClasses.push(SelectionClass.COMMENT);
        }
      }

      this._setClasses(targetClasses);
    },

    /**
     * Set the provided list of classes on the element, to the exclusion of all
     * other SelectionClass values.
     * @param {!Array<!string>} targetClasses
     */
    _setClasses(targetClasses) {
      // Remove any selection classes that do not belong.
      for (const key in SelectionClass) {
        if (SelectionClass.hasOwnProperty(key)) {
          const className = SelectionClass[key];
          if (!targetClasses.includes(className)) {
            this.classList.remove(SelectionClass[key]);
          }
        }
      }
      // Add new selection classes iff they are not already present.
      for (const _class of targetClasses) {
        if (!this.classList.contains(_class)) {
          this.classList.add(_class);
        }
      }
    },

    _getCopyEventTarget(e) {
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
    _elementDescendedFromClass(element, className) {
      return this.descendedFromClass(element, className,
          this.diffBuilder.diffElement);
    },

    _handleCopy(e) {
      let commentSelected = false;
      const target = this._getCopyEventTarget(e);
      if (target.type === 'textarea') { return; }
      if (!this._elementDescendedFromClass(target, 'diff-row')) { return; }
      if (this.classList.contains(SelectionClass.COMMENT)) {
        commentSelected = true;
      }
      const lineEl = this.diffBuilder.getLineElByChild(target);
      if (!lineEl) {
        return;
      }
      const side = this.diffBuilder.getSideByLineEl(lineEl);
      const text = this._getSelectedText(side, commentSelected);
      if (text) {
        e.clipboardData.setData('Text', text);
        e.preventDefault();
      }
    },

    /**
     * For Polymer 2, use shadowRoot.getSelection instead.
     */
    _getSelection() {
      let selection;
      if (window.POLYMER2) {
        const diffHost = util.querySelector(document.body, 'gr-diff');
        selection = diffHost &&
          diffHost.shadowRoot &&
          diffHost.shadowRoot.getSelection();
      }
      return selection ? selection: window.getSelection();
    },

    /**
     * Get the text of the current selection. If commentSelected is
     * true, it returns only the text of comments within the selection.
     * Otherwise it returns the text of the selected diff region.
     *
     * @param {!string} side The side that is selected.
     * @param {boolean} commentSelected Whether or not a comment is selected.
     * @return {string} The selected text.
     */
    _getSelectedText(side, commentSelected) {
      const sel = this._getSelection();
      if (sel.rangeCount != 1) {
        return ''; // No multi-select support yet.
      }
      if (commentSelected) {
        return this._getCommentLines(sel, side);
      }
      const range = GrRangeNormalizer.normalize(sel.getRangeAt(0));
      const startLineEl =
          this.diffBuilder.getLineElByChild(range.startContainer);
      const endLineEl = this.diffBuilder.getLineElByChild(range.endContainer);
      // Happens when triple click in side-by-side mode with other side empty.
      const endsAtOtherEmptySide = !endLineEl &&
          range.endOffset === 0 &&
          range.endContainer.nodeName === 'TD' &&
          (range.endContainer.classList.contains('left') ||
           range.endContainer.classList.contains('right'));
      const startLineNum = parseInt(startLineEl.getAttribute('data-value'), 10);
      let endLineNum;
      if (endsAtOtherEmptySide) {
        endLineNum = startLineNum + 1;
      } else if (endLineEl) {
        endLineNum = parseInt(endLineEl.getAttribute('data-value'), 10);
      }

      return this._getRangeFromDiff(startLineNum, range.startOffset, endLineNum,
          range.endOffset, side);
    },

    /**
     * Query the diff object for the selected lines.
     *
     * @param {number} startLineNum
     * @param {number} startOffset
     * @param {number|undefined} endLineNum Use undefined to get the range
     *     extending to the end of the file.
     * @param {number} endOffset
     * @param {!string} side The side that is currently selected.
     * @return {string} The selected diff text.
     */
    _getRangeFromDiff(startLineNum, startOffset, endLineNum, endOffset, side) {
      const lines =
          this._getDiffLines(side).slice(startLineNum - 1, endLineNum);
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
     * @return {!Array<string>} An array of strings indexed by line number.
     */
    _getDiffLines(side) {
      if (this._linesCache[side]) {
        return this._linesCache[side];
      }
      let lines = [];
      const key = side === 'left' ? 'a' : 'b';
      for (const chunk of this.diff.content) {
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
    _getCommentLines(sel, side) {
      const range = GrRangeNormalizer.normalize(sel.getRangeAt(0));
      const content = [];
      // Query the diffElement for comments.
      const messages = this.diffBuilder.diffElement.querySelectorAll(
          `.side-by-side [data-side="${side
          }"] .message *, .unified .message *`);

      for (let i = 0; i < messages.length; i++) {
        const el = messages[i];
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
     * @param {!Node} domNode The root DOM node.
     * @param {!Selection} sel The selection.
     * @param {!Range} range The normalized selection range.
     * @return {string} The text within the selection.
     */
    _getTextContentForRange(domNode, sel, range) {
      if (!sel.containsNode(domNode, true)) { return ''; }

      let text = '';
      if (domNode instanceof Text) {
        text = domNode.textContent;
        if (domNode === range.endContainer) {
          text = text.substring(0, range.endOffset);
        }
        if (domNode === range.startContainer) {
          text = text.substring(range.startOffset);
        }
      } else {
        for (const childNode of domNode.childNodes) {
          text += this._getTextContentForRange(childNode, sel, range);
        }
      }
      return text;
    },
  });
})();
