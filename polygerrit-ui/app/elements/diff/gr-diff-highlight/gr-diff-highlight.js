/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../gr-selection-action-box/gr-selection-action-box.js';
import '../../../styles/shared-styles.js';
/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.GrAnnotation) { return; }

  // TODO(wyatta): refactor this to be <MARK> rather than <HL>.
  const ANNOTATION_TAG = 'HL';

  // Astral code point as per https://mathiasbynens.be/notes/javascript-unicode
  const REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

  const GrAnnotation = {

    /**
     * The DOM API textContent.length calculation is broken when the text
     * contains Unicode. See https://mathiasbynens.be/notes/javascript-unicode .
     * @param  {!Text} node text node.
     * @return {number} The length of the text.
     */
    getLength(node) {
      return this.getStringLength(node.textContent);
    },

    getStringLength(str) {
      return str.replace(REGEX_ASTRAL_SYMBOL, '_').length;
    },

    /**
     * Surrounds the element's text at specified range in an ANNOTATION_TAG
     * element. If the element has child elements, the range is split and
     * applied as deeply as possible.
     */
    annotateElement(parent, offset, length, cssClass) {
      const nodes = [].slice.apply(parent.childNodes);
      let nodeLength;
      let subLength;

      for (const node of nodes) {
        nodeLength = this.getLength(node);

        // If the current node is completely before the offset.
        if (nodeLength <= offset) {
          offset -= nodeLength;
          continue;
        }

        // Sublength is the annotation length for the current node.
        subLength = Math.min(length, nodeLength - offset);

        if (node instanceof Text) {
          this._annotateText(node, offset, subLength, cssClass);
        } else if (node instanceof HTMLElement) {
          this.annotateElement(node, offset, subLength, cssClass);
        }

        // If there is still more to annotate, then shift the indices, otherwise
        // work is done, so break the loop.
        if (subLength < length) {
          length -= subLength;
          offset = 0;
        } else {
          break;
        }
      }
    },

    /**
     * Wraps node in annotation tag with cssClass, replacing the node in DOM.
     *
     * @return {!Element} Wrapped node.
     */
    wrapInHighlight(node, cssClass) {
      let hl;
      if (node.tagName === ANNOTATION_TAG) {
        hl = node;
        hl.classList.add(cssClass);
      } else {
        hl = document.createElement(ANNOTATION_TAG);
        hl.className = cssClass;
        Polymer.dom(node.parentElement).replaceChild(hl, node);
        Polymer.dom(hl).appendChild(node);
      }
      return hl;
    },

    /**
     * Splits Text Node and wraps it in hl with cssClass.
     * Wraps trailing part after split, tailing one if opt_firstPart is true.
     *
     * @param {!Node} node
     * @param {number} offset
     * @param {string} cssClass
     * @param {boolean=} opt_firstPart
     */
    splitAndWrapInHighlight(node, offset, cssClass, opt_firstPart) {
      if (this.getLength(node) === offset || offset === 0) {
        return this.wrapInHighlight(node, cssClass);
      } else {
        if (opt_firstPart) {
          this.splitNode(node, offset);
          // Node points to first part of the Text, second one is sibling.
        } else {
          node = this.splitNode(node, offset);
        }
        return this.wrapInHighlight(node, cssClass);
      }
    },

    /**
     * Splits Node at offset.
     * If Node is Element, it's cloned and the node at offset is split too.
     *
     * @param {!Node} node
     * @param {number} offset
     * @return {!Node} Trailing Node.
     */
    splitNode(element, offset) {
      if (element instanceof Text) {
        return this.splitTextNode(element, offset);
      }
      const tail = element.cloneNode(false);
      element.parentElement.insertBefore(tail, element.nextSibling);
      // Skip nodes before offset.
      let node = element.firstChild;
      while (node &&
          this.getLength(node) <= offset ||
          this.getLength(node) === 0) {
        offset -= this.getLength(node);
        node = node.nextSibling;
      }
      if (this.getLength(node) > offset) {
        tail.appendChild(this.splitNode(node, offset));
      }
      while (node.nextSibling) {
        tail.appendChild(node.nextSibling);
      }
      return tail;
    },

    /**
     * Node.prototype.splitText Unicode-valid alternative.
     *
     * DOM Api for splitText() is broken for Unicode:
     * https://mathiasbynens.be/notes/javascript-unicode
     *
     * @param {!Text} node
     * @param {number} offset
     * @return {!Text} Trailing Text Node.
     */
    splitTextNode(node, offset) {
      if (node.textContent.match(REGEX_ASTRAL_SYMBOL)) {
        // TODO (viktard): Polyfill Array.from for IE10.
        const head = Array.from(node.textContent);
        const tail = head.splice(offset);
        const parent = node.parentNode;

        // Split the content of the original node.
        node.textContent = head.join('');

        const tailNode = document.createTextNode(tail.join(''));
        if (parent) {
          parent.insertBefore(tailNode, node.nextSibling);
        }
        return tailNode;
      } else {
        return node.splitText(offset);
      }
    },

    _annotateText(node, offset, length, cssClass) {
      const nodeLength = this.getLength(node);

      // There are four cases:
      //  1) Entire node is highlighted.
      //  2) Highlight is at the start.
      //  3) Highlight is at the end.
      //  4) Highlight is in the middle.

      if (offset === 0 && nodeLength === length) {
        // Case 1.
        this.wrapInHighlight(node, cssClass);
      } else if (offset === 0) {
        // Case 2.
        this.splitAndWrapInHighlight(node, length, cssClass, true);
      } else if (offset + length === nodeLength) {
        // Case 3
        this.splitAndWrapInHighlight(node, offset, cssClass, false);
      } else {
        // Case 4
        this.splitAndWrapInHighlight(this.splitTextNode(node, offset), length,
            cssClass, true);
      }
    },
  };

  window.GrAnnotation = GrAnnotation;
})(window);
/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.GrRangeNormalizer) { return; }

  // Astral code point as per https://mathiasbynens.be/notes/javascript-unicode
  const REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

  const GrRangeNormalizer = {
    /**
     * Remap DOM range to whole lines of a diff if necessary. If the start or
     * end containers are DOM elements that are singular pieces of syntax
     * highlighting, the containers are remapped to the .contentText divs that
     * contain the entire line of code.
     *
     * @param {!Object} range - the standard DOM selector range.
     * @return {!Object} A modified version of the range that correctly accounts
     *     for syntax highlighting.
     */
    normalize(range) {
      const startContainer = this._getContentTextParent(range.startContainer);
      const startOffset = range.startOffset +
          this._getTextOffset(startContainer, range.startContainer);
      const endContainer = this._getContentTextParent(range.endContainer);
      const endOffset = range.endOffset + this._getTextOffset(endContainer,
          range.endContainer);
      return {
        startContainer,
        startOffset,
        endContainer,
        endOffset,
      };
    },

    _getContentTextParent(target) {
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
    },

    /**
     * Gets the character offset of the child within the parent.
     * Performs a synchronous in-order traversal from top to bottom of the node
     * element, counting the length of the syntax until child is found.
     *
     * @param {!Element} node The root DOM element to be searched through.
     * @param {!Element} child The child element being searched for.
     * @return {number}
     */
    _getTextOffset(node, child) {
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
    },

    /**
     * The DOM API textContent.length calculation is broken when the text
     * contains Unicode. See https://mathiasbynens.be/notes/javascript-unicode .
     * @param {text} node A text node.
     * @return {number} The length of the text.
     */
    _getLength(node) {
      return node.textContent.replace(REGEX_ASTRAL_SYMBOL, '_').length;
    },
  };

  window.GrRangeNormalizer = GrRangeNormalizer;
})(window);

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        position: relative;
      }
      .contentWrapper ::content .range {
        background-color: var(--diff-highlight-range-color);
        display: inline;
      }
      .contentWrapper ::content .rangeHighlight {
        background-color: var(--diff-highlight-range-hover-color);
        display: inline;
      }
      gr-selection-action-box {
        /**
         * Needs z-index to apear above wrapped content, since it's inseted
         * into DOM before it.
         */
        z-index: 10;
      }
    </style>
    <div class="contentWrapper">
      <slot></slot>
    </div>
