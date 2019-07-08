// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  var QUOTE_MARKER_PATTERN = /\n\s?>\s/g;

  Polymer({
    is: 'gr-formatted-text',

    properties: {
      content: {
        type: String,
        observer: '_contentChanged',
      },
      config: Object,
      noTrailingMargin: {
        type: Boolean,
        value: false,
      },
    },

    observers: [
      '_contentOrConfigChanged(content, config)',
    ],

    ready: function() {
      if (this.noTrailingMargin) {
        this.classList.add('noTrailingMargin');
      }
    },

    /**
     * Get the plain text as it appears in the generated DOM.
     *
     * This differs from the `content` property in that it will not include
     * formatting markers such as > characters to make quotes or * and - markers
     * to make list items.
     *
     * @return {string}
     */
    getTextContent: function() {
      return this._blocksToText(this._computeBlocks(this.content));
    },

    _contentChanged: function(content) {
      // In the case where the config may not be set (perhaps due to the
      // request for it still being in flight), set the content anyway to
      // prevent waiting on the config to display the text.
      if (this.config) { return; }
      this.$.container.textContent = content;
    },

    /**
     * Given a source string, update the DOM inside #container.
     */
    _contentOrConfigChanged: function(content) {
      var container = Polymer.dom(this.$.container);

      // Remove existing content.
      while (container.firstChild) {
        container.removeChild(container.firstChild);
      }

      // Add new content.
      this._computeNodes(this._computeBlocks(content))
          .forEach(function(node) {
        container.appendChild(node);
      });
    },

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
    _computeBlocks: function(content) {
      if (!content) { return []; }

      var result = [];
      var split = content.split('\n\n');
      var p;

      for (var i = 0; i < split.length; i++) {
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
    },

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
    _makeList: function(p, out) {
      var block = null;
      var inList = false;
      var inParagraph = false;
      var lines = p.split('\n');
      var line;

      for (var i = 0; i < lines.length; i++) {
        line = lines[i];

        if (line[0] === '-' || line[0] === '*') {
          // The next line looks like a list item. If not building a list
          // already, then create one. Remove the list item marker (* or -) from
          // the line.
          if (!inList) {
            if (inParagraph) {
              // Add the finished paragraph block to the result.
              inParagraph = false;
              out.push(block);
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
      if (block != null) {
        out.push(block);
      }
    },

    _makeQuote: function(p) {
      var quotedLines = p
          .split('\n')
          .map(function(l) { return l.replace(/^[ ]?>[ ]?/, ''); })
          .join('\n');
      return {
        type: 'quote',
        blocks: this._computeBlocks(quotedLines),
      };
    },

    _isQuote: function(p) {
      return p.indexOf('> ') === 0 || p.indexOf(' > ') === 0;
    },

    _isPreFormat: function(p) {
      return p.indexOf('\n ') !== -1 || p.indexOf('\n\t') !== -1 ||
          p.indexOf(' ') === 0 || p.indexOf('\t') === 0;
    },

    _isList: function(p) {
      return p.indexOf('\n- ') !== -1 || p.indexOf('\n* ') !== -1 ||
          p.indexOf('- ') === 0 || p.indexOf('* ') === 0;
    },

    _makeLinkedText: function(content, isPre) {
      var text = document.createElement('gr-linked-text');
      text.config = this.config;
      text.content = content;
      text.pre = true;
      if (isPre) {
        text.classList.add('pre');
      }
      return text;
    },

    /**
     * Map an array of block objects to an array of DOM nodes.
     * @param  {!Array<!Object>} blocks
     * @return {!Array<!HTMLElement>}
     */
    _computeNodes: function(blocks) {
      return blocks.map(function(block) {
        if (block.type === 'paragraph') {
          var p = document.createElement('p');
          p.appendChild(this._makeLinkedText(block.text));
          return p;
        }

        if (block.type === 'quote') {
          var bq = document.createElement('blockquote');
          this._computeNodes(block.blocks).forEach(function(node) {
            bq.appendChild(node);
          });
          return bq;
        }

        if (block.type === 'pre') {
          return this._makeLinkedText(block.text, true);
        }

        if (block.type === 'list') {
          var ul = document.createElement('ul');
          block.items.forEach(function(item) {
            var li = document.createElement('li');
            li.appendChild(this._makeLinkedText(item));
            ul.appendChild(li);
          }.bind(this));
          return ul;
        }
      }.bind(this));
    },

    _blocksToText: function(blocks) {
      return blocks.map(function(block) {
        if (block.type === 'paragraph' || block.type === 'pre') {
          return block.text;
        }
        if (block.type === 'quote') {
          return this._blocksToText(block.blocks);
        }
        if (block.type === 'list') {
          return block.items.join('\n');
        }
      }.bind(this)).join('\n\n');
    },
  });
})();
