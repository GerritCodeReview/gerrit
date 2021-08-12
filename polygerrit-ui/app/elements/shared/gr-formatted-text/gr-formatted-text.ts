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
import '../gr-linked-text/gr-linked-text';
import {CommentLinks} from '../../../types/common';
import {appContext} from '../../../services/app-context';
import {GrLitElement} from '../../lit/gr-lit-element';
import {css, customElement, html, property} from 'lit-element';
import {classMap} from 'lit-html/directives/class-map';

const CODE_MARKER_PATTERN = /^(`{1,3})([^`]+?)\1$/;

interface Block {
  type: string;
  text?: string;
  blocks?: Block[];
  items?: string[];
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-formatted-text': GrFormattedText;
  }
}
@customElement('gr-formatted-text')
export class GrFormattedText extends GrLitElement {
  @property({type: String})
  content?: string;

  @property({type: Object})
  config?: CommentLinks;

  @property({type: Boolean, reflect: true})
  noTrailingMargin = false;
  
  @property({type: Boolean})
  monospace = false;

  private readonly reporting = appContext.reportingService;

  static get styles() {
    return [
      css`
        :host {
          display: block;
          font-family: var(--font-family);
        }
        .monospace {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
        }
        p,
        ul,
        code,
        blockquote,
        gr-linked-text.pre {
          margin: 0 0 var(--spacing-m) 0;
        }
        p,
        ul,
        code,
        blockquote {
          max-width: var(--gr-formatted-text-prose-max-width, none);
        }
        :host([noTrailingMargin]) p:last-child,
        :host([noTrailingMargin]) ul:last-child,
        :host([noTrailingMargin]) blockquote:last-child,
        :host([noTrailingMargin]) gr-linked-text.pre:last-child {
          margin: 0;
        }
        code,
        blockquote {
          border-left: 1px solid #aaa;
          padding: 0 var(--spacing-m);
        }
        code {
          display: block;
          white-space: pre-wrap;
          color: var(--deemphasized-text-color);
        }
        li {
          list-style-type: disc;
          margin-left: var(--spacing-xl);
        }
        code,
        gr-linked-text.pre {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-code);
          /* usually 16px = 12px + 4px */
          line-height: calc(var(--font-size-code) + var(--spacing-s));
        }
      `,
    ];
  }

  render() {
    const nodes = this._computeNodes(this._computeBlocks(this.content));
    return html`<div
      id="container"
      class="${classMap({monospace: this.monospace})}"
    >
      ${nodes}
    </div>`;

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
   */
  _computeBlocks(content?: string): Block[] {
    if (!content) return [];

    const result = [];
    const lines = content.replace(/[\s\n\r\t]+$/g, '').split('\n');

    for (let i = 0; i < lines.length; i++) {
      if (!lines[i].length) {
        continue;
      }

      if (this._isCodeMarkLine(lines[i])) {
        // handle multi-line code
        let nextI = i + 1;
        while (!this._isCodeMarkLine(lines[nextI]) && nextI < lines.length) {
          nextI++;
        }

        if (this._isCodeMarkLine(lines[nextI])) {
          result.push({
            type: 'code',
            text: lines.slice(i + 1, nextI).join('\n'),
          });
          i = nextI;
          continue;
        }

        // otherwise treat it as regular line and continue
        // check for other cases
      }

      if (this._isSingleLineCode(lines[i])) {
        // no guard check as _isSingleLineCode tested on the pattern
        const codeContent = lines[i].match(CODE_MARKER_PATTERN)![2];
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
        const blockLines = lines
          .slice(i, nextI)
          .map(l => l.replace(/^[ ]?>[ ]?/, ''));
        result.push({
          type: 'quote',
          blocks: this._computeBlocks(blockLines.join('\n')),
        });
        i = nextI - 1;
      } else if (this._isPreFormat(lines[i])) {
        let nextI = i + 1;
        // include pre or all regular lines but stop at next new line
        while (
          this._isPreFormat(lines[nextI]) ||
          (this._isRegularLine(lines[nextI]) &&
            !this._isWhitespaceLine(lines[nextI]) &&
            lines[nextI].length)
        ) {
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
   * @param lines The block containing the list.
   */
  _makeList(lines: string[]) {
    const items = [];
    for (let i = 0; i < lines.length; i++) {
      let line = lines[i];
      line = line.substring(1).trim();
      items.push(line);
    }
    return {type: 'list', items};
  }

  _isRegularLine(line: string) {
    // line can not be recognized by existing patterns
    if (line === undefined) return false;
    return (
      !this._isQuote(line) &&
      !this._isCodeMarkLine(line) &&
      !this._isSingleLineCode(line) &&
      !this._isList(line) &&
      !this._isPreFormat(line)
    );
  }

  _isQuote(line: string) {
    return line && (line.startsWith('> ') || line.startsWith(' > '));
  }

  _isCodeMarkLine(line: string) {
    return line && line.trim() === '```';
  }

  _isSingleLineCode(line: string) {
    return line && CODE_MARKER_PATTERN.test(line);
  }

  _isPreFormat(line: string) {
    return line && /^[ \t]/.test(line) && !this._isWhitespaceLine(line);
  }

  _isList(line: string) {
    return line && /^[-*] /.test(line);
  }

  _isWhitespaceLine(line: string) {
    return line && /^\s+$/.test(line);
  }

  _makeLinkedText(content = '', isPre?: boolean) {
    const text = document.createElement('gr-linked-text');
    text.config = this.config;
    text.content = content;
    text.pre = true;
    if (isPre) {
      text.classList.add('pre');
    }
    return text;
  }

  /**
   * Map an array of block objects to an array of DOM nodes.
   */
  _computeNodes(blocks: Block[]): HTMLElement[] {
    return blocks.map(block => {
      if (block.type === 'paragraph') {
        const p = document.createElement('p');
        p.appendChild(this._makeLinkedText(block.text));
        return p;
      }

      if (block.type === 'quote') {
        const bq = document.createElement('blockquote');
        for (const node of this._computeNodes(block.blocks || [])) {
          if (node) bq.appendChild(node);
        }
        return bq;
      }

      if (block.type === 'code') {
        const code = document.createElement('code');
        code.textContent = block.text || '';
        return code;
      }

      if (block.type === 'pre') {
        return this._makeLinkedText(block.text, true);
      }

      if (block.type === 'list') {
        const ul = document.createElement('ul');
        const items = block.items || [];
        for (const item of items) {
          const li = document.createElement('li');
          li.appendChild(this._makeLinkedText(item));
          ul.appendChild(li);
        }
        return ul;
      }

      this.reporting.error(new Error(`Unrecognized block type: ${block.type}`));
      return document.createElement('span');
    });
  }
}
