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
  if (window.GrRangeNormalizer) { return; }

  // Astral code point as per https://mathiasbynens.be/notes/javascript-unicode
  var REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

  var GrRangeNormalizer = {
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
    normalize: function(range) {
      var startContainer = this._getContentTextParent(range.startContainer);
      var startOffset = range.startOffset + this._getTextOffset(startContainer,
          range.startContainer);
      var endContainer = this._getContentTextParent(range.endContainer);
      var endOffset = range.endOffset + this._getTextOffset(endContainer,
          range.endContainer);
      return {
        startContainer: startContainer,
        startOffset: startOffset,
        endContainer: endContainer,
        endOffset: endOffset,
      };
    },

    _getContentTextParent: function(target) {
      var element = target;
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
    _getTextOffset: function(node, child) {
      var count = 0;
      var stack = [node];
      while (stack.length) {
        var n = stack.pop();
        if (n === child) {
          break;
        }
        if (n.childNodes && n.childNodes.length !== 0) {
          var arr = [];
          for (var i = 0; i < n.childNodes.length; i++) {
            arr.push(n.childNodes[i]);
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
     * @param {Text} A text node.
     * @return {Number} The length of the text.
     */
    _getLength: function(node) {
      return node.textContent.replace(REGEX_ASTRAL_SYMBOL, '_').length;
    },
  };

  window.GrRangeNormalizer = GrRangeNormalizer;
})(window);
