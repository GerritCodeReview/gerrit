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
    _legacyUndefinedCheck: true,

    properties: {
      /** The text to highlight within the content. */
      highlightText: {
        type: String,
        value: '',
        observer: '_update',
      },

      /**
       * Internal state to track all highlighted elements,
       * will be used to undo all highlights if highlightText changed.
       */
      _highlights: {
        type: Array,
        value() {
          return [];
        },
      },
    },

    attached() {
      this._update();
    },

    _cleanUp() {
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

    _retrieveSlotNodes() {
      return (this.$.highlightContainer.assignedNodes &&
        this.$.highlightContainer.assignedNodes()) ||
        // Note: remove after moved to polymer 2.
        Polymer.dom(this).childNodes;
    },

    _update() {
      // Unhighlight all existing highlights.
      this._cleanUp();

      // Skip highlighting process if its empty.
      if (!this.highlightText) return;

      // Retrieve the slot content.
      const nodes = Array.from(this._retrieveSlotNodes());

      // Keep records on current matched part against the highlightText.
      const matchedPartial = {prefix: '', highlight: null};
      this._annoteElement({childNodes: nodes}, matchedPartial);
    },

    /**
     * Recursively highlight elements in the content.
     *
     * @param {HTMLElement} node - An HTMLElement to be processed
     * @param {Object} matchedPartial - Record on match state from previous element.
     */
    _annoteElement(node, matchedPartial) {
      const childNodes = Array.from(node.childNodes);
      for (const node of childNodes) {
        if (node instanceof HTMLElement) {
          this._annoteElement(node, matchedPartial);
        } else if (node instanceof Text) {
          this._annoteText(node, matchedPartial);
        }
      }
    },

    /**
     * Handle the highlight on TextNode with previous match state.
     *
     * @param {Text} textNode
     * @param {Object} matchedPartial
     */
    _annoteText(textNode, matchedPartial) {
      const {prefix: existingPrefix, highlight} = matchedPartial;
      let prefix = existingPrefix;
      const text = (textNode.textContent || '').toLowerCase();
      const highlightText = this.highlightText.toLowerCase();
      let skipLen = prefix.length;
      let curMatch = '';
      for (let i = 0; i < text.length; i++) {
        while (i < text.length && text[i] === highlightText[skipLen]) {
          prefix += text[i];
          curMatch += text[i];
          i++;
          skipLen++;
        }

        // Find a match.
        if (prefix === highlightText) {
          // Highlight the prefix part.
          if (highlight) highlight();
          // Highlight current part.
          const tailNode = this._highlight(
              textNode,
              i,
              curMatch
          );

          // Start a new search with the tail node.
          return this._annoteText(tailNode, matchedPartial);
        } else if (i < text.length) {
          // Reset the match if its not reaching the end.
          prefix = '';
          curMatch = '';
          skipLen = 0;
        }
      }

      // Update matchedPartial to pass along.
      matchedPartial.prefix = prefix;
      if (prefix) {
        matchedPartial.highlight = () => {
          // highlight previous one.
          if (highlight) highlight();

          // Highlight the matched part.
          this._highlight(textNode, text.length, curMatch);
        };
      } else {
        matchedPartial.highlight = null;
      }
    },

    /**
     * Highlight based on previous state and current matches.
     *
     * @param {Text} textNode
     * @param {Number} endIndex
     * @param {String} textTohighlight
     *
     * @example
     *
     * // see if highlightText is 'query';
     * // now we are handling following Text node
     * // the endIndex will be determined before calling _highlight
     * const textNode = document.createTextNode('test query highlight');
     * _highlight(textNode, 10, 'query');
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
    _highlight(textNode, endIndex, textToHighlight) {
      const nodeWithMatch = util.splitTextNode(
          textNode,
          endIndex - textToHighlight.length
      );
      const tailNode = util.splitTextNode(
          nodeWithMatch,
          textToHighlight.length
      );
      const hl = document.createElement('strong');
      hl.classList.add('highlight-text');
      nodeWithMatch.replaceWith(hl);
      hl.appendChild(nodeWithMatch);

      this._highlights.push(hl);
      return tailNode;
    },
  });
})();