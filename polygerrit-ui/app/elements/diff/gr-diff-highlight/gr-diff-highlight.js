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

  Polymer({
    is: 'gr-diff-highlight',

    properties: {
      /** @type {!Array<!Gerrit.HoveredRange>} */
      commentRanges: {
        type: Array,
        notify: true,
      },
      loggedIn: Boolean,
      /**
       * querySelector can return null, so needs to be nullable.
       *
       * @type {?HTMLElement}
       * */
      _cachedDiffBuilder: Object,

      /**
       * Which range is currently selected by the user.
       * Stored in order to add a range-based comment
       * later.
       * undefined if no range is selected.
       *
       * @type {{side: string, range: Gerrit.Range}|undefined}
       */
      selectedRange: {
        type: Object,
        notify: true,
      },
    },

    behaviors: [
      Gerrit.FireBehavior,
    ],

    listeners: {
      'comment-thread-mouseleave': '_handleCommentThreadMouseleave',
      'comment-thread-mouseenter': '_handleCommentThreadMouseenter',
      'create-comment-requested': '_handleRangeCommentRequest',
    },

    get diffBuilder() {
      if (!this._cachedDiffBuilder) {
        this._cachedDiffBuilder =
            Polymer.dom(this).querySelector('gr-diff-builder');
      }
      return this._cachedDiffBuilder;
    },

    /**
     * Determines side/line/range for a DOM selection and shows a tooltip.
     *
     * With native shadow DOM, gr-diff-highlight cannot access a selection that
     * references the DOM elements making up the diff because they are in the
     * shadow DOM the gr-diff element. For this reason, we listen to the
     * selectionchange event and retrieve the selection in gr-diff, and then
     * call this method to process the Selection.
     *
     * @param {Selection} selection A DOM Selection living in the shadow DOM of
     *     the diff element.
     * @param {boolean} isMouseUp If true, this is called due to a mouseup
     *     event, in which case we might want to immediately create a comment,
     *     because isMouseUp === true combined with an existing selection must
     *     mean that this is the end of a double-click.
     */
    handleSelectionChange(selection, isMouseUp) {
      // Debounce is not just nice for waiting until the selection has settled,
      // it is also vital for being able to click on the action box before it is
      // removed.
      // If you wait longer than 50 ms, then you don't properly catch a very
      // quick 'c' press after the selection change. If you wait less than 10
      // ms, then you will have about 50 _handleSelection calls when doing a
      // simple drag for select.
      this.debounce(
          'selectionChange', () => this._handleSelection(selection, isMouseUp),
          10);
    },

    _getThreadEl(e) {
      const path = Polymer.dom(e).path || [];
      for (const pathEl of path) {
        if (pathEl.classList.contains('comment-thread')) return pathEl;
      }
      return null;
    },

    _handleCommentThreadMouseenter(e) {
      const threadEl = this._getThreadEl(e);
      const index = this._indexForThreadEl(threadEl);

      if (index !== undefined) {
        this.set(['commentRanges', index, 'hovering'], true);
      }
    },

    _handleCommentThreadMouseleave(e) {
      const threadEl = this._getThreadEl(e);
      const index = this._indexForThreadEl(threadEl);

      if (index !== undefined) {
        this.set(['commentRanges', index, 'hovering'], false);
      }
    },

    _indexForThreadEl(threadEl) {
      const side = threadEl.getAttribute('comment-side');
      const range = JSON.parse(threadEl.getAttribute('range'));

      if (!range) return undefined;

      return this._indexOfCommentRange(side, range);
    },

    _indexOfCommentRange(side, range) {
      function rangesEqual(a, b) {
        if (!a && !b) {
          return true;
        }
        if (!a || !b) {
          return false;
        }
        return a.start_line === b.start_line &&
            a.start_character === b.start_character &&
            a.end_line === b.end_line &&
            a.end_character === b.end_character;
      }

      return this.commentRanges.findIndex(commentRange =>
        commentRange.side === side && rangesEqual(commentRange.range, range));
    },

    /**
     * Get current normalized selection.
     * Merges multiple ranges, accounts for triple click, accounts for
     * syntax highligh, convert native DOM Range objects to Gerrit concepts
     * (line, side, etc).
     * @param {Selection} selection
     * @return {({
     *   start: {
     *     node: Node,
     *     side: string,
     *     line: Number,
     *     column: Number
     *   },
     *   end: {
     *     node: Node,
     *     side: string,
     *     line: Number,
     *     column: Number
     *   }
     * })|null|!Object}
     */
    _getNormalizedRange(selection) {
      const rangeCount = selection.rangeCount;
      if (rangeCount === 0) {
        return null;
      } else if (rangeCount === 1) {
        return this._normalizeRange(selection.getRangeAt(0));
      } else {
        const startRange = this._normalizeRange(selection.getRangeAt(0));
        const endRange = this._normalizeRange(
            selection.getRangeAt(rangeCount - 1));
        return {
          start: startRange.start,
          end: endRange.end,
        };
      }
    },

    /**
     * Normalize a specific DOM Range.
     * @return {!Object} fixed normalized range
     */
    _normalizeRange(domRange) {
      const range = GrRangeNormalizer.normalize(domRange);
      return this._fixTripleClickSelection({
        start: this._normalizeSelectionSide(
            range.startContainer, range.startOffset),
        end: this._normalizeSelectionSide(
            range.endContainer, range.endOffset),
      }, domRange);
    },

    /**
     * Adjust triple click selection for the whole line.
     * A triple click always results in:
     * - start.column == end.column == 0
     * - end.line == start.line + 1
     *
     * @param {!Object} range Normalized range, ie column/line numbers
     * @param {!Range} domRange DOM Range object
     * @return {!Object} fixed normalized range
     */
    _fixTripleClickSelection(range, domRange) {
      if (!range.start) {
        // Selection outside of current diff.
        return range;
      }
      const start = range.start;
      const end = range.end;
      // Happens when triple click in side-by-side mode with other side empty.
      const endsAtOtherEmptySide = !end &&
          domRange.endOffset === 0 &&
          domRange.endContainer.nodeName === 'TD' &&
          (domRange.endContainer.classList.contains('left') ||
           domRange.endContainer.classList.contains('right'));
      const endsAtBeginningOfNextLine = end &&
          start.column === 0 &&
          end.column === 0 &&
          end.line === start.line + 1;
      const content = domRange.cloneContents().querySelector('.contentText');
      const lineLength = content && this._getLength(content) || 0;
      if (lineLength && (endsAtBeginningOfNextLine || endsAtOtherEmptySide)) {
        // Move the selection to the end of the previous line.
        range.end = {
          node: start.node,
          column: lineLength,
          side: start.side,
          line: start.line,
        };
      }
      return range;
    },

    /**
     * Convert DOM Range selection to concrete numbers (line, column, side).
     * Moves range end if it's not inside td.content.
     * Returns null if selection end is not valid (outside of diff).
     *
     * @param {Node} node td.content child
     * @param {number} offset offset within node
     * @return {({
     *   node: Node,
     *   side: string,
     *   line: Number,
     *   column: Number
     * }|undefined)}
     */
    _normalizeSelectionSide(node, offset) {
      let column;
      if (!this.contains(node)) {
        return;
      }
      const lineEl = this.diffBuilder.getLineElByChild(node);
      if (!lineEl) {
        return;
      }
      const side = this.diffBuilder.getSideByLineEl(lineEl);
      if (!side) {
        return;
      }
      const line = this.diffBuilder.getLineNumberByChild(lineEl);
      if (!line) {
        return;
      }
      const contentText = this.diffBuilder.getContentByLineEl(lineEl);
      if (!contentText) {
        return;
      }
      const contentTd = contentText.parentElement;
      if (!contentTd.contains(node)) {
        node = contentText;
        column = 0;
      } else {
        const thread = contentTd.querySelector('.comment-thread');
        if (thread && thread.contains(node)) {
          column = this._getLength(contentText);
          node = contentText;
        } else {
          column = this._convertOffsetToColumn(node, offset);
        }
      }

      return {
        node,
        side,
        line,
        column,
      };
    },

    /**
     * The only line in which add a comment tooltip is cut off is the first
     * line. Even if there is a collapsed section, The first visible line is
     * in the position where the second line would have been, if not for the
     * collapsed section, so don't need to worry about this case for
     * positioning the tooltip.
     */
    _positionActionBox(actionBox, startLine, range) {
      if (startLine > 1) {
        actionBox.placeAbove(range);
        return;
      }
      actionBox.positionBelow = true;
      actionBox.placeBelow(range);
    },

    _isRangeValid(range) {
      if (!range || !range.start || !range.end) {
        return false;
      }
      const start = range.start;
      const end = range.end;
      if (start.side !== end.side ||
          end.line < start.line ||
          (start.line === end.line && start.column === end.column)) {
        return false;
      }
      return true;
    },

    _handleSelection(selection, isMouseUp) {
      const normalizedRange = this._getNormalizedRange(selection);
      if (!this._isRangeValid(normalizedRange)) {
        this._removeActionBox();
        return;
      }
      const domRange = selection.getRangeAt(0);
      const start = normalizedRange.start;
      const end = normalizedRange.end;

      // TODO (viktard): Drop empty first and last lines from selection.

      // If the selection is from the end of one line to the start of the next
      // line, then this must have been a double-click, or you have started
      // dragging. Showing the action box is bad in the former case and not very
      // useful in the latter, so never do that.
      // If this was a mouse-up event, we create a comment immediately if
      // the selection is from the end of a line to the start of the next line.
      // In a perfect world we would only do this for double-click, but it is
      // extremely rare that a user would drag from the end of one line to the
      // start of the next and release the mouse, so we don't bother.
      // TODO(brohlfs): This does not work, if the double-click is before a new
      // diff chunk (start will be equal to end), and neither before an "expand
      // the diff context" block (end line will match the first line of the new
      // section and thus be greater than start line + 1).
      if (start.line === end.line - 1 && end.column === 0) {
        // Rather than trying to find the line contents (for comparing
        // start.column with the content length), we just check if the selection
        // is empty to see that it's at the end of a line.
        const content = domRange.cloneContents().querySelector('.contentText');
        if (isMouseUp && this._getLength(content) === 0) {
          this._fireCreateRangeComment(start.side, {
            start_line: start.line,
            start_character: 0,
            end_line: start.line,
            end_character: start.column,
          });
        }
        return;
      }

      let actionBox = this.$$('gr-selection-action-box');
      if (!actionBox) {
        actionBox = document.createElement('gr-selection-action-box');
        const root = Polymer.dom(this.root);
        root.insertBefore(actionBox, root.firstElementChild);
      }
      this.selectedRange = {
        range: {
          start_line: start.line,
          start_character: start.column,
          end_line: end.line,
          end_character: end.column,
        },
        side: start.side,
      };
      if (start.line === end.line) {
        this._positionActionBox(actionBox, start.line, domRange);
      } else if (start.node instanceof Text) {
        if (start.column) {
          this._positionActionBox(actionBox, start.line,
              start.node.splitText(start.column));
        }
        start.node.parentElement.normalize(); // Undo splitText from above.
      } else if (start.node.classList.contains('content') &&
          start.node.firstChild) {
        this._positionActionBox(actionBox, start.line, start.node.firstChild);
      } else {
        this._positionActionBox(actionBox, start.line, start.node);
      }
    },

    _fireCreateRangeComment(side, range) {
      this.fire('create-range-comment', {side, range});
      this._removeActionBox();
    },

    _handleRangeCommentRequest(e) {
      e.stopPropagation();
      if (!this.selectedRange) {
        throw Error('Selected Range is needed for new range comment!');
      }
      const {side, range} = this.selectedRange;
      this._fireCreateRangeComment(side, range);
    },

    _removeActionBox() {
      this.selectedRange = undefined;
      const actionBox = this.$$('gr-selection-action-box');
      if (actionBox) {
        Polymer.dom(this.root).removeChild(actionBox);
      }
    },

    _convertOffsetToColumn(el, offset) {
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
     * @param {!Element} startNode
     * @param {function(Node):boolean} callback
     * @param {Object=} opt_flags If flags.left is true, traverse left.
     */
    _traverseContentSiblings(startNode, callback, opt_flags) {
      const travelLeft = opt_flags && opt_flags.left;
      let node = startNode;
      while (node) {
        if (node instanceof Element &&
            node.tagName !== 'HL' &&
            node.tagName !== 'SPAN') {
          break;
        }
        const nextNode = travelLeft ? node.previousSibling : node.nextSibling;
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
     * @param {?Element} node this is sometimes passed as null.
     * @return {number}
     */
    _getLength(node) {
      if (node instanceof Element && node.classList.contains('content')) {
        return this._getLength(node.querySelector('.contentText'));
      } else {
        return GrAnnotation.getLength(node);
      }
    },
  });
})();
