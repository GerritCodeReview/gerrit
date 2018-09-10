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
import '../gr-diff-comment-thread/gr-diff-comment-thread.js';
import '../gr-diff-comment-thread-group/gr-diff-comment-thread-group.js';
import '../gr-diff-processor/gr-diff-processor.js';
import '../gr-ranged-comment-layer/gr-ranged-comment-layer.js';
import '../gr-syntax-layer/gr-syntax-layer.js';
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
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffLine) { return; }

  function GrDiffLine(type) {
    this.type = type;
    this.highlights = [];
  }

  GrDiffLine.prototype.afterNumber = 0;

  GrDiffLine.prototype.beforeNumber = 0;

  GrDiffLine.prototype.contextGroup = null;

  GrDiffLine.prototype.text = '';

  GrDiffLine.Type = {
    ADD: 'add',
    BOTH: 'both',
    BLANK: 'blank',
    CONTEXT_CONTROL: 'contextControl',
    REMOVE: 'remove',
  };

  GrDiffLine.FILE = 'FILE';

  GrDiffLine.BLANK_LINE = new GrDiffLine(GrDiffLine.Type.BLANK);

  window.GrDiffLine = GrDiffLine;
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
(function(window, GrDiffLine) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffGroup) { return; }

  function GrDiffGroup(type, opt_lines) {
    this.type = type;
    this.lines = [];
    this.adds = [];
    this.removes = [];
    this.dueToRebase = undefined;

    this.lineRange = {
      left: {start: null, end: null},
      right: {start: null, end: null},
    };

    if (opt_lines) {
      opt_lines.forEach(this.addLine, this);
    }
  }

  GrDiffGroup.prototype.element = null;

  GrDiffGroup.Type = {
    BOTH: 'both',
    CONTEXT_CONTROL: 'contextControl',
    DELTA: 'delta',
  };

  GrDiffGroup.prototype.addLine = function(line) {
    this.lines.push(line);

    const notDelta = (this.type === GrDiffGroup.Type.BOTH ||
        this.type === GrDiffGroup.Type.CONTEXT_CONTROL);
    if (notDelta && (line.type === GrDiffLine.Type.ADD ||
        line.type === GrDiffLine.Type.REMOVE)) {
      throw Error('Cannot add delta line to a non-delta group.');
    }

    if (line.type === GrDiffLine.Type.ADD) {
      this.adds.push(line);
    } else if (line.type === GrDiffLine.Type.REMOVE) {
      this.removes.push(line);
    }
    this._updateRange(line);
  };

  GrDiffGroup.prototype.getSideBySidePairs = function() {
    if (this.type === GrDiffGroup.Type.BOTH ||
        this.type === GrDiffGroup.Type.CONTEXT_CONTROL) {
      return this.lines.map(line => {
        return {
          left: line,
          right: line,
        };
      });
    }

    const pairs = [];
    let i = 0;
    let j = 0;
    while (i < this.removes.length || j < this.adds.length) {
      pairs.push({
        left: this.removes[i] || GrDiffLine.BLANK_LINE,
        right: this.adds[j] || GrDiffLine.BLANK_LINE,
      });
      i++;
      j++;
    }
    return pairs;
  };

  GrDiffGroup.prototype._updateRange = function(line) {
    if (line.beforeNumber === 'FILE' || line.afterNumber === 'FILE') { return; }

    if (line.type === GrDiffLine.Type.ADD ||
        line.type === GrDiffLine.Type.BOTH) {
      if (this.lineRange.right.start === null ||
          line.afterNumber < this.lineRange.right.start) {
        this.lineRange.right.start = line.afterNumber;
      }
      if (this.lineRange.right.end === null ||
          line.afterNumber > this.lineRange.right.end) {
        this.lineRange.right.end = line.afterNumber;
      }
    }

    if (line.type === GrDiffLine.Type.REMOVE ||
        line.type === GrDiffLine.Type.BOTH) {
      if (this.lineRange.left.start === null ||
          line.beforeNumber < this.lineRange.left.start) {
        this.lineRange.left.start = line.beforeNumber;
      }
      if (this.lineRange.left.end === null ||
          line.beforeNumber > this.lineRange.left.end) {
        this.lineRange.left.end = line.beforeNumber;
      }
    }
  };

  window.GrDiffGroup = GrDiffGroup;
})(window, GrDiffLine);
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
(function(window, GrDiffGroup, GrDiffLine) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilder) { return; }

  /**
   * In JS, unicode code points above 0xFFFF occupy two elements of a string.
   * For example '𐀏'.length is 2. An occurence of such a code point is called a
   * surrogate pair.
   *
   * This regex segments a string along tabs ('\t') and surrogate pairs, since
   * these are two cases where '1 char' does not automatically imply '1 column'.
   *
   * TODO: For human languages whose orthographies use combining marks, this
   * approach won't correctly identify the grapheme boundaries. In those cases,
   * a grapheme consists of multiple code points that should count as only one
   * character against the column limit. Getting that correct (if it's desired)
   * is probably beyond the limits of a regex, but there are nonstandard APIs to
   * do this, and proposed (but, as of Nov 2017, unimplemented) standard APIs.
   *
   * Further reading:
   *   On Unicode in JS: https://mathiasbynens.be/notes/javascript-unicode
   *   Graphemes: http://unicode.org/reports/tr29/#Grapheme_Cluster_Boundaries
   *   A proposed JS API: https://github.com/tc39/proposal-intl-segmenter
   */
  const REGEX_TAB_OR_SURROGATE_PAIR = /\t|[\uD800-\uDBFF][\uDC00-\uDFFF]/;

  function GrDiffBuilder(diff, comments, createThreadGroupFn, prefs, outputEl,
      layers) {
    this._diff = diff;
    this._comments = comments;
    this._createThreadGroupFn = createThreadGroupFn;
    this._prefs = prefs;
    this._outputEl = outputEl;
    this.groups = [];
    this._blameInfo = null;

    this.layers = layers || [];

    if (isNaN(prefs.tab_size) || prefs.tab_size <= 0) {
      throw Error('Invalid tab size from preferences.');
    }

    if (isNaN(prefs.line_length) || prefs.line_length <= 0) {
      throw Error('Invalid line length from preferences.');
    }

    for (const layer of this.layers) {
      if (layer.addListener) {
        layer.addListener(this._handleLayerUpdate.bind(this));
      }
    }
  }

  GrDiffBuilder.GroupType = {
    ADDED: 'b',
    BOTH: 'ab',
    REMOVED: 'a',
  };

  GrDiffBuilder.Highlights = {
    ADDED: 'edit_b',
    REMOVED: 'edit_a',
  };

  GrDiffBuilder.Side = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  GrDiffBuilder.ContextButtonType = {
    ABOVE: 'above',
    BELOW: 'below',
    ALL: 'all',
  };

  const PARTIAL_CONTEXT_AMOUNT = 10;

  /**
   * Abstract method
   * @param {string} outputEl
   * @param {number} fontSize
   */
  GrDiffBuilder.prototype.addColumns = function() {
    throw Error('Subclasses must implement addColumns');
  };

  /**
   * Abstract method
   * @param {Object} group
   */
  GrDiffBuilder.prototype.buildSectionElement = function() {
    throw Error('Subclasses must implement buildSectionElement');
  };

  GrDiffBuilder.prototype.emitGroup = function(group, opt_beforeSection) {
    const element = this.buildSectionElement(group);
    this._outputEl.insertBefore(element, opt_beforeSection);
    group.element = element;
  };

  GrDiffBuilder.prototype.renderSection = function(element) {
    for (let i = 0; i < this.groups.length; i++) {
      const group = this.groups[i];
      if (group.element === element) {
        const newElement = this.buildSectionElement(group);
        group.element.parentElement.replaceChild(newElement, group.element);
        group.element = newElement;
        break;
      }
    }
  };

  GrDiffBuilder.prototype.getGroupsByLineRange = function(
      startLine, endLine, opt_side) {
    const groups = [];
    for (let i = 0; i < this.groups.length; i++) {
      const group = this.groups[i];
      if (group.lines.length === 0) {
        continue;
      }
      let groupStartLine = 0;
      let groupEndLine = 0;
      if (opt_side) {
        groupStartLine = group.lineRange[opt_side].start;
        groupEndLine = group.lineRange[opt_side].end;
      }

      if (groupStartLine === 0) { // Line was removed or added.
        groupStartLine = groupEndLine;
      }
      if (groupEndLine === 0) { // Line was removed or added.
        groupEndLine = groupStartLine;
      }
      if (startLine <= groupEndLine && endLine >= groupStartLine) {
        groups.push(group);
      }
    }
    return groups;
  };

  GrDiffBuilder.prototype.getContentByLine = function(lineNumber, opt_side,
      opt_root) {
    const root = Polymer.dom(opt_root || this._outputEl);
    const sideSelector = opt_side ? ('.' + opt_side) : '';
    return root.querySelector('td.lineNum[data-value="' + lineNumber +
        '"]' + sideSelector + ' ~ td.content .contentText');
  };

  /**
   * Find line elements or line objects by a range of line numbers and a side.
   *
   * @param {number} start The first line number
   * @param {number} end The last line number
   * @param {string} opt_side The side of the range. Either 'left' or 'right'.
   * @param {!Array<GrDiffLine>} out_lines The output list of line objects. Use
   *     null if not desired.
   * @param  {!Array<HTMLElement>} out_elements The output list of line elements.
   *     Use null if not desired.
   */
  GrDiffBuilder.prototype.findLinesByRange = function(start, end, opt_side,
      out_lines, out_elements) {
    const groups = this.getGroupsByLineRange(start, end, opt_side);
    for (const group of groups) {
      let content = null;
      for (const line of group.lines) {
        if ((opt_side === 'left' && line.type === GrDiffLine.Type.ADD) ||
            (opt_side === 'right' && line.type === GrDiffLine.Type.REMOVE)) {
          continue;
        }
        const lineNumber = opt_side === 'left' ?
            line.beforeNumber : line.afterNumber;
        if (lineNumber < start || lineNumber > end) { continue; }

        if (out_lines) { out_lines.push(line); }
        if (out_elements) {
          if (content) {
            content = this._getNextContentOnSide(content, opt_side);
          } else {
            content = this.getContentByLine(lineNumber, opt_side,
                group.element);
          }
          if (content) { out_elements.push(content); }
        }
      }
    }
  };

  /**
   * Re-renders the DIV.contentText elements for the given side and range of
   * diff content.
   */
  GrDiffBuilder.prototype._renderContentByRange = function(start, end, side) {
    const lines = [];
    const elements = [];
    let line;
    let el;
    this.findLinesByRange(start, end, side, lines, elements);
    for (let i = 0; i < lines.length; i++) {
      line = lines[i];
      el = elements[i];
      if (!el) {
        // Cannot re-render an element if it does not exist. This can happen
        // if lines are collapsed and not visible on the page yet.
        continue;
      }
      el.parentElement.replaceChild(this._createTextEl(line, side).firstChild,
          el);
    }
  };

  GrDiffBuilder.prototype.getSectionsByLineRange = function(
      startLine, endLine, opt_side) {
    return this.getGroupsByLineRange(startLine, endLine, opt_side).map(
        group => { return group.element; });
  };

  // TODO(wyatta): Move this completely into the processor.
  GrDiffBuilder.prototype._insertContextGroups = function(groups, lines,
      hiddenRange) {
    const linesBeforeCtx = lines.slice(0, hiddenRange[0]);
    const hiddenLines = lines.slice(hiddenRange[0], hiddenRange[1]);
    const linesAfterCtx = lines.slice(hiddenRange[1]);

    if (linesBeforeCtx.length > 0) {
      groups.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, linesBeforeCtx));
    }

    const ctxLine = new GrDiffLine(GrDiffLine.Type.CONTEXT_CONTROL);
    ctxLine.contextGroup =
        new GrDiffGroup(GrDiffGroup.Type.BOTH, hiddenLines);
    groups.push(new GrDiffGroup(GrDiffGroup.Type.CONTEXT_CONTROL,
        [ctxLine]));

    if (linesAfterCtx.length > 0) {
      groups.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, linesAfterCtx));
    }
  };

  GrDiffBuilder.prototype._createContextControl = function(section, line) {
    if (!line.contextGroup || !line.contextGroup.lines.length) {
      return null;
    }

    const td = this._createElement('td');
    const showPartialLinks =
        line.contextGroup.lines.length > PARTIAL_CONTEXT_AMOUNT;

    if (showPartialLinks) {
      td.appendChild(this._createContextButton(
          GrDiffBuilder.ContextButtonType.ABOVE, section, line));
      td.appendChild(document.createTextNode(' - '));
    }

    td.appendChild(this._createContextButton(
        GrDiffBuilder.ContextButtonType.ALL, section, line));

    if (showPartialLinks) {
      td.appendChild(document.createTextNode(' - '));
      td.appendChild(this._createContextButton(
          GrDiffBuilder.ContextButtonType.BELOW, section, line));
    }

    return td;
  };

  GrDiffBuilder.prototype._createContextButton = function(type, section, line) {
    const contextLines = line.contextGroup.lines;
    const context = PARTIAL_CONTEXT_AMOUNT;

    const button = this._createElement('gr-button', 'showContext');
    button.setAttribute('link', true);
    button.setAttribute('no-uppercase', true);

    let text;
    const groups = []; // The groups that replace this one if tapped.

    if (type === GrDiffBuilder.ContextButtonType.ALL) {
      text = 'Show ' + contextLines.length + ' common line';
      if (contextLines.length > 1) { text += 's'; }
      groups.push(line.contextGroup);
    } else if (type === GrDiffBuilder.ContextButtonType.ABOVE) {
      text = '+' + context + '↑';
      this._insertContextGroups(groups, contextLines,
          [context, contextLines.length]);
    } else if (type === GrDiffBuilder.ContextButtonType.BELOW) {
      text = '+' + context + '↓';
      this._insertContextGroups(groups, contextLines,
          [0, contextLines.length - context]);
    }

    Polymer.dom(button).textContent = text;

    button.addEventListener('tap', e => {
      e.detail = {
        groups,
        section,
      };
      // Let it bubble up the DOM tree.
    });

    return button;
  };

  GrDiffBuilder.prototype._getCommentsForLine = function(comments, line,
      opt_side) {
    function byLineNum(lineNum) {
      return function(c) {
        return (c.line === lineNum) ||
               (c.line === undefined && lineNum === GrDiffLine.FILE);
      };
    }
    const leftComments =
        comments[GrDiffBuilder.Side.LEFT].filter(byLineNum(line.beforeNumber));
    const rightComments =
        comments[GrDiffBuilder.Side.RIGHT].filter(byLineNum(line.afterNumber));

    leftComments.forEach(c => { c.__commentSide = 'left'; });
    rightComments.forEach(c => { c.__commentSide = 'right'; });

    let result;

    switch (opt_side) {
      case GrDiffBuilder.Side.LEFT:
        result = leftComments;
        break;
      case GrDiffBuilder.Side.RIGHT:
        result = rightComments;
        break;
      default:
        result = leftComments.concat(rightComments);
        break;
    }

    return result;
  };

  /**
   * @param {number} line
   * @param {string=} opt_side
   * @return {!Object}
   */
  GrDiffBuilder.prototype._commentThreadGroupForLine = function(
      line, opt_side) {
    const comments =
        this._getCommentsForLine(this._comments, line, opt_side);
    if (!comments || comments.length === 0) {
      return null;
    }

    let patchNum = this._comments.meta.patchRange.patchNum;
    let isOnParent = comments[0].side === 'PARENT' || false;
    if (line.type === GrDiffLine.Type.REMOVE ||
        opt_side === GrDiffBuilder.Side.LEFT) {
      if (this._comments.meta.patchRange.basePatchNum === 'PARENT' ||
          Gerrit.PatchSetBehavior.isMergeParent(
              this._comments.meta.patchRange.basePatchNum)) {
        isOnParent = true;
      } else {
        patchNum = this._comments.meta.patchRange.basePatchNum;
      }
    }
    const threadGroupEl = this._createThreadGroupFn(patchNum, isOnParent,
        opt_side);
    threadGroupEl.comments = comments;
    if (opt_side) {
      threadGroupEl.setAttribute('data-side', opt_side);
    }
    return threadGroupEl;
  };

  GrDiffBuilder.prototype._createLineEl = function(
      line, number, type, opt_class) {
    const td = this._createElement('td');
    if (opt_class) {
      td.classList.add(opt_class);
    }

    if (line.type === GrDiffLine.Type.REMOVE) {
      td.setAttribute('aria-label', `${number} removed`);
    } else if (line.type === GrDiffLine.Type.ADD) {
      td.setAttribute('aria-label', `${number} added`);
    }

    if (line.type === GrDiffLine.Type.BLANK) {
      return td;
    } else if (line.type === GrDiffLine.Type.CONTEXT_CONTROL) {
      td.classList.add('contextLineNum');
      td.setAttribute('data-value', '@@');
      td.textContent = '@@';
    } else if (line.type === GrDiffLine.Type.BOTH || line.type === type) {
      td.classList.add('lineNum');
      td.setAttribute('data-value', number);
      td.textContent = number === 'FILE' ? 'File' : number;
    }
    return td;
  };

  GrDiffBuilder.prototype._createTextEl = function(line, opt_side) {
    const td = this._createElement('td');
    if (line.type !== GrDiffLine.Type.BLANK) {
      td.classList.add('content');
    }
    td.classList.add(line.type);

    const lineLimit =
        !this._prefs.line_wrapping ? this._prefs.line_length : Infinity;

    const contentText =
        this._formatText(line.text, this._prefs.tab_size, lineLimit);
    if (opt_side) {
      contentText.setAttribute('data-side', opt_side);
    }

    for (const layer of this.layers) {
      layer.annotate(contentText, line);
    }

    td.appendChild(contentText);

    return td;
  };

  /**
   * Returns a 'div' element containing the supplied |text| as its innerText,
   * with '\t' characters expanded to a width determined by |tabSize|, and the
   * text wrapped at column |lineLimit|, which may be Infinity if no wrapping is
   * desired.
   *
   * @param {string} text The text to be formatted.
   * @param {number} tabSize The width of each tab stop.
   * @param {number} lineLimit The column after which to wrap lines.
   * @return {HTMLElement}
   */
  GrDiffBuilder.prototype._formatText = function(text, tabSize, lineLimit) {
    const contentText = this._createElement('div', 'contentText');

    let columnPos = 0;
    let textOffset = 0;
    for (const segment of text.split(REGEX_TAB_OR_SURROGATE_PAIR)) {
      if (segment) {
        // |segment| contains only normal characters. If |segment| doesn't fit
        // entirely on the current line, append chunks of |segment| followed by
        // line breaks.
        let rowStart = 0;
        let rowEnd = lineLimit - columnPos;
        while (rowEnd < segment.length) {
          contentText.appendChild(
              document.createTextNode(segment.substring(rowStart, rowEnd)));
          contentText.appendChild(this._createElement('span', 'br'));
          columnPos = 0;
          rowStart = rowEnd;
          rowEnd += lineLimit;
        }
        // Append the last part of |segment|, which fits on the current line.
        contentText.appendChild(
            document.createTextNode(segment.substring(rowStart)));
        columnPos += (segment.length - rowStart);
        textOffset += segment.length;
      }
      if (textOffset < text.length) {
        // Handle the special character at |textOffset|.
        if (text.startsWith('\t', textOffset)) {
          // Append a single '\t' character.
          let effectiveTabSize = tabSize - (columnPos % tabSize);
          if (columnPos + effectiveTabSize > lineLimit) {
            contentText.appendChild(this._createElement('span', 'br'));
            columnPos = 0;
            effectiveTabSize = tabSize;
          }
          contentText.appendChild(this._getTabWrapper(effectiveTabSize));
          columnPos += effectiveTabSize;
          textOffset++;
        } else {
          // Append a single surrogate pair.
          if (columnPos >= lineLimit) {
            contentText.appendChild(this._createElement('span', 'br'));
            columnPos = 0;
          }
          contentText.appendChild(document.createTextNode(
              text.substring(textOffset, textOffset + 2)));
          textOffset += 2;
          columnPos += 1;
        }
      }
    }
    return contentText;
  };

  /**
   * Returns a <span> element holding a '\t' character, that will visually
   * occupy |tabSize| many columns.
   *
   * @param {number} tabSize The effective size of this tab stop.
   * @return {HTMLElement}
   */
  GrDiffBuilder.prototype._getTabWrapper = function(tabSize) {
    // Force this to be a number to prevent arbitrary injection.
    const result = this._createElement('span', 'tab');
    result.style['tab-size'] = tabSize;
    result.style['-moz-tab-size'] = tabSize;
    result.innerText = '\t';
    return result;
  };

  GrDiffBuilder.prototype._createElement = function(tagName, classStr) {
    const el = document.createElement(tagName);
    // When Shady DOM is being used, these classes are added to account for
    // Polymer's polyfill behavior. In order to guarantee sufficient
    // specificity within the CSS rules, these are added to every element.
    // Since the Polymer DOM utility functions (which would do this
    // automatically) are not being used for performance reasons, this is
    // done manually.
    el.classList.add('style-scope', 'gr-diff');
    if (classStr) {
      for (const className of classStr.split(' ')) {
        el.classList.add(className);
      }
    }
    return el;
  };

  GrDiffBuilder.prototype._handleLayerUpdate = function(start, end, side) {
    this._renderContentByRange(start, end, side);
  };

  /**
   * Finds the next DIV.contentText element following the given element, and on
   * the same side. Will only search within a group.
   * @param {HTMLElement} content
   * @param {string} side Either 'left' or 'right'
   * @return {HTMLElement}
   */
  GrDiffBuilder.prototype._getNextContentOnSide = function(content, side) {
    throw Error('Subclasses must implement _getNextContentOnSide');
  };

  /**
   * Determines whether the given group is either totally an addition or totally
   * a removal.
   * @param {!Object} group (GrDiffGroup)
   * @return {boolean}
   */
  GrDiffBuilder.prototype._isTotal = function(group) {
    return group.type === GrDiffGroup.Type.DELTA &&
        (!group.adds.length || !group.removes.length) &&
        !(!group.adds.length && !group.removes.length);
  };

  /**
   * Set the blame information for the diff. For any already-rendered line,
   * re-render its blame cell content.
   * @param {Object} blame
   */
  GrDiffBuilder.prototype.setBlame = function(blame) {
    this._blameInfo = blame;

    // TODO(wyatta): make this loop asynchronous.
    for (const commit of blame) {
      for (const range of commit.ranges) {
        for (let i = range.start; i <= range.end; i++) {
          // TODO(wyatta): this query is expensive, but, when traversing a
          // range, the lines are consecutive, and given the previous blame
          // cell, the next one can be reached cheaply.
          const el = this._getBlameByLineNum(i);
          if (!el) { continue; }
          // Remove the element's children (if any).
          while (el.hasChildNodes()) {
            el.removeChild(el.lastChild);
          }
          const blame = this._getBlameForBaseLine(i, commit);
          el.appendChild(blame);
        }
      }
    }
  };

  /**
   * Find the blame cell for a given line number.
   * @param {number} lineNum
   * @return {HTMLTableDataCellElement}
   */
  GrDiffBuilder.prototype._getBlameByLineNum = function(lineNum) {
    const root = Polymer.dom(this._outputEl);
    return root.querySelector(`td.blame[data-line-number="${lineNum}"]`);
  };

  /**
   * Given a base line number, return the commit containing that line in the
   * current set of blame information. If no blame information has been
   * provided, null is returned.
   * @param {number} lineNum
   * @return {Object} The commit information.
   */
  GrDiffBuilder.prototype._getBlameCommitForBaseLine = function(lineNum) {
    if (!this._blameInfo) { return null; }

    for (const blameCommit of this._blameInfo) {
      for (const range of blameCommit.ranges) {
        if (range.start <= lineNum && range.end >= lineNum) {
          return blameCommit;
        }
      }
    }
    return null;
  };

  /**
   * Given the number of a base line, get the content for the blame cell of that
   * line. If there is no blame information for that line, returns null.
   * @param {number} lineNum
   * @param {Object=} opt_commit Optionally provide the commit object, so that
   *     it does not need to be searched.
   * @return {HTMLSpanElement}
   */
  GrDiffBuilder.prototype._getBlameForBaseLine = function(lineNum, opt_commit) {
    const commit = opt_commit || this._getBlameCommitForBaseLine(lineNum);
    if (!commit) { return null; }

    const isStartOfRange = commit.ranges.some(r => r.start === lineNum);

    const date = (new Date(commit.time * 1000)).toLocaleDateString();
    const blameNode = this._createElement('span',
        isStartOfRange ? 'startOfRange' : '');
    const shaNode = this._createElement('span', 'sha');
    shaNode.innerText = commit.id.substr(0, 7);
    blameNode.appendChild(shaNode);
    blameNode.append(` on ${date} by ${commit.author}`);
    return blameNode;
  };

  /**
   * Create a blame cell for the given base line. Blame information will be
   * included in the cell if available.
   * @param {GrDiffLine} line
   * @return {HTMLTableDataCellElement}
   */
  GrDiffBuilder.prototype._createBlameCell = function(line) {
    const blameTd = this._createElement('td', 'blame');
    blameTd.setAttribute('data-line-number', line.beforeNumber);
    if (line.beforeNumber) {
      const content = this._getBlameForBaseLine(line.beforeNumber);
      if (content) {
        blameTd.appendChild(content);
      }
    }
    return blameTd;
  };

  window.GrDiffBuilder = GrDiffBuilder;
})(window, GrDiffGroup, GrDiffLine);
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
(function(window, GrDiffBuilder) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilderSideBySide) { return; }

  function GrDiffBuilderSideBySide(diff, comments, createThreadGroupFn, prefs,
      outputEl, layers) {
    GrDiffBuilder.call(this, diff, comments, createThreadGroupFn, prefs,
        outputEl, layers);
  }
  GrDiffBuilderSideBySide.prototype = Object.create(GrDiffBuilder.prototype);
  GrDiffBuilderSideBySide.prototype.constructor = GrDiffBuilderSideBySide;

  GrDiffBuilderSideBySide.prototype.buildSectionElement = function(group) {
    const sectionEl = this._createElement('tbody', 'section');
    sectionEl.classList.add(group.type);
    if (this._isTotal(group)) {
      sectionEl.classList.add('total');
    }
    if (group.dueToRebase) {
      sectionEl.classList.add('dueToRebase');
    }
    const pairs = group.getSideBySidePairs();
    for (let i = 0; i < pairs.length; i++) {
      sectionEl.appendChild(this._createRow(sectionEl, pairs[i].left,
          pairs[i].right));
    }
    return sectionEl;
  };

  GrDiffBuilderSideBySide.prototype.addColumns = function(outputEl, fontSize) {
    const width = fontSize * 4;
    const colgroup = document.createElement('colgroup');

    // Add the blame column.
    let col = this._createElement('col', 'blame');
    colgroup.appendChild(col);

    // Add left-side line number.
    col = document.createElement('col');
    col.setAttribute('width', width);
    colgroup.appendChild(col);

    // Add left-side content.
    colgroup.appendChild(document.createElement('col'));

    // Add right-side line number.
    col = document.createElement('col');
    col.setAttribute('width', width);
    colgroup.appendChild(col);

    // Add right-side content.
    colgroup.appendChild(document.createElement('col'));

    outputEl.appendChild(colgroup);
  };

  GrDiffBuilderSideBySide.prototype._createRow = function(section, leftLine,
      rightLine) {
    const row = this._createElement('tr');
    row.classList.add('diff-row', 'side-by-side');
    row.setAttribute('left-type', leftLine.type);
    row.setAttribute('right-type', rightLine.type);
    row.tabIndex = -1;

    row.appendChild(this._createBlameCell(leftLine));

    this._appendPair(section, row, leftLine, leftLine.beforeNumber,
        GrDiffBuilder.Side.LEFT);
    this._appendPair(section, row, rightLine, rightLine.afterNumber,
        GrDiffBuilder.Side.RIGHT);
    return row;
  };

  GrDiffBuilderSideBySide.prototype._appendPair = function(section, row, line,
      lineNumber, side) {
    const lineEl = this._createLineEl(line, lineNumber, line.type, side);
    lineEl.classList.add(side);
    row.appendChild(lineEl);
    const action = this._createContextControl(section, line);
    if (action) {
      row.appendChild(action);
    } else {
      const textEl = this._createTextEl(line, side);
      const threadGroupEl = this._commentThreadGroupForLine(line, side);
      if (threadGroupEl) {
        textEl.appendChild(threadGroupEl);
      }
      row.appendChild(textEl);
    }
  };

  GrDiffBuilderSideBySide.prototype._getNextContentOnSide = function(
      content, side) {
    let tr = content.parentElement.parentElement;
    while (tr = tr.nextSibling) {
      content = tr.querySelector(
          'td.content .contentText[data-side="' + side + '"]');
      if (content) { return content; }
    }
    return null;
  };

  window.GrDiffBuilderSideBySide = GrDiffBuilderSideBySide;
})(window, GrDiffBuilder);
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
(function(window, GrDiffBuilder) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilderUnified) { return; }

  function GrDiffBuilderUnified(diff, comments, createThreadGroupFn, prefs,
      outputEl, layers) {
    GrDiffBuilder.call(this, diff, comments, createThreadGroupFn, prefs,
        outputEl, layers);
  }
  GrDiffBuilderUnified.prototype = Object.create(GrDiffBuilder.prototype);
  GrDiffBuilderUnified.prototype.constructor = GrDiffBuilderUnified;

  GrDiffBuilderUnified.prototype.buildSectionElement = function(group) {
    const sectionEl = this._createElement('tbody', 'section');
    sectionEl.classList.add(group.type);
    if (this._isTotal(group)) {
      sectionEl.classList.add('total');
    }
    if (group.dueToRebase) {
      sectionEl.classList.add('dueToRebase');
    }

    for (let i = 0; i < group.lines.length; ++i) {
      sectionEl.appendChild(this._createRow(sectionEl, group.lines[i]));
    }
    return sectionEl;
  };

  GrDiffBuilderUnified.prototype.addColumns = function(outputEl, fontSize) {
    const width = fontSize * 4;
    const colgroup = document.createElement('colgroup');

    // Add the blame column.
    let col = this._createElement('col', 'blame');
    colgroup.appendChild(col);

    // Add left-side line number.
    col = document.createElement('col');
    col.setAttribute('width', width);
    colgroup.appendChild(col);

    // Add right-side line number.
    col = document.createElement('col');
    col.setAttribute('width', width);
    colgroup.appendChild(col);

    // Add the content.
    colgroup.appendChild(document.createElement('col'));

    outputEl.appendChild(colgroup);
  };

  GrDiffBuilderUnified.prototype._createRow = function(section, line) {
    const row = this._createElement('tr', line.type);
    row.appendChild(this._createBlameCell(line));

    let lineEl = this._createLineEl(line, line.beforeNumber,
        GrDiffLine.Type.REMOVE);
    lineEl.classList.add('left');
    row.appendChild(lineEl);
    lineEl = this._createLineEl(line, line.afterNumber,
        GrDiffLine.Type.ADD);
    lineEl.classList.add('right');
    row.appendChild(lineEl);
    row.classList.add('diff-row', 'unified');
    row.tabIndex = -1;

    const action = this._createContextControl(section, line);
    if (action) {
      row.appendChild(action);
    } else {
      const textEl = this._createTextEl(line);
      const threadGroupEl = this._commentThreadGroupForLine(line);
      if (threadGroupEl) {
        textEl.appendChild(threadGroupEl);
      }
      row.appendChild(textEl);
    }
    return row;
  };

  GrDiffBuilderUnified.prototype._getNextContentOnSide = function(
      content, side) {
    let tr = content.parentElement.parentElement;
    while (tr = tr.nextSibling) {
      if (tr.classList.contains('both') || (
          (side === 'left' && tr.classList.contains('remove')) ||
          (side === 'right' && tr.classList.contains('add')))) {
        return tr.querySelector('.contentText');
      }
    }
    return null;
  };

  window.GrDiffBuilderUnified = GrDiffBuilderUnified;
})(window, GrDiffBuilder);
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
(function(window, GrDiffBuilderSideBySide) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilderImage) { return; }

  const IMAGE_MIME_PATTERN = /^image\/(bmp|gif|jpeg|jpg|png|tiff|webp)$/;

  function GrDiffBuilderImage(diff, comments, createThreadGroupFn, prefs,
      outputEl, baseImage, revisionImage) {
    GrDiffBuilderSideBySide.call(this, diff, comments, createThreadGroupFn,
        prefs, outputEl, []);
    this._baseImage = baseImage;
    this._revisionImage = revisionImage;
  }

  GrDiffBuilderImage.prototype = Object.create(
      GrDiffBuilderSideBySide.prototype);
  GrDiffBuilderImage.prototype.constructor = GrDiffBuilderImage;

  GrDiffBuilderImage.prototype.renderDiff = function() {
    const section = this._createElement('tbody', 'image-diff');

    this._emitImagePair(section);
    this._emitImageLabels(section);

    this._outputEl.appendChild(section);
    this._outputEl.appendChild(this._createEndpoint());
  };

  GrDiffBuilderImage.prototype._createEndpoint = function() {
    const tbody = this._createElement('tbody');
    const tr = this._createElement('tr');
    const td = this._createElement('td');

    // TODO(kaspern): Support blame for image diffs and remove the hardcoded 4
    // column limit.
    td.setAttribute('colspan', '4');
    const endpoint = this._createElement('gr-endpoint-decorator');
    const endpointDomApi = Polymer.dom(endpoint);
    endpointDomApi.setAttribute('name', 'image-diff');
    endpointDomApi.appendChild(
        this._createEndpointParam('baseImage', this._baseImage));
    endpointDomApi.appendChild(
        this._createEndpointParam('revisionImage', this._revisionImage));
    td.appendChild(endpoint);
    tr.appendChild(td);
    tbody.appendChild(tr);
    return tbody;
  };

  GrDiffBuilderImage.prototype._createEndpointParam = function(name, value) {
    const endpointParam = this._createElement('gr-endpoint-param');
    endpointParam.setAttribute('name', name);
    endpointParam.value = value;
    return endpointParam;
  };

  GrDiffBuilderImage.prototype._emitImagePair = function(section) {
    const tr = this._createElement('tr');

    tr.appendChild(this._createElement('td', 'left lineNum blank'));
    tr.appendChild(this._createImageCell(this._baseImage, 'left', section));

    tr.appendChild(this._createElement('td', 'right lineNum blank'));
    tr.appendChild(this._createImageCell(
        this._revisionImage, 'right', section));

    section.appendChild(tr);
  };

  GrDiffBuilderImage.prototype._createImageCell = function(image, className,
      section) {
    const td = this._createElement('td', className);
    if (image && IMAGE_MIME_PATTERN.test(image.type)) {
      const imageEl = this._createElement('img');
      imageEl.onload = function() {
        image._height = imageEl.naturalHeight;
        image._width = imageEl.naturalWidth;
        this._updateImageLabel(section, className, image);
      }.bind(this);
      imageEl.src = 'data:' + image.type + ';base64, ' + image.body;
      imageEl.addEventListener('error', () => {
        imageEl.remove();
        td.textContent = '[Image failed to load]';
      });
      td.appendChild(imageEl);
    }
    return td;
  };

  GrDiffBuilderImage.prototype._updateImageLabel = function(section, className,
      image) {
    const label = Polymer.dom(section)
        .querySelector('.' + className + ' span.label');
    this._setLabelText(label, image);
  };

  GrDiffBuilderImage.prototype._setLabelText = function(label, image) {
    label.textContent = this._getImageLabel(image);
  };

  GrDiffBuilderImage.prototype._emitImageLabels = function(section) {
    const tr = this._createElement('tr');

    let addNamesInLabel = false;

    if (this._baseImage && this._revisionImage &&
        this._baseImage._name !== this._revisionImage._name) {
      addNamesInLabel = true;
    }

    tr.appendChild(this._createElement('td', 'left lineNum blank'));
    let td = this._createElement('td', 'left');
    let label = this._createElement('label');
    let nameSpan;
    let labelSpan = this._createElement('span', 'label');

    if (addNamesInLabel) {
      nameSpan = this._createElement('span', 'name');
      nameSpan.textContent = this._baseImage._name;
      label.appendChild(nameSpan);
      label.appendChild(this._createElement('br'));
    }

    this._setLabelText(labelSpan, this._baseImage, addNamesInLabel);

    label.appendChild(labelSpan);
    td.appendChild(label);
    tr.appendChild(td);

    tr.appendChild(this._createElement('td', 'right lineNum blank'));
    td = this._createElement('td', 'right');
    label = this._createElement('label');
    labelSpan = this._createElement('span', 'label');

    if (addNamesInLabel) {
      nameSpan = this._createElement('span', 'name');
      nameSpan.textContent = this._revisionImage._name;
      label.appendChild(nameSpan);
      label.appendChild(this._createElement('br'));
    }

    this._setLabelText(labelSpan, this._revisionImage, addNamesInLabel);

    label.appendChild(labelSpan);
    td.appendChild(label);
    tr.appendChild(td);

    section.appendChild(tr);
  };

  GrDiffBuilderImage.prototype._getImageLabel = function(image) {
    if (image) {
      const type = image.type || image._expectedType;
      if (image._width && image._height) {
        return image._width + '×' + image._height + ' ' + type;
      } else {
        return type;
      }
    }
    return 'No image';
  };

  window.GrDiffBuilderImage = GrDiffBuilderImage;
})(window, GrDiffBuilderSideBySide);
/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
(function(window, GrDiffBuilderSideBySide) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilderBinary) { return; }

  function GrDiffBuilderBinary(diff, comments, prefs, outputEl) {
    GrDiffBuilder.call(this, diff, comments, null, prefs, outputEl);
  }

  GrDiffBuilderBinary.prototype = Object.create(GrDiffBuilder.prototype);
  GrDiffBuilderBinary.prototype.constructor = GrDiffBuilderBinary;

  // This method definition is a no-op to satisfy the parent type.
  GrDiffBuilderBinary.prototype.addColumns = function(outputEl, fontSize) {};

  GrDiffBuilderBinary.prototype.buildSectionElement = function() {
    const section = this._createElement('tbody', 'binary-diff');
    const row = this._createElement('tr');
    const cell = this._createElement('td');
    const label = this._createElement('label');
    label.textContent = 'Difference in binary files';
    cell.appendChild(label);
    row.appendChild(cell);
    section.appendChild(row);
    return section;
  };

  window.GrDiffBuilderBinary = GrDiffBuilderBinary;
})(window, GrDiffBuilderSideBySide);

