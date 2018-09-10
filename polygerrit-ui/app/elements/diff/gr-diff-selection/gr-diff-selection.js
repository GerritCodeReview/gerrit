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

import '../../../behaviors/dom-util-behavior/dom-util-behavior.js';
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
  _template: Polymer.html`
    <style include="shared-styles">
      .contentWrapper ::content .content,
      .contentWrapper ::content .contextControl,
      .contentWrapper ::content .blame {
        -webkit-user-select: none;
        -moz-user-select: none;
        -ms-user-select: none;
        user-select: none;
      }

      :host-context(.selected-left:not(.selected-comment)) .contentWrapper ::content .side-by-side .left + .content .contentText,
      :host-context(.selected-right:not(.selected-comment)) .contentWrapper ::content .side-by-side .right + .content .contentText,
      :host-context(.selected-left:not(.selected-comment)) .contentWrapper ::content .unified .left.lineNum ~ .content:not(.both) .contentText,
      :host-context(.selected-right:not(.selected-comment)) .contentWrapper ::content .unified .right.lineNum ~ .content .contentText,
      :host-context(.selected-left.selected-comment) .contentWrapper ::content .side-by-side .left + .content .message,
      :host-context(.selected-right.selected-comment) .contentWrapper ::content .side-by-side .right + .content .message :not(.collapsedContent),
      :host-context(.selected-comment) .contentWrapper ::content .unified .message :not(.collapsedContent),
      :host-context(.selected-blame) .contentWrapper ::content .blame {
        -webkit-user-select: text;
        -moz-user-select: text;
        -ms-user-select: text;
        user-select: text;
      }
    </style>
    <div class="contentWrapper">
      <slot></slot>
    </div>
`,

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

  _handleDown(e) {
    const lineEl = this.diffBuilder.getLineElByChild(e.target);
    const blameSelected = this._elementDescendedFromClass(e.target, 'blame');
    if (!lineEl && !blameSelected) { return; }

    const targetClasses = [];

    if (blameSelected) {
      targetClasses.push(SelectionClass.BLAME);
    } else {
      const commentSelected =
          this._elementDescendedFromClass(e.target, 'gr-diff-comment');
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
   * Get the text of the current window selection. If commentSelected is
   * true, it returns only the text of comments within the selection.
   * Otherwise it returns the text of the selected diff region.
   *
   * @param {!string} side The side that is selected.
   * @param {boolean} commentSelected Whether or not a comment is selected.
   * @return {string} The selected text.
   */
  _getSelectedText(side, commentSelected) {
    const sel = window.getSelection();
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
    const startLineNum = parseInt(startLineEl.getAttribute('data-value'), 10);
    const endLineNum = endLineEl === null ?
        undefined :
        parseInt(endLineEl.getAttribute('data-value'), 10);

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
  }
});