`,

  is: 'gr-diff-highlight',

  properties: {
    comments: Object,
    loggedIn: Boolean,
    /**
     * querySelector can return null, so needs to be nullable.
     *
     * @type {?HTMLElement}
     * */
    _cachedDiffBuilder: Object,
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

  /**
   * Get current normalized selection.
   * Merges multiple ranges, accounts for triple click, accounts for
   * syntax highligh, convert native DOM Range objects to Gerrit concepts
   * (line, side, etc).
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
  _getNormalizedRange() {
    const selection = window.getSelection();
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
    const endsAtBeginningOfNextLine = end &&
        start.column === 0 &&
        end.column === 0 &&
        end.line === start.line + 1;
    const content = domRange.cloneContents().querySelector('.contentText');
    const lineLength = content && this._getLength(content) || 0;
    if (lineLength && endsAtBeginningOfNextLine) {
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

  _handleSelection() {
    const normalizedRange = this._getNormalizedRange();
    if (!normalizedRange) {
      return;
    }
    const domRange = window.getSelection().getRangeAt(0);
    /** @type {?} */
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
    const root = Polymer.dom(this.root);
    root.insertBefore(actionBox, root.firstElementChild);
    actionBox.range = {
      startLine: start.line,
      startChar: start.column,
      endLine: end.line,
      endChar: end.column,
    };
    actionBox.side = start.side;
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
  }
});
