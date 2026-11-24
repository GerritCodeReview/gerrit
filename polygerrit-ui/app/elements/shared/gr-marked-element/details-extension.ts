/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import type {
  RendererThis,
  Token,
  TokenizerAndRendererExtension,
  TokenizerThis,
  Tokens,
} from 'marked';

/** Matches the opening `<details>` tag and captures its attributes. */
const OPEN_TAG = /^ {0,3}<details\b((?:[^>"']|"[^"]*"|'[^']*')*)>[ \t]*\n?/i;

/** Matches a leading `<summary>...</summary>` inside a `<details>` block. */
const SUMMARY_TAG = /^[ \t]*<summary\b[^>]*>([\s\S]*?)<\/summary>[ \t]*\n?/i;

/** Matches the `open` attribute, but not attributes merely containing it. */
const OPEN_ATTR = /(?:^|\s)open(?:\s*=("[^"]*"|'[^']*'|[^\s"'=<>`]+))?/i;

interface DetailsToken extends Tokens.Generic {
  type: 'details';
  open: boolean;
  summaryTokens: Token[] | null;
  tokens: Token[];
}

/**
 * Index of the `</details>` closing the tag that starts at the beginning of
 * `src`, taking nested `<details>` blocks into account. `from` is the index
 * right after the opening tag. Returns -1 when the block is never closed.
 */
function findClosingTag(src: string, from: number): number {
  const tags = /<details\b|<\/details\s*>/gi;
  tags.lastIndex = from;
  let depth = 1;
  let match: RegExpExecArray | null;
  while ((match = tags.exec(src))) {
    depth += match[0][1] === '/' ? -1 : 1;
    if (depth === 0) return match.index;
  }
  return -1;
}

/**
 * `<details>` blocks are passed through as raw HTML by the default html block
 * tokenizer, which means Gerrit renders them as plaintext and nothing inside
 * them is interpreted as markdown. This extension tokenizes them as a block of
 * their own so that the summary and the body are parsed as markdown.
 *
 * Only the `open` attribute survives, everything else on the tag is dropped.
 */
export const detailsExtension: TokenizerAndRendererExtension = {
  name: 'details',
  level: 'block',
  start(src) {
    return src.match(/^ {0,3}<details\b/i)?.index;
  },
  tokenizer(this: TokenizerThis, src: string): Tokens.Generic | undefined {
    const openTag = OPEN_TAG.exec(src);
    if (!openTag) return;
    const closeStart = findClosingTag(src, openTag[0].length);
    if (closeStart < 0) return;

    let rawEnd = closeStart + '</details>'.length;
    if (src[rawEnd] === '\n') rawEnd++;

    let inner = src.slice(openTag[0].length, closeStart);
    const token: DetailsToken = {
      type: 'details',
      raw: src.slice(0, rawEnd),
      open: OPEN_ATTR.test(openTag[1]),
      summaryTokens: null,
      tokens: [],
    };

    const summary = SUMMARY_TAG.exec(inner);
    if (summary) {
      inner = inner.slice(summary[0].length);
      token.summaryTokens = this.lexer.inlineTokens(summary[1].trim());
    }
    this.lexer.blockTokens(inner.trim(), token.tokens);
    return token;
  },
  renderer(this: RendererThis, token: Tokens.Generic): string {
    const {open, summaryTokens, tokens} = token as DetailsToken;
    return (
      `<details${open ? ' open' : ''}>` +
      (summaryTokens
        ? `<summary>${this.parser.parseInline(summaryTokens)}</summary>`
        : '') +
      this.parser.parse(tokens) +
      '</details>\n'
    );
  },
};
