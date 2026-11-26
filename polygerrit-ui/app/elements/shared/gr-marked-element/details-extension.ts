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
 * Match the closing tag of a `<details>` block, ignoring tags in code spans
 * and fenced code blocks. `from` is the index after the opening tag.
 */
function findClosingTag(src: string, from: number): RegExpExecArray | null {
  const tokens =
    /^ {0,3}(`{3,}|~{3,})[^\n]*(?:\n|$)|`+|<details(?=[\s>])|<\/details\s*>/gim;
  tokens.lastIndex = from;
  let depth = 1;
  let match: RegExpExecArray | null;
  while ((match = tokens.exec(src))) {
    if (match[1]) {
      const fence = match[1];
      const closingFence = new RegExp(
        `^ {0,3}${fence[0]}{${fence.length},}[ \\t]*(?:\\n|$)`,
        'gm'
      );
      closingFence.lastIndex = tokens.lastIndex;
      const end = closingFence.exec(src);
      tokens.lastIndex = end ? closingFence.lastIndex : src.length;
    } else if (match[0][0] === '`') {
      const codeSpans = /`+/g;
      codeSpans.lastIndex = tokens.lastIndex;
      let end: RegExpExecArray | null;
      while ((end = codeSpans.exec(src))) {
        if (end[0].length === match[0].length) break;
      }
      if (end) tokens.lastIndex = codeSpans.lastIndex;
    } else {
      depth += match[0][1] === '/' ? -1 : 1;
      if (depth === 0) return match;
    }
  }
  return null;
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
    const match = /\n {0,3}<details(?=[\s>])/i.exec(src);
    return match ? match.index + 1 : undefined;
  },
  tokenizer(this: TokenizerThis, src: string): Tokens.Generic | undefined {
    const openTag = OPEN_TAG.exec(src);
    if (!openTag) return;
    const closeTag = findClosingTag(src, openTag[0].length);
    if (!closeTag) return;

    let rawEnd = closeTag.index + closeTag[0].length;
    rawEnd += /^[ \t]*\n?/.exec(src.slice(rawEnd))?.[0].length ?? 0;

    let inner = src.slice(openTag[0].length, closeTag.index);
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
