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

  // eslint-disable-next-line no-unused-vars
  const QUOTE_MARKER_PATTERN = /\n\s?>\s/g;

  class GrFormattedText extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-formatted-text'; }

    static get properties() {
      return {
        content: {
          type: String,
          observer: '_contentChanged',
        },
        config: Object,
        noTrailingMargin: {
          type: Boolean,
          value: false,
        },
      };
    }

    static get observers() {
      return [
        '_contentOrConfigChanged(content, config)',
      ];
    }

    ready() {
      super.ready();
      if (this.noTrailingMargin) {
        this.classList.add('noTrailingMargin');
      }
    }

    /**
     * Get the plain text as it appears in the generated DOM.
     *
     * This differs from the `content` property in that it will not include
     * formatting markers such as > characters to make quotes or * and - markers
     * to make list items.
     *
     * @return {string}
     */
    getTextContent() {
      return this._blocksToText(this._computeBlocks(this.content));
    }

    _contentChanged(content) {
      // In the case where the config may not be set (perhaps due to the
      // request for it still being in flight), set the content anyway to
      // prevent waiting on the config to display the text.
      if (this.config) { return; }
      this._contentOrConfigChanged(content);
    }

    /**
     * Given a source string, update the DOM inside #container.
     */
    _contentOrConfigChanged(content) {
      const container = Polymer.dom(this.$.container);

      // Remove existing content.
      while (container.firstChild) {
        container.removeChild(container.firstChild);
      }

      // Add new content.
      for (const node of this._computeNodes(this._computeBlocks(content))) {
        container.appendChild(node);
      }
    }

    /**
     * Given a source string, parse into an array of block objects. Each block
     * has a `type` property which takes any of the follwoing values.
     * * 'paragraph'
     * * 'quote' (Block quote.)
     * * 'pre' (Pre-formatted text.)
     * * 'list' (Unordered list.)
     *
     * For blocks of type 'paragraph' and 'pre' there is a `text` property that
     * maps to a string of the block's content.
     *
     * For blocks of type 'list', there is an `items` property that maps to a
     * list of strings representing the list items.
     *
     * For blocks of type 'quote', there is a `blocks` property that maps to a
     * list of blocks contained in the quote.
     *
     * NOTE: Strings appearing in all block objects are NOT escaped.
     *
     * @param {string} content
     * @return {!Array<!Object>}
     */
    _computeBlocks(content) {
      if (!content) { return []; }

      const result = [];
      const split = content.split('\n\n');
      let p;

      for (let i = 0; i < split.length; i++) {
        p = split[i];
        if (!p.length) { continue; }

        if (this._isQuote(p)) {
          result.push(this._makeQuote(p));
        } else if (this._isPreFormat(p)) {
          result.push({type: 'pre', text: p});
        } else if (this._isList(p)) {
          this._makeList(p, result);
        } else {
          result.push({type: 'paragraph', text: p});
        }
      }
      return result;
    }

    /**
     * Take a block of comment text that contains a list and potentially
     * a paragraph (but does not contain blank lines), generate appropriate
     * block objects and append them to the output list.
     *
     * In simple cases, this will generate a single list block. For example, on
     * the following input.
     *
     *    * Item one.
     *    * Item two.
     *    * item three.
     *
     * However, if the list starts with a paragraph, it will need to also
     * generate that paragraph. Consider the following input.
     *
     *    A bit of text describing the context of the list:
     *    * List item one.
     *    * List item two.
     *    * Et cetera.
     *
     * In this case, `_makeList` generates a paragraph block object
     * containing the non-bullet-prefixed text, followed by a list block.
     *
     * @param {!string} p The block containing the list (as well as a
     *   potential paragraph).
     * @param {!Array<!Object>} out The list of blocks to append to.
     */
    _makeList(p, out) {
      let block = null;
      let inList = false;
      let inParagraph = false;
      const lines = p.split('\n');
      let line;

      for (let i = 0; i < lines.length; i++) {
        line = lines[i];

        if (line[0] === '-' || line[0] === '*') {
          // The next line looks like a list item. If not building a list
          // already, then create one. Remove the list item marker (* or -) from
          // the line.
          if (!inList) {
            if (inParagraph) {
              // Add the finished paragraph block to the result.
              inParagraph = false;
              if (block !== null) {
                out.push(block);
              }
            }
            inList = true;
            block = {type: 'list', items: []};
          }
          line = line.substring(1).trim();
        } else if (!inList) {
          // Otherwise, if a list has not yet been started, but the next line
          // does not look like a list item, then add the line to a paragraph
          // block. If a paragraph block has not yet been started, then create
          // one.
          if (!inParagraph) {
            inParagraph = true;
            block = {type: 'paragraph', text: ''};
          } else {
            block.text += ' ';
          }
          block.text += line;
          continue;
        }
        block.items.push(line);
      }
      if (block !== null) {
        out.push(block);
      }
    }

    _makeQuote(p) {
      const quotedLines = p
          .split('\n')
          .map(l => l.replace(/^[ ]?>[ ]?/, ''))
          .join('\n');
      return {
        type: 'quote',
        blocks: this._computeBlocks(quotedLines),
      };
    }

    _isQuote(p) {
      return p.startsWith('> ') || p.startsWith(' > ');
    }

    _isPreFormat(p) {
      return p.includes('\n ') || p.includes('\n\t') ||
          p.startsWith(' ') || p.startsWith('\t');
    }

    _isList(p) {
      return p.includes('\n- ') || p.includes('\n* ') ||
          p.startsWith('- ') || p.startsWith('* ');
    }

    /**
     * @param {string} content
     * @param {boolean=} opt_isPre
     */
    _makeLinkedText(content, opt_isPre) {
      const text = document.createElement('gr-linked-text');
      text.config = this.config;
      text.content = content;
      text.pre = true;
      if (opt_isPre) {
        text.classList.add('pre');
      }
      return text;
    }

    /**
     * Map an array of block objects to an array of DOM nodes.
     * @param  {!Array<!Object>} blocks
     * @return {!Array<!HTMLElement>}
     */
    _computeNodes(blocks) {
      return blocks.map(block => {
        if (block.type === 'paragraph') {
          const p = document.createElement('p');
          p.appendChild(this._makeLinkedText(block.text));
          return p;
        }

        if (block.type === 'quote') {
          const bq = document.createElement('blockquote');
          for (const node of this._computeNodes(block.blocks)) {
            bq.appendChild(node);
          }
          return bq;
        }

        if (block.type === 'pre') {
          return this._makeLinkedText(block.text, true);
        }

        if (block.type === 'list') {
          const ul = document.createElement('ul');
          for (const item of block.items) {
            const li = document.createElement('li');
            li.appendChild(this._makeLinkedText(item));
            ul.appendChild(li);
          }
          return ul;
        }
      });
    }

    _blocksToText(blocks) {
      return blocks.map(block => {
        if (block.type === 'paragraph' || block.type === 'pre') {
          return block.text;
        }
        if (block.type === 'quote') {
          return this._blocksToText(block.blocks);
        }
        if (block.type === 'list') {
          return block.items.join('\n');
        }
      }).join('\n\n');
    }
  }

  customElements.define(GrFormattedText.is, GrFormattedText);
})();
