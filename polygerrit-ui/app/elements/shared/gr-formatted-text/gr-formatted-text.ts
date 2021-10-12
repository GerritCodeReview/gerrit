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
import {LitElement, css, html, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators';

const CODE_MARKER_PATTERN = /^(`{1,3})([^`]+?)\1$/;

export type Block = ListBlock | QuoteBlock | TextBlock;
export interface ListBlock {
  type: 'list';
  items: string[];
}
export interface QuoteBlock {
  type: 'quote';
  blocks: Block[];
}
export interface TextBlock {
  type: 'paragraph' | 'code' | 'pre';
  text: string;
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-formatted-text': GrFormattedText;
  }
}
@customElement('gr-formatted-text')
export class GrFormattedText extends LitElement {
  @property({type: String})
  content?: string;

  @property({type: Object})
  config?: CommentLinks;

  @property({type: Boolean, reflect: true})
  noTrailingMargin = false;

  static get styles() {
    return [
      css`
        :host {
          display: block;
          font-family: var(--font-family);
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

  override render() {
    if (!this.content) return;
    const blocks = this._computeBlocks(this.content);
    return html`${blocks.map(block => this.renderBlock(block))}`;
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
  _computeBlocks(content: string): Block[] {
    const result: Block[] = [];
    const lines = content.replace(/[\s\n\r\t]+$/g, '').split('\n');

    for (let i = 0; i < lines.length; i++) {
      if (!lines[i].length) {
        continue;
      }

      if (this.isCodeMarkLine(lines[i])) {
        const startOfCode = i + 1;
        const endOfCode = this.getEndOfSection(
          lines,
          startOfCode,
          line => !this.isCodeMarkLine(line)
        );
        // If the code extends to the end then there is no closing``` and the
        // opening``` should not be counted as a multiline code block.
        const lineAfterCode = lines[endOfCode];
        if (lineAfterCode && this.isCodeMarkLine(lineAfterCode)) {
          result.push({
            type: 'code',
            // Does not include either of the ``` lines
            text: lines.slice(startOfCode, endOfCode).join('\n'),
          });
          i = endOfCode; // advances past the closing```
          continue;
        }
      }
      if (this.isSingleLineCode(lines[i])) {
        // no guard check as _isSingleLineCode tested on the pattern
        const codeContent = lines[i].match(CODE_MARKER_PATTERN)![2];
        result.push({type: 'code', text: codeContent});
      } else if (this.isList(lines[i])) {
        const endOfList = this.getEndOfSection(lines, i + 1, line =>
          this.isList(line)
        );
        result.push(this.makeList(lines.slice(i, endOfList)));
        i = endOfList - 1;
      } else if (this.isQuote(lines[i])) {
        const endOfQuote = this.getEndOfSection(lines, i + 1, line =>
          this.isQuote(line)
        );
        const blockLines = lines
          .slice(i, endOfQuote)
          .map(l => l.replace(/^[ ]?>[ ]?/, ''));
        result.push({
          type: 'quote',
          blocks: this._computeBlocks(blockLines.join('\n')),
        });
        i = endOfQuote - 1;
      } else if (this.isPreFormat(lines[i])) {
        // include pre or all regular lines but stop at next new line
        const predicate = (line: string) =>
          this.isPreFormat(line) ||
          (this.isRegularLine(line) &&
            !this.isWhitespaceLine(line) &&
            line.length > 0);
        const endOfPre = this.getEndOfSection(lines, i + 1, predicate);
        result.push({
          type: 'pre',
          text: lines.slice(i, endOfPre).join('\n'),
        });
        i = endOfPre - 1;
      } else {
        const endOfRegularLines = this.getEndOfSection(lines, i + 1, line =>
          this.isRegularLine(line)
        );
        result.push({
          type: 'paragraph',
          text: lines.slice(i, endOfRegularLines).join('\n'),
        });
        i = endOfRegularLines - 1;
      }
    }

    return result;
  }

  private getEndOfSection(
    lines: string[],
    startIndex: number,
    sectionPredicate: (line: string) => boolean
  ) {
    const index = lines
      .slice(startIndex)
      .findIndex(line => !sectionPredicate(line));
    return index === -1 ? lines.length : index + startIndex;
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
  private makeList(lines: string[]): Block {
    const items = lines.map(line => line.substring(1).trim());
    return {type: 'list', items};
  }

  private isRegularLine(line: string): boolean {
    return (
      !this.isQuote(line) &&
      !this.isCodeMarkLine(line) &&
      !this.isSingleLineCode(line) &&
      !this.isList(line) &&
      !this.isPreFormat(line)
    );
  }

  private isQuote(line: string): boolean {
    return line.startsWith('> ') || line.startsWith(' > ');
  }

  private isCodeMarkLine(line: string): boolean {
    return line.trim() === '```';
  }

  private isSingleLineCode(line: string): boolean {
    return CODE_MARKER_PATTERN.test(line);
  }

  private isPreFormat(line: string): boolean {
    return /^[ \t]/.test(line) && !this.isWhitespaceLine(line);
  }

  private isList(line: string): boolean {
    return /^[-*] /.test(line);
  }

  private isWhitespaceLine(line: string): boolean {
    return /^\s+$/.test(line);
  }

  private renderLinkedText(content: string, isPre?: boolean): TemplateResult {
    return html`
      <gr-linked-text
        class="${isPre ? 'pre' : ''}"
        .config=${this.config}
        content=${content}
        pre
      ></gr-linked-text>
    `;
  }

  private renderBlock(block: Block): TemplateResult {
    switch (block.type) {
      case 'paragraph':
        return html`<p>${this.renderLinkedText(block.text)}</p>`;
      case 'quote':
        return html`
          <blockquote>
            ${block.blocks.map(subBlock => this.renderBlock(subBlock))}
          </blockquote>
        `;
      case 'code':
        return html`<code>${block.text}</code>`;
      case 'pre':
        return this.renderLinkedText(block.text, true);
      case 'list':
        return html`
          <ul>
            ${block.items.map(
              item => html`<li>${this.renderLinkedText(item)}</li>`
            )}
          </ul>
        `;
    }
  }
}
