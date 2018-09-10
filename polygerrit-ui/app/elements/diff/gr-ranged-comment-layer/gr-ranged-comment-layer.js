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

import '../../core/gr-reporting/gr-reporting.js';
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

const HOVER_PATH_PATTERN = /^comments\.(left|right)\.\#(\d+)\.__hovering$/;
const SPLICE_PATH_PATTERN = /^comments\.(left|right)\.splices$/;

const RANGE_HIGHLIGHT = 'range';
const HOVER_HIGHLIGHT = 'rangeHighlight';

const NORMALIZE_RANGE_EVENT = 'normalize-range';

Polymer({
  _template: Polymer.html`
    <gr-reporting id="reporting" category="comments"></gr-reporting>
`,

  is: 'gr-ranged-comment-layer',

  properties: {
    comments: Object,
    _listeners: {
      type: Array,
      value() { return []; },
    },
    _commentMap: {
      type: Object,
      value() { return {left: [], right: []}; },
    },
  },

  observers: [
    '_handleCommentChange(comments.*)',
  ],

  /**
   * Layer method to add annotations to a line.
   * @param {!HTMLElement} el The DIV.contentText element to apply the
   *     annotation to.
   * @param {!Object} line The line object. (GrDiffLine)
   */
  annotate(el, line) {
    let ranges = [];
    if (line.type === GrDiffLine.Type.REMOVE || (
        line.type === GrDiffLine.Type.BOTH &&
        el.getAttribute('data-side') !== 'right')) {
      ranges = ranges.concat(this._getRangesForLine(line, 'left'));
    }
    if (line.type === GrDiffLine.Type.ADD || (
        line.type === GrDiffLine.Type.BOTH &&
        el.getAttribute('data-side') !== 'left')) {
      ranges = ranges.concat(this._getRangesForLine(line, 'right'));
    }

    for (const range of ranges) {
      GrAnnotation.annotateElement(el, range.start,
          range.end - range.start,
          range.hovering ? HOVER_HIGHLIGHT : RANGE_HIGHLIGHT);
    }
  },

  /**
   * Register a listener for layer updates.
   * @param {Function<Number, Number, String>} fn The update handler function.
   *     Should accept as arguments the line numbers for the start and end of
   *     the update and the side as a string.
   */
  addListener(fn) {
    this._listeners.push(fn);
  },

  /**
   * Notify Layer listeners of changes to annotations.
   * @param {number} start The line where the update starts.
   * @param {number} end The line where the update ends.
   * @param {string} side The side of the update. ('left' or 'right')
   */
  _notifyUpdateRange(start, end, side) {
    for (const listener of this._listeners) {
      listener(start, end, side);
    }
  },

  /**
   * Handle change in the comments by updating the comment maps and by
   * emitting appropriate update notifications.
   * @param {Object} record The change record.
   */
  _handleCommentChange(record) {
    if (!record.path) { return; }

    // If the entire set of comments was changed.
    if (record.path === 'comments') {
      this._commentMap.left = this._computeCommentMap(this.comments.left);
      this._commentMap.right = this._computeCommentMap(this.comments.right);
      return;
    }

    // If the change only changed the `hovering` property of a comment.
    let match = record.path.match(HOVER_PATH_PATTERN);
    let side;

    if (match) {
      side = match[1];
      const index = match[2];
      const comment = this.comments[side][index];
      if (comment && comment.range) {
        this._commentMap[side] = this._computeCommentMap(this.comments[side]);
        this._notifyUpdateRange(
            comment.range.start_line, comment.range.end_line, side);
      }
      return;
    }

    // If comments were spliced in or out.
    match = record.path.match(SPLICE_PATH_PATTERN);
    if (match) {
      side = match[1];
      this._commentMap[side] = this._computeCommentMap(this.comments[side]);
      this._handleCommentSplice(record.value, side);
    }
  },

  /**
   * Take a list of comments and return a sparse list mapping line numbers to
   * partial ranges. Uses an end-character-index of -1 to indicate the end of
   * the line.
   * @param {?} commentList The list of comments.
   *    Getting this param to match closure requirements caused problems.
   * @return {!Object} The sparse list.
   */
  _computeCommentMap(commentList) {
    const result = {};
    for (const comment of commentList) {
      if (!comment.range) { continue; }
      const range = comment.range;
      for (let line = range.start_line; line <= range.end_line; line++) {
        if (!result[line]) { result[line] = []; }
        result[line].push({
          comment,
          start: line === range.start_line ? range.start_character : 0,
          end: line === range.end_line ? range.end_character : -1,
        });
      }
    }
    return result;
  },

  /**
   * Translate a splice record into range update notifications.
   */
  _handleCommentSplice(record, side) {
    if (!record || !record.indexSplices) { return; }

    for (const splice of record.indexSplices) {
      const ranges = splice.removed.length ?
          splice.removed.map(c => { return c.range; }) :
          [splice.object[splice.index].range];
      for (const range of ranges) {
        if (!range) { continue; }
        this._notifyUpdateRange(range.start_line, range.end_line, side);
      }
    }
  },

  _getRangesForLine(line, side) {
    const lineNum = side === 'left' ? line.beforeNumber : line.afterNumber;
    const ranges = this.get(['_commentMap', side, lineNum]) || [];
    return ranges
        .map(range => {
          range = {
            start: range.start,
            end: range.end === -1 ? line.text.length : range.end,
            hovering: !!range.comment.__hovering,
          };

          // Normalize invalid ranges where the start is after the end but the
          // start still makes sense. Set the end to the end of the line.
          // @see Issue 5744
          if (range.start >= range.end && range.start < line.text.length) {
            range.end = line.text.length;
            this.$.reporting.reportInteraction(NORMALIZE_RANGE_EVENT,
                'Modified invalid comment range on l.' + lineNum +
                ' of the ' + side + ' side');
          }

          return range;
        })
        // Sort the ranges so that hovering highlights are on top.
        .sort((a, b) => a.hovering && !b.hovering ? 1 : 0);
  }
});
