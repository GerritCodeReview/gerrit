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
(function() {
  'use strict';

  const REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

  Polymer({
    is: 'gr-highlight-text',
    _legacyUndefinedCheck: true,

    properties: {
      highlightText: {
        type: String,
        value: '',
        observer: '_update',
      },

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
              highlightText.substr(
                  existingPrefix.length,
                  highlightText.length
              )
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

    _highlight(textNode, currentIndex, highlightText) {
      const nodeWithMatch = this.splitTextNode(
          textNode,
          currentIndex - highlightText.length
      );
      const tailNode = this.splitTextNode(nodeWithMatch, highlightText.length);
      const hl = document.createElement('strong');
      hl.classList.add('highlight-text');
      nodeWithMatch.replaceWith(hl);
      hl.appendChild(nodeWithMatch);

      this._highlights.push(hl);
      return tailNode;
    },

    /**
     * Node.prototype.splitText Unicode-valid alternative.
     *
     * DOM Api for splitText() is broken for Unicode:
     * https://mathiasbynens.be/notes/javascript-unicode
     *
     */
    splitTextNode(node, offset) {
      if (node.textContent.match(REGEX_ASTRAL_SYMBOL)) {
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
  });
})();