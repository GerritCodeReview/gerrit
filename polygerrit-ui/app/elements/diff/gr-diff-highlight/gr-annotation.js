// Copyright (C) 2016 The Android Open Source Project
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
  if (window.GrAnnotation) { return; }

  // TODO(wyatta): refactor this to be <MARK> rather than <HL>.
  var ANNOTATION_TAG = 'HL';

  // Astral code point as per https://mathiasbynens.be/notes/javascript-unicode
  var REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

  var GrAnnotation = {

    /**
     * The DOM API textContent.length calculation is broken when the text
     * contains Unicode. See https://mathiasbynens.be/notes/javascript-unicode .
     * @param  {Text} A text node.
     * @return {Number} The length of the text.
     */
    getLength: function(node) {
      return node.textContent.replace(REGEX_ASTRAL_SYMBOL, '_').length;
    },

    /**
     * Wraps node in annotation tag with cssClass, replacing the node in DOM.
     *
     * @return {!Element} Wrapped node.
     */
    wrapInHighlight: function(node, cssClass) {
      var hl;
      if (node.tagName === ANNOTATION_TAG) {
        hl = node;
        hl.classList.add(cssClass);
      } else {
        hl = document.createElement(ANNOTATION_TAG);
        hl.className = cssClass;
        Polymer.dom(node.parentElement).replaceChild(hl, node);
        hl.appendChild(node);
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
    splitAndWrapInHighlight: function(node, offset, cssClass, opt_firstPart) {
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
    splitNode: function(element, offset) {
      if (element instanceof Text) {
        return this.splitTextNode(element, offset);
      }
      var tail = element.cloneNode(false);
      element.parentElement.insertBefore(tail, element.nextSibling);
      // Skip nodes before offset.
      var node = element.firstChild;
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
    splitTextNode: function(node, offset) {
      if (node.textContent.match(REGEX_ASTRAL_SYMBOL)) {
        // TODO (viktard): Polyfill Array.from for IE10.
        var head = Array.from(node.textContent);
        var tail = head.splice(offset);
        var parent = node.parentElement;
        var headNode = document.createTextNode(head.join(''));
        parent.replaceChild(headNode, node);
        var tailNode = document.createTextNode(tail.join(''));
        parent.insertBefore(tailNode, headNode.nextSibling);
        return tailNode;
      } else {
        return node.splitText(offset);
      }
    },
  };

  window.GrAnnotation = GrAnnotation;
})(window);

