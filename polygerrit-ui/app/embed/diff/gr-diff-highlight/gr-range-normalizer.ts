/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Astral code point as per https://mathiasbynens.be/notes/javascript-unicode
const REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

export interface NormalizedRange {
  endContainer: Node;
  endOffset: number;
  startContainer: Node;
  startOffset: number;
}

/**
 * Remap DOM range to whole lines of a diff if necessary. If the start or
 * end containers are DOM elements that are singular pieces of syntax
 * highlighting, the containers are remapped to the .contentText divs that
 * contain the entire line of code.
 *
 * @param range - the standard DOM selector range.
 * @return A modified version of the range that correctly accounts
 *     for syntax highlighting.
 */
export function normalize(range: Range): NormalizedRange {
  const startContainer = _getContentTextParent(range.startContainer);
  const startOffset =
    range.startOffset + _getTextOffset(startContainer, range.startContainer);
  const endContainer = _getContentTextParent(range.endContainer);
  const endOffset =
    range.endOffset + _getTextOffset(endContainer, range.endContainer);
  return {
    startContainer,
    startOffset,
    endContainer,
    endOffset,
  };
}

function _getContentTextParent(target: Node): Node {
  if (!target.parentElement) return target;

  let element: Element | null;
  if (target instanceof Element) {
    element = target;
  } else {
    element = target.parentElement;
  }

  while (element && !element.classList.contains('contentText')) {
    if (element.parentElement === null) {
      return target;
    }
    element = element.parentElement;
  }
  return element ? element : target;
}

/**
 * Gets the character offset of the child within the parent.
 * Performs a synchronous in-order traversal from top to bottom of the node
 * element, counting the length of the syntax until child is found.
 *
 * @param node The root DOM element to be searched through.
 * @param child The child element being searched for.
 */
// TODO(TS): Only export for test.
export function _getTextOffset(node: Node | null, child: Node): number {
  let count = 0;
  let stack = [node];
  while (stack.length) {
    const n = stack.pop();
    if (n === child) {
      break;
    }
    if (n?.childNodes && n.childNodes.length !== 0) {
      const arr = [];
      for (const childNode of n.childNodes) {
        arr.push(childNode);
      }
      arr.reverse();
      stack = stack.concat(arr);
    } else {
      count += _getLength(n);
    }
  }
  return count;
}

/**
 * The DOM API textContent.length calculation is broken when the text
 * contains Unicode. See https://mathiasbynens.be/notes/javascript-unicode .
 *
 * @param node A text node.
 * @return The length of the text.
 */
function _getLength(node?: Node | null) {
  return node && node.textContent
    ? node.textContent.replace(REGEX_ASTRAL_SYMBOL, '_').length
    : 0;
}
