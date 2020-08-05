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

// Astral code point as per https://mathiasbynens.be/notes/javascript-unicode
const REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

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
export function normalize(range: Range) {
  const startContainer = _getContentTextParent(range.startContainer);
  const startOffset = range.startOffset +
      _getTextOffset(startContainer, range.startContainer);
  const endContainer = _getContentTextParent(range.endContainer);
  const endOffset = range.endOffset + _getTextOffset(endContainer,
      range.endContainer);
  return {
    startContainer,
    startOffset,
    endContainer,
    endOffset,
  };
}

function _getContentTextParent(target: Node): Node {
  if (!target.parentElement) return target;

  let element: HTMLElement;
  if (target.nodeType === Node.ELEMENT_NODE) {
    element = target as HTMLElement;
  } else {
    element = target.parentElement;
  }
  while (element && !element.classList.contains('contentText')) {
    if (element.parentElement === null) {
      return target;
    }
    element = element.parentElement;
  }
  return element;
}

/**
 * Gets the character offset of the child within the parent.
 * Performs a synchronous in-order traversal from top to bottom of the node
 * element, counting the length of the syntax until child is found.
 *
 * @param node The root DOM element to be searched through.
 * @param child The child element being searched for.
 */
// TODO(brohlfs): Only export for test.
export function _getTextOffset(node: Node, child: Node): number {
  let count = 0;
  let stack = [node];
  while (stack.length) {
    const n = stack.pop();
    if (n === child) {
      break;
    }
    if (n && n.childNodes && n.childNodes.length !== 0) {
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
function _getLength(node?: Node) {
  return node && node.textContent
    ? node.textContent.replace(REGEX_ASTRAL_SYMBOL, '_').length
    : 0;
}