const Defs = {};

/**
 * @typedef {{
 *  number: number,
 *  leftSide: {boolean}
 * }}
 */
Defs.LineOfInterest;

const DiffViewMode = {
  SIDE_BY_SIDE: 'SIDE_BY_SIDE',
  UNIFIED: 'UNIFIED_DIFF',
};

const TimingLabel = {
  TOTAL: 'Diff Total Render',
  CONTENT: 'Diff Content Render',
  SYNTAX: 'Diff Syntax Render',
};

// If any line of the diff is more than the character limit, then disable
// syntax highlighting for the entire file.
const SYNTAX_MAX_LINE_LENGTH = 500;

const TRAILING_WHITESPACE_PATTERN = /\s+$/;

Polymer({
  _template: Polymer.html`
    <div class="contentWrapper">
      <slot></slot>
    </div>
    <gr-ranged-comment-layer id="rangeLayer" comments="[[comments]]"></gr-ranged-comment-layer>
    <gr-syntax-layer id="syntaxLayer" diff="[[diff]]"></gr-syntax-layer>
    <gr-diff-processor id="processor" groups="{{_groups}}"></gr-diff-processor>
    <gr-reporting id="reporting"></gr-reporting>
    <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
`,

  is: 'gr-diff-builder',

  /**
   * Fired when the diff begins rendering.
   *
   * @event render-start
   */

  /**
   * Fired when the diff is rendered.
   *
   * @event render
   */

  /**
   * Fired when the diff finishes rendering text content, but not
   * necessarily syntax highlights.
   *
   * @event render-content
   */

  properties: {
    diff: Object,
    diffPath: String,
    changeNum: String,
    patchNum: String,
    viewMode: String,
    comments: Object,
    isImageDiff: Boolean,
    baseImage: Object,
    revisionImage: Object,
    projectName: String,
    parentIndex: Number,
    /**
     * @type {Defs.LineOfInterest|null}
     */
    lineOfInterest: Object,

    /**
     * @type {function(number, booleam, !string)}
     */
    createCommentFn: Function,

    _builder: Object,
    _groups: Array,
    _layers: Array,
    _showTabs: Boolean,
  },

  get diffElement() {
    return this.queryEffectiveChildren('#diffTable');
  },

  observers: [
    '_groupsChanged(_groups.splices)',
  ],

  attached() {
    // Setup annotation layers.
    const layers = [
      this._createTrailingWhitespaceLayer(),
      this.$.syntaxLayer,
      this._createIntralineLayer(),
      this._createTabIndicatorLayer(),
      this.$.rangeLayer,
    ];

    // Get layers from plugins (if any).
    for (const pluginLayer of this.$.jsAPI.getDiffLayers(
        this.diffPath, this.changeNum, this.patchNum)) {
      layers.push(pluginLayer);
    }

    this._layers = layers;

    this.async(() => {
      this._preRenderThread();
    });
  },

  render(comments, prefs) {
    this.$.syntaxLayer.enabled = prefs.syntax_highlighting;
    this._showTabs = !!prefs.show_tabs;
    this._showTrailingWhitespace = !!prefs.show_whitespace_errors;

    // Stop the processor and syntax layer (if they're running).
    this.cancel();

    this._builder = this._getDiffBuilder(this.diff, comments, prefs);

    this.$.processor.context = prefs.context;
    this.$.processor.keyLocations = this._getKeyLocations(comments,
        this.lineOfInterest);

    this._clearDiffContent();
    this._builder.addColumns(this.diffElement, prefs.font_size);

    const reporting = this.$.reporting;
    const isBinary = !!(this.isImageDiff || this.diff.binary);

    reporting.time(TimingLabel.TOTAL);
    reporting.time(TimingLabel.CONTENT);
    this.dispatchEvent(new CustomEvent('render-start', {bubbles: true}));
    return this.$.processor.process(this.diff.content, isBinary)
        .then(() => {
          if (this.isImageDiff) {
            this._builder.renderDiff();
          }
          this.dispatchEvent(new CustomEvent('render-content',
              {bubbles: true}));

          if (this._anyLineTooLong()) {
            this.$.syntaxLayer.enabled = false;
          }

          reporting.timeEnd(TimingLabel.CONTENT);
          reporting.time(TimingLabel.SYNTAX);
          return this.$.syntaxLayer.process().then(() => {
            reporting.timeEnd(TimingLabel.SYNTAX);
            reporting.timeEnd(TimingLabel.TOTAL);
            this.dispatchEvent(
                new CustomEvent('render', {bubbles: true}));
          });
        });
  },

  getLineElByChild(node) {
    while (node) {
      if (node instanceof Element) {
        if (node.classList.contains('lineNum')) {
          return node;
        }
        if (node.classList.contains('section')) {
          return null;
        }
      }
      node = node.previousSibling || node.parentElement;
    }
    return null;
  },

  getLineNumberByChild(node) {
    const lineEl = this.getLineElByChild(node);
    return lineEl ?
        parseInt(lineEl.getAttribute('data-value'), 10) :
        null;
  },

  getContentByLine(lineNumber, opt_side, opt_root) {
    return this._builder.getContentByLine(lineNumber, opt_side, opt_root);
  },

  getContentByLineEl(lineEl) {
    const root = Polymer.dom(lineEl.parentElement);
    const side = this.getSideByLineEl(lineEl);
    const line = lineEl.getAttribute('data-value');
    return this.getContentByLine(line, side, root);
  },

  getLineElByNumber(lineNumber, opt_side) {
    const sideSelector = opt_side ? ('.' + opt_side) : '';
    return this.diffElement.querySelector(
        '.lineNum[data-value="' + lineNumber + '"]' + sideSelector);
  },

  getContentsByLineRange(startLine, endLine, opt_side) {
    const result = [];
    this._builder.findLinesByRange(startLine, endLine, opt_side, null,
        result);
    return result;
  },

  getSideByLineEl(lineEl) {
    return lineEl.classList.contains(GrDiffBuilder.Side.RIGHT) ?
    GrDiffBuilder.Side.RIGHT : GrDiffBuilder.Side.LEFT;
  },

  emitGroup(group, sectionEl) {
    this._builder.emitGroup(group, sectionEl);
  },

  showContext(newGroups, sectionEl) {
    const groups = this._builder.groups;

    const contextIndex = groups.findIndex(group =>
      group.element === sectionEl
    );
    groups.splice(...[contextIndex, 1].concat(newGroups));

    for (const newGroup of newGroups) {
      this._builder.emitGroup(newGroup, sectionEl);
    }
    sectionEl.parentNode.removeChild(sectionEl);

    this.async(() => this.fire('render-content'), 1);
  },

  cancel() {
    this.$.processor.cancel();
    this.$.syntaxLayer.cancel();
  },

  _handlePreferenceError(pref) {
    const message = `The value of the '${pref}' user preference is ` +
        `invalid. Fix in diff preferences`;
    this.dispatchEvent(new CustomEvent('show-alert', {
      detail: {
        message,
      }, bubbles: true}));
    throw Error(`Invalid preference value: ${pref}`);
  },

  _getDiffBuilder(diff, comments, prefs) {
    if (isNaN(prefs.tab_size) || prefs.tab_size <= 0) {
      this._handlePreferenceError('tab size');
      return;
    }

    if (isNaN(prefs.line_length) || prefs.line_length <= 0) {
      this._handlePreferenceError('diff width');
      return;
    }

    let builder = null;
    const createFn = this.createCommentFn;
    if (this.isImageDiff) {
      builder = new GrDiffBuilderImage(diff, comments, createFn, prefs,
        this.diffElement, this.baseImage, this.revisionImage);
    } else if (diff.binary) {
      // If the diff is binary, but not an image.
      return new GrDiffBuilderBinary(diff, comments, prefs,
          this.diffElement);
    } else if (this.viewMode === DiffViewMode.SIDE_BY_SIDE) {
      builder = new GrDiffBuilderSideBySide(diff, comments, createFn,
          prefs, this.diffElement, this._layers);
    } else if (this.viewMode === DiffViewMode.UNIFIED) {
      builder = new GrDiffBuilderUnified(diff, comments, createFn, prefs,
          this.diffElement, this._layers);
    }
    if (!builder) {
      throw Error('Unsupported diff view mode: ' + this.viewMode);
    }
    return builder;
  },

  _clearDiffContent() {
    this.diffElement.innerHTML = null;
  },

  /**
   * @param {!Object} comments
   * @param {Defs.LineOfInterest|null} lineOfInterest
   */
  _getKeyLocations(comments, lineOfInterest) {
    const result = {
      left: {},
      right: {},
    };
    for (const side in comments) {
      if (side !== GrDiffBuilder.Side.LEFT &&
          side !== GrDiffBuilder.Side.RIGHT) {
        continue;
      }
      for (const c of comments[side]) {
        result[side][c.line || GrDiffLine.FILE] = true;
      }
    }

    if (lineOfInterest) {
      const side = lineOfInterest.leftSide ? 'left' : 'right';
      result[side][lineOfInterest.number] = true;
    }

    return result;
  },

  _groupsChanged(changeRecord) {
    if (!changeRecord) { return; }
    for (const splice of changeRecord.indexSplices) {
      let group;
      for (let i = 0; i < splice.addedCount; i++) {
        group = splice.object[splice.index + i];
        this._builder.groups.push(group);
        this._builder.emitGroup(group);
      }
    }
  },

  _createIntralineLayer() {
    return {
      // Take a DIV.contentText element and a line object with intraline
      // differences to highlight and apply them to the element as
      // annotations.
      annotate(el, line) {
        const HL_CLASS = 'style-scope gr-diff intraline';
        for (const highlight of line.highlights) {
          // The start and end indices could be the same if a highlight is
          // meant to start at the end of a line and continue onto the
          // next one. Ignore it.
          if (highlight.startIndex === highlight.endIndex) { continue; }

          // If endIndex isn't present, continue to the end of the line.
          const endIndex = highlight.endIndex === undefined ?
              line.text.length :
              highlight.endIndex;

          GrAnnotation.annotateElement(
              el,
              highlight.startIndex,
              endIndex - highlight.startIndex,
              HL_CLASS);
        }
      },
    };
  },

  _createTabIndicatorLayer() {
    const show = () => this._showTabs;
    return {
      annotate(el, line) {
        // If visible tabs are disabled, do nothing.
        if (!show()) { return; }

        // Find and annotate the locations of tabs.
        const split = line.text.split('\t');
        if (!split) { return; }
        for (let i = 0, pos = 0; i < split.length - 1; i++) {
          // Skip forward by the length of the content
          pos += split[i].length;

          GrAnnotation.annotateElement(el, pos, 1,
              'style-scope gr-diff tab-indicator');

          // Skip forward by one tab character.
          pos++;
        }
      },
    };
  },

  _createTrailingWhitespaceLayer() {
    const show = function() {
      return this._showTrailingWhitespace;
    }.bind(this);

    return {
      annotate(el, line) {
        if (!show()) { return; }

        const match = line.text.match(TRAILING_WHITESPACE_PATTERN);
        if (match) {
          // Normalize string positions in case there is unicode before or
          // within the match.
          const index = GrAnnotation.getStringLength(
              line.text.substr(0, match.index));
          const length = GrAnnotation.getStringLength(match[0]);
          GrAnnotation.annotateElement(el, index, length,
              'style-scope gr-diff trailing-whitespace');
        }
      },
    };
  },

  /**
   * In pages with large diffs, creating the first comment thread can be
   * slow because nested Polymer elements (particularly
   * iron-autogrow-textarea) add style elements to the document head,
   * which, in turn, triggers a reflow on the page. Create a hidden
   * thread, attach it to the page, and remove it so the stylesheet will
   * already exist and the user's comment will be quick to load.
   * @see https://gerrit-review.googlesource.com/c/82213/
   */
  _preRenderThread() {
    const thread = document.createElement('gr-diff-comment-thread');
    thread.setAttribute('hidden', true);
    thread.addDraft();
    const parent = Polymer.dom(this.root);
    parent.appendChild(thread);
    Polymer.dom.flush();
    parent.removeChild(thread);
  },

  /**
   * @return {boolean} whether any of the lines in _groups are longer
   * than SYNTAX_MAX_LINE_LENGTH.
   */
  _anyLineTooLong() {
    return this._groups.reduce((acc, group) => {
      return acc || group.lines.reduce((acc, line) => {
        return acc || line.text.length >= SYNTAX_MAX_LINE_LENGTH;
      }, false);
    }, false);
  },

  setBlame(blame) {
    if (!this._builder || !blame) { return; }
    this._builder.setBlame(blame);
  }
});
