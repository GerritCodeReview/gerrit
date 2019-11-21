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
     * Annotates the [offset, offset+length) text segment in the parent with the
     * element definition provided as arguments.
     *
     * @param {!Element} parent the node whose contents will be annotated.
     * @param {number} offset the 0-based offset from which the annotation will
     *   start.
     * @param {number} length of the annotated text.
     * @param {GrAnnotation.ElementSpec} elementSpec the spec to create the
     *   annotating element.
     */
    annotateWithElement(parent, offset, length, {tagName, attributes = {}}) {
      let childNodes;

      if (parent instanceof Element) {
        childNodes = Array.from(parent.childNodes);
      } else if (parent instanceof Text) {
        childNodes = [parent];
        parent = parent.parentNode;
      } else {
        return;
      }

      const nestedNodes = [];
      for (let node of childNodes) {
        const initialNodeLength = this.getLength(node);
        // If the current node is completely before the offset.
        if (offset > 0 && initialNodeLength <= offset) {
          offset -= initialNodeLength;
          continue;
        }

        if (offset > 0) {
          node = this.splitNode(node, offset);
          offset = 0;
        }
        if (this.getLength(node) > length) {
          this.splitNode(node, length);
        }
        nestedNodes.push(node);

        length -= this.getLength(node);
        if (!length) break;
      }

      const wrapper = document.createElement(tagName);
      const sanitizer = window.Polymer.sanitizeDOMValue;
      for (const [name, value] of Object.entries(attributes)) {
        wrapper.setAttribute(
            name, sanitizer
              ? sanitizer(value, name, 'attribute', wrapper)
              : value);
      }
      for (const inner of nestedNodes) {
        parent.replaceChild(wrapper, inner);
        wrapper.appendChild(inner);
      }
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

  /**
   * Data used to construct an element.
   *
   * @typedef {{
   *   tagName: string,
   *   attributes: (!Object<string, *>|undefined)
   * }}
   */
  GrAnnotation.ElementSpec;

  window.GrAnnotation = GrAnnotation;
})(window);
