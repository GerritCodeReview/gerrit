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
import '../gr-linked-text/gr-linked-text.js';
import '../../../styles/shared-styles.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-formatted-text_html.js';

// eslint-disable-next-line no-unused-vars
const QUOTE_MARKER_PATTERN = /\n\s?>\s/g;
const CODE_MARKER_PATTERN = /^(`{1,3})([^`]+?)\1$/;

/** @extends PolymerElement */
class GrFormattedText extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

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

  /** @override */
  ready() {
    super.ready();
    if (this.noTrailingMargin) {
      this.classList.add('noTrailingMargin');
    }
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
    const container = dom(this.$.container);

    // Remove existing content.
    while (container.firstChild) {
      container.removeChild(container.firstChild);
    }

    // Add new content.
    for (const node of this._computeNodes(this._computeBlocks(content))) {
      if (node) container.appendChild(node);
    }
  }

  /**
   * Given a source string, parse into an array of block objects. Each block
   * has a `type` property which takes any of the following values.
   * * 'paragraph'
   * * 'quote' (Block quote.)
   * * 'pre' (Pre-formatted text.)
   * * 'list' (Unordered list.)
   * * 'code' (code blocks.)
   *
   * For blocks of type 'paragraph', 'pre' and 'code' there is a `text`
   * property that maps to a string of the block's content.
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
    const lines = content.replace(/[\s\n\r\t]+$/g, '').split('\n');

    for (let i = 0; i < lines.length; i++) {
      if (!lines[i].length) {
        continue;
      }

      if (this._isCodeMarkLine(lines[i])) {
        // handle multi-line code
        let nextI = i+1;
        while (!this._isCodeMarkLine(lines[nextI]) && nextI < lines.length) {
          nextI++;
        }

        if (this._isCodeMarkLine(lines[nextI])) {
          result.push({
            type: 'code',
            text: lines.slice(i+1, nextI).join('\n'),
          });
          i = nextI;
          continue;
        }

        // otherwise treat it as regular line and continue
        // check for other cases
      }

      if (this._isSingleLineCode(lines[i])) {
        // no guard check as _isSingleLineCode tested on the pattern
        const codeContent = lines[i].match(CODE_MARKER_PATTERN)[2];
        result.push({type: 'code', text: codeContent});
      } else if (this._isList(lines[i])) {
        let nextI = i + 1;
        while (this._isList(lines[nextI])) {
          nextI++;
        }
        result.push(this._makeList(lines.slice(i, nextI)));
        i = nextI - 1;
      } else if (this._isQuote(lines[i])) {
        let nextI = i + 1;
        while (this._isQuote(lines[nextI])) {
          nextI++;
        }
        const blockLines = lines.slice(i, nextI)
            .map(l => l.replace(/^[ ]?>[ ]?/, ''));
        result.push({
          type: 'quote',
          blocks: this._computeBlocks(blockLines.join('\n')),
        });
        i = nextI - 1;
      } else if (this._isPreFormat(lines[i])) {
        let nextI = i + 1;
        // include pre or all regular lines but stop at next new line
        while (this._isPreFormat(lines[nextI])
         || (this._isRegularLine(lines[nextI]) && lines[nextI].length)) {
          nextI++;
        }
        result.push({
          type: 'pre',
          text: lines.slice(i, nextI).join('\n'),
        });
        i = nextI - 1;
      } else {
        let nextI = i + 1;
        while (this._isRegularLine(lines[nextI])) {
          nextI++;
        }
        result.push({
          type: 'paragraph',
          text: lines.slice(i, nextI).join('\n'),
        });
        i = nextI - 1;
      }
    }

    return result;
  }

  /**
   * Take a block of comment text that contains a list, generate appropriate
   * block objects and append them to the output list.
   *
   * * Item one.
   * * Item two.
   * * item three.
   *
   * TODO(taoalpha): maybe we should also support nested list
   *
   * @param {!Array<string>} lines The block containing the list.
   */
  _makeList(lines) {
    const block = {type: 'list', items: []};
    let line;

    for (let i = 0; i < lines.length; i++) {
      line = lines[i];
      line = line.substring(1).trim();
      block.items.push(line);
    }
    return block;
  }

  _isRegularLine(line) {
    // line can not be recognized by existing patterns
    if (line === undefined) return false;
    return !this._isQuote(line) && !this._isCodeMarkLine(line)
    && !this._isSingleLineCode(line) && !this._isList(line) &&
    !this._isPreFormat(line);
  }

  _isQuote(line) {
    return line && (line.startsWith('> ') || line.startsWith(' > '));
  }

  _isCodeMarkLine(line) {
    return line && line.trim() === '```';
  }

  _isSingleLineCode(line) {
    return line && CODE_MARKER_PATTERN.test(line);
  }

  _isPreFormat(line) {
    return line && /^[ \t]/.test(line);
  }

  _isList(line) {
    return line && /^[-*] /.test(line);
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
   *
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
          if (node) bq.appendChild(node);
        }
        return bq;
      }

      if (block.type === 'code') {
        const code = document.createElement('code');
        code.textContent = block.text;
        return code;
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

      console.warn('Unrecognized type.');
      return;
    });
  }
}

customElements.define(GrFormattedText.is, GrFormattedText);
