/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
(function() {
  'use strict';

  /**
   * gr-highlight-text is a component that takes a highlightText and
   * then highlights all appearance of that text in its content.
   *
   * @example
   *
   * // template to use gr-highlight-text directive.
   * `
   * <gr-highlight-text highlight-text='[[test]]'>
   *   <div>
   *     <p>test highlight test</p>
   *   </div>
   * </gr-highlight-text>
   * `
   *
   * // after renderred, the `test` in the content will be rendered
   * // in `bold`.
   */

  Polymer({
    is: 'gr-highlight-text',

    properties: {
      /** The text to highlight within the content. */
      highlightText: {
        type: String,
        value: '',
        observer: '_updateHighlights',
      },

      /**
       * Internal state to track all highlighted elements,
       * will be used to undo all highlights if highlightText changed.
       *
       * All highlight elements will be generated as
       * `<strong>highlightText</strong>` from `_highlight` method.
       */
      _highlights: {
        type: Array,
        value() {
          return [];
        },
      },
    },

    attached() {
      this._updateHighlights();
    },

    _removeAllHighlights() {
      this._highlights = this._highlights || [];
      for (const hl of this._highlights) {
        const parent = hl.parentNode;
        hl.replaceWith(hl.childNodes[0]);

        // Normalize parent to avoid unnecessary multiple text nodes.
        if (parent) {
          parent.normalize();
        }
      }
      this._highlights = [];
    },

    // Returns the HTML nodes to be highlighted.
    _getSlottedNodes() {
      return (this.$.highlightContainer.assignedNodes &&
        this.$.highlightContainer.assignedNodes()) ||
        // Note: remove after moved to polymer 2.
        Polymer.dom(this).childNodes;
    },

    _updateHighlights() {
      this._removeAllHighlights();

      if (!this.highlightText) return;

      const nodes = Array.from(this._getSlottedNodes());

      // Record the node traversal state in case matches across nodes.
      const nodeTraversalState = {
        matchedTextInPreviousNode: '',
        highlightCallbackForPreviousNode: null,
      };
      this._highlightElement({childNodes: nodes}, nodeTraversalState);
    },

    /**
     * Recursively highlight elements in the content.
     *
     * @param {*} node - An HTMLElement to be processed
     * @param {Object} nodeTraversalState - Record on match state from previous element.
     */
    _highlightElement(node, nodeTraversalState) {
      const childNodes = Array.from(node.childNodes);
      for (const node of childNodes) {
        if (node instanceof HTMLElement) {
          this._highlightElement(node, nodeTraversalState);
        } else if (node instanceof Text) {
          this._highlightTextNode(node, nodeTraversalState);
        }
      }
    },

    /**
     * Handle the highlight on TextNode.
     *
     * @param {Text} textNode
     * @param {Object} nodeTraversalState
     */
    _highlightTextNode(textNode, nodeTraversalState) {
      const {
        matchedTextInPreviousNode,
        highlightCallbackForPreviousNode,
      } = nodeTraversalState;
      let matchedText = matchedTextInPreviousNode;
      const text = (textNode.textContent || '').toLowerCase();
      const highlightText = this.highlightText.toLowerCase();
      let matchedTextInCurrentNode = '';
      for (let i = 0; i < text.length; i++) {
        while (i < text.length &&
          text[i] === highlightText[matchedText.length]
        ) {
          matchedText += text[i];
          matchedTextInCurrentNode += text[i];
          i++;
        }

        if (matchedText === highlightText) {
          if (highlightCallbackForPreviousNode) {
            highlightCallbackForPreviousNode();
          }

          // Highlight current part.
          const tailNode = this._highlight(
              textNode,
              i,
              matchedTextInCurrentNode.length
          );

          // Start a new search with the tail node.
          return this._highlightTextNode(tailNode, nodeTraversalState);
        } else if (i < text.length) {
          // Reset the match if its not reaching the end.
          matchedText = '';
          matchedTextInCurrentNode = '';
        }
      }

      nodeTraversalState.matchedTextInPreviousNode = matchedText;
      if (matchedText) {
        nodeTraversalState.highlightCallbackForPreviousNode = () => {
          if (highlightCallbackForPreviousNode) {
            highlightCallbackForPreviousNode();
          }

          this._highlight(
              textNode,
              text.length,
              matchedTextInCurrentNode.length
          );
        };
      } else {
        nodeTraversalState.highlightCallbackForPreviousNode = null;
      }
    },

    /**
     * Highlight based on previous state and current matches.
     *
     * @param {Text} textNode
     * @param {number} endIndex
     * @param {number} highlightLength
     *
     * @example
     *
     * // see if highlightText is 'query' (length is 5);
     * // now we are handling following Text node
     * // the endIndex will be determined before calling _highlight
     * const textNode = document.createTextNode('test query highlight');
     * _highlight(textNode, 10, 5);
     *
     * // The text node will be split into two parts,
     * // and textToHighlight will be wrapped in highlight container
     * // the end result should look like:
     * `
     * test <strong class='highlight-text'>query</strong> highlight
     * `
     *
     * // also the element will be added to _highlights
     */
    _highlight(textNode, endIndex, highlightLength) {
      const nodeWithMatch = util.splitTextNode(
          textNode, endIndex - highlightLength
      );
      const tailNode = util.splitTextNode(nodeWithMatch, highlightLength);
      const hl = document.createElement('strong');
      hl.classList.add('highlight-text');
      nodeWithMatch.replaceWith(hl);
      hl.appendChild(nodeWithMatch);

      this._highlights.push(hl);
      return tailNode;
    },
  });
})();