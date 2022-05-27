/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-linked-text/gr-linked-text';
import {CommentLinks} from '../../../types/common';
import {LitElement, css, html, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators';

const CODE_MARKER_PATTERN = /^(`{1,3})([^`]+?)\1$/;
const INLINE_PATTERN = /(\[.+?\]\(.+?\)|`[^`]+?`)/;
const EXTRACT_LINK_PATTERN = /\[(.+?)\]\((.+?)\)/;

export type Block = ListBlock | QuoteBlock | Paragraph | CodeBlock | PreBlock;
export interface ListBlock {
  type: 'list';
  items: ListItem[];
}
export interface ListItem {
  spans: InlineItem[];
}

export interface QuoteBlock {
  type: 'quote';
  blocks: Block[];
}
export interface Paragraph {
  type: 'paragraph';
  spans: InlineItem[];
}
export interface CodeBlock {
  type: 'code';
  text: string;
}
export interface PreBlock {
  type: 'pre';
  text: string;
}

export type InlineItem = TextSpan | LinkSpan | CodeSpan;

export interface TextSpan {
  type: 'text';
  text: string;
}

export interface LinkSpan {
  type: 'link';
  text: string;
  url: string;
}

export interface CodeSpan {
  type: 'code';
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

  static override get styles() {
    return [
      css`
        :host {
          display: block;
          font-family: var(--font-family);
        }
        a {
          color: var(--link-color);
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
        blockquote {
          border-left: 1px solid #aaa;
          padding: 0 var(--spacing-m);
        }
        code {
          display: block;
          white-space: pre-wrap;
          background-color: var(--background-color-secondary);
          border: 1px solid var(--border-color);
          border-left-width: var(--spacing-s);
          margin: var(--spacing-m) 0;
          padding: var(--spacing-s) var(--spacing-m);
          overflow-x: scroll;
        }
        li {
          list-style-type: disc;
          margin-left: var(--spacing-xl);
        }
        .inline-code,
        code {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-code);
          line-height: var(--line-height-mono);
          background-color: var(--background-color-secondary);
          border: 1px solid var(--border-color);
          padding: 1px var(--spacing-s);
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
   * * 'paragraph' (Paragraph of regular text)
   * * 'quote' (Block quote.)
   * * 'pre' (Pre-formatted text.)
   * * 'list' (Unordered list.)
   * * 'code' (code blocks.)
   *
   * For blocks of type 'paragraph' there is a list of spans that is the content
   * for that paragraph.
   *
   * For blocks of type 'pre' and 'code' there is a `text`
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
        const endOfPre = this.findEndOfPreBlock(lines, i);
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
          spans: this.computeInlineItems(
            lines.slice(i, endOfRegularLines).join('\n')
          ),
        });
        i = endOfRegularLines - 1;
      }
    }

    return result;
  }

  private computeInlineItems(content: string): InlineItem[] {
    const result: InlineItem[] = [];
    const textSpans = content.split(INLINE_PATTERN);
    for (let i = 0; i < textSpans.length; ++i) {
      // Because INLINE_PATTERN has a single capturing group, string.split will
      // return strings before and after each match as well as the matched
      // group. These are always interleaved starting with a non-matched string
      // which may be empty.
      if (textSpans[i].length === 0) {
        // No point in processing empty strings.
        continue;
      } else if (i % 2 === 0) {
        // A non-matched string.
        result.push({type: 'text', text: textSpans[i]});
      } else if (textSpans[i].startsWith('`')) {
        result.push({type: 'code', text: textSpans[i].slice(1, -1)});
      } else {
        const m = textSpans[i].match(EXTRACT_LINK_PATTERN);
        if (!m) {
          result.push({type: 'text', text: textSpans[i]});
        } else {
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          const [_, text, url] = m;
          result.push({type: 'link', text, url});
        }
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

  private findEndOfPreBlock(lines: string[], startIndex: number) {
    let lastPreFormat = startIndex;
    for (let i = startIndex + 1; i < lines.length; ++i) {
      const line = lines[i];
      if (this.isPreFormat(line)) {
        lastPreFormat = i;
      } else if (!this.isWhitespaceLine(line) && line.length !== 0) {
        break;
      }
    }
    return lastPreFormat + 1;
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
    return {
      type: 'list',
      items: lines.map(line => {
        return {
          spans: this.computeInlineItems(line.substring(1).trim()),
        };
      }),
    };
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

  private renderInlineText(content: string): TemplateResult {
    return html`
      <gr-linked-text
        .config=${this.config}
        content=${content}
        pre
        inline
      ></gr-linked-text>
    `;
  }

  private renderLink(text: string, url: string): TemplateResult {
    return html`<a href=${url}>${text}</a>`;
  }

  private renderInlineCode(text: string): TemplateResult {
    return html`<span class="inline-code">${text}</span>`;
  }

  private renderInlineItem(span: InlineItem): TemplateResult {
    switch (span.type) {
      case 'text':
        return this.renderInlineText(span.text);
      case 'link':
        return this.renderLink(span.text, span.url);
      case 'code':
        return this.renderInlineCode(span.text);
      default:
        return html``;
    }
  }

  private renderListItem(item: ListItem): TemplateResult {
    return html` <li>
      ${item.spans.map(item => this.renderInlineItem(item))}
    </li>`;
  }

  private renderBlock(block: Block): TemplateResult {
    switch (block.type) {
      case 'paragraph':
        return html` <p>
          ${block.spans.map(item => this.renderInlineItem(item))}
        </p>`;
      case 'quote':
        return html`
          <blockquote>
            ${block.blocks.map(subBlock => this.renderBlock(subBlock))}
          </blockquote>
        `;
      case 'code':
        return html`<code>${block.text}</code>`;
      case 'pre':
        return html`<pre><code>${block.text}</code></pre>`;
      case 'list':
        return html`
          <ul>
            ${block.items.map(item => this.renderListItem(item))}
          </ul>
        `;
    }
  }
}
