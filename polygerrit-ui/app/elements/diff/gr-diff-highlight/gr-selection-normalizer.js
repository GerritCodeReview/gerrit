// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the 'License');
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an 'AS IS' BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.GrSelectionNormalizer) { return; }

  const GrSelectionNormalizer = function(root, diffBuilder) {
    this._root = root;
    this._diffBuilder = diffBuilder;
  };

  /**
   * Get current normalized selection.
   * Merges multiple ranges, accounts for triple click, accounts for
   * syntax highligh, convert native DOM Range objects to Gerrit concepts
   * (line, side, etc).
   * @return {{
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
   * }}
   */
  GrSelectionNormalizer.prototype.getNormalizedRange = function() {
    const selection = window.getSelection();
    const rangeCount = selection.rangeCount;
    if (rangeCount === 0) {
      return null;
    } else if (rangeCount === 1) {
      return this._normalize(selection.getRangeAt(0));
    } else {
      const startRange = this._normalize(selection.getRangeAt(0));
      const endRange = this._normalize(
          selection.getRangeAt(rangeCount - 1));
      return {
        start: startRange.start,
        end: endRange.end,
      };
    }
  };

  /**
   * Adjust triple click selection for the whole line.
   * domRange.endContainer may be one of the following:
   * 1) 0 offset at right column's line number cell, or
   * 2) 0 offset at left column's line number at the next line.
   * Case 1 means left column was triple clicked.
   * Case 2 means right column or unified view triple clicked.
   * @param {!Object} range Normalized range, ie column/line numbers
   * @param {!Range} domRange DOM Range object
   * @return {!Object} fixed normalized range
   */
  GrSelectionNormalizer.prototype._fixTripleClickSelection = function(
      range, domRange) {
    if (!range.start) {
      // Selection outside of current diff.
      return range;
    }
    const start = range.start;
    const end = range.end;
    const endsAtOtherSideLineNum =
          domRange.endOffset === 0 &&
          domRange.endContainer.nodeName === 'TD' &&
          (domRange.endContainer.classList.contains('left') ||
           domRange.endContainer.classList.contains('right'));
    const endsOnOtherSideStart = endsAtOtherSideLineNum ||
          end &&
          end.column === 0 &&
          end.line === start.line &&
          end.side != start.side;
    if (endsOnOtherSideStart || endsAtOtherSideLineNum) {
      // Selection ends at the beginning of the next line.
      // Move the selection to the end of the previous line.
      range.end = {
        node: start.node,
        column: this._getLength(
            domRange.cloneContents().querySelector('.contentText')),
        side: start.side,
        line: start.line,
      };
    }
    return range;
  };

  /**
   * Convert DOM Range selection to concrete numbers (line, column, side).
   * Moves range end if it's not inside td.content.
   * Returns null if selection end is not valid (outside of diff).
   *
   * @param {Node} node td.content child
   * @param {number} offset offset within node
   * @return {{
   *   node: Node,
   *   side: string,
   *   line: Number,
   *   column: Number
   * }}
   */
  GrSelectionNormalizer.prototype._normalizeSelectionSide = function(
      node, offset) {
    let column;
    if (!this._root.contains(node)) {
      return;
    }
    const lineEl = this._diffBuilder.getLineElByChild(node);
    if (!lineEl) {
      return;
    }
    const side = this._diffBuilder.getSideByLineEl(lineEl);
    if (!side) {
      return;
    }
    const line = this._diffBuilder.getLineNumberByChild(lineEl);
    if (!line) {
      return;
    }
    const contentText = this._diffBuilder.getContentByLineEl(lineEl);
    if (!contentText) {
      return;
    }
    const contentTd = contentText.parentElement;
    if (!contentTd.contains(node)) {
      node = contentText;
      column = 0;
    } else {
      const thread = contentTd.querySelector('gr-diff-comment-thread');
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
  };

  /**
   * Remap DOM range to whole lines of a diff if necessary. If the start or
   * end containers are DOM elements that are singular pieces of syntax
   * highlighting, the containers are remapped to the .contentText divs that
   * contain the entire line of code.
   *
   * @param {Object} range - the standard DOM selector range.
   * @return {Object} A modified version of the range that correctly accounts
   *     for syntax highlighting.
   */
  GrSelectionNormalizer.prototype.normalize = function(range) {
    const startContainer = this._getContentTextParent(range.startContainer);
    const startOffset = range.startOffset +
          this._getTextOffset(startContainer, range.startContainer);
    const endContainer = this._getContentTextParent(range.endContainer);
    const endOffset = range.endOffset + this._getTextOffset(endContainer,
        range.endContainer);
    return this._fixTripleClickSelection({
      start: this._normalizeSelectionSide(startContainer, startOffset),
      end: this._normalizeSelectionSide(endContainer, endOffset),
    }, range);
  };

  GrSelectionNormalizer.prototype._getContentTextParent = function(target) {
    let element = target;
    if (element.nodeName === '#text') {
      element = element.parentElement;
    }
    while (!element.classList.contains('contentText')) {
      if (element.parentElement === null) {
        return target;
      }
      element = element.parentElement;
    }
    return element;
  };

  /**
   * Gets the character offset of the child within the parent.
   * Performs a synchronous in-order traversal from top to bottom of the node
   * element, counting the length of the syntax until child is found.
   *
   * @param {!Element} node The root DOM element to be searched through.
   * @param {!Element} child The child element being searched for.
   * @return {number}
   */
  GrSelectionNormalizer.prototype._getTextOffset = function(node, child) {
    let count = 0;
    let stack = [node];
    while (stack.length) {
      const n = stack.pop();
      if (n === child) {
        break;
      }
      if (n.childNodes && n.childNodes.length !== 0) {
        const arr = [];
        for (const childNode of n.childNodes) {
          arr.push(childNode);
        }
        arr.reverse();
        stack = stack.concat(arr);
      } else {
        count += this._getLength(n);
      }
    }
    return count;
  };

  GrSelectionNormalizer.prototype._convertOffsetToColumn = function(
      el, offset) {
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
  };

  /**
   * Get length of a node. If the node is a content node, then only give the
   * length of its .contentText child.
   *
   * @param {!Node} node
   * @return {number}
   */
  GrSelectionNormalizer.prototype._getLength = function(node) {
    if (node instanceof Element && node.classList.contains('content')) {
      return this._getLength(node.querySelector('.contentText'));
    } else {
      return GrAnnotation.getLength(node);
    }
  };

  /**
   * Traverse Element from right to left, call callback for each node.
   * Stops if callback returns true.
   *
   * @param {!Node} startNode
   * @param {function(Node):boolean} callback
   * @param {Object=} opt_flags If flags.left is true, traverse left.
   */
  GrSelectionNormalizer.prototype._traverseContentSiblings = function(
      startNode, callback, opt_flags) {
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
  };

  window.GrSelectionNormalizer = GrSelectionNormalizer;
})(window);
