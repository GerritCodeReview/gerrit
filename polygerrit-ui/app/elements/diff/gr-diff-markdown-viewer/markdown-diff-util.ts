/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DiffInfo} from '../../../types/diff';
import {CommentSide, Side} from '../../../constants/constants';
import {CommentThread, PatchRange} from '../../../types/common';
import {isInBaseOfPatchRange} from '../../../utils/comment-util';
import {getDiffLines} from '../../../utils/diff-util';
import {html, TemplateResult} from 'lit';
import {Lexer, Marked, Token, Tokens} from 'marked';

export type DiffBlockStatus = 'unchanged' | 'added' | 'deleted' | 'modified';

export type WrapperTag =
  | 'p'
  | 'li'
  | 'th'
  | 'td'
  | 'h1'
  | 'h2'
  | 'h3'
  | 'h4'
  | 'h5'
  | 'h6';

export type MarkdownToken = Token & {
  startLine?: number;
  endLine?: number;
};

export interface AlignedDiffRow {
  status: DiffBlockStatus;
  leftToken?: MarkdownToken;
  rightToken?: MarkdownToken;
  leftHtml?: TemplateResult;
  rightHtml?: TemplateResult;
  leftStartLine?: number;
  leftEndLine?: number;
  rightStartLine?: number;
  rightEndLine?: number;
}

export interface AlignedDiffRowWithThreads extends AlignedDiffRow {
  leftThreads: CommentThread[];
  rightThreads: CommentThread[];
}

export interface InlineDiffSegment {
  text: string;
  type: 'common' | 'added' | 'deleted';
}

/**
 * Maximum normalized edit distance (0.0 = identical, 1.0 = completely disjoint)
 * for two blocks, lines, or list items to be inferred as homologous
 * modifications of one another (inspired by dandavison/delta's
 * --max-line-distance, which defaults to 0.6).
 */
export const DEFAULT_MAX_HOMOLOGOUS_DISTANCE = 0.6;

/** Reconstruct the entire file content from diff chunks for the given side. */
export function reconstructFileContent(diff: DiffInfo, side: Side): string {
  return getDiffLines(diff, side).join('\n');
}

/** Parse markdown text into top-level block tokens with line number metadata. */
export function parseMarkdownBlocks(markdown: string): MarkdownToken[] {
  if (!markdown) return [];
  const marked = new Marked();
  const tokens = marked.lexer(markdown);
  const result: MarkdownToken[] = [];
  let curLine = 1;
  for (const t of tokens) {
    const raw = t.raw;
    const newlineCount = (raw.match(/\n/g) || []).length;
    const startLine = curLine;
    const endLine = curLine + newlineCount - (raw.endsWith('\n') ? 1 : 0);
    curLine += newlineCount;
    if (t.type === 'space') {
      continue;
    }
    (t as MarkdownToken).startLine = startLine;
    (t as MarkdownToken).endLine = Math.max(startLine, endLine);
    result.push(t as MarkdownToken);
  }
  return result;
}

function isMarkdownLinkToken(token: Token): token is Tokens.Link {
  return token.type === 'link';
}

function isInlineContainerToken(
  token: Token
): token is (Tokens.Strong | Tokens.Em | Tokens.Del) & {tokens: Token[]} {
  return (
    (token.type === 'strong' || token.type === 'em' || token.type === 'del') &&
    'tokens' in token &&
    Array.isArray(token.tokens)
  );
}

function getContainerDelimiters(token: Token & {tokens?: Token[]}): {
  openDelim: string;
  closeDelim: string;
} {
  const innerRaw = (token.tokens ?? []).map(t => t.raw).join('');
  const idx = innerRaw ? token.raw.indexOf(innerRaw) : -1;
  if (idx >= 0) {
    return {
      openDelim: token.raw.slice(0, idx),
      closeDelim: token.raw.slice(idx + innerRaw.length),
    };
  }
  return {openDelim: '', closeDelim: ''};
}

function tokenizeInlineAstTokens(tokens: Token[], result: string[]) {
  for (const token of tokens) {
    if (token.type === 'codespan' || isMarkdownLinkToken(token)) {
      result.push(token.raw);
    } else if (isInlineContainerToken(token)) {
      const {openDelim, closeDelim} = getContainerDelimiters(token);
      if (openDelim) result.push(openDelim);
      tokenizeInlineAstTokens(token.tokens, result);
      if (closeDelim) result.push(closeDelim);
    } else {
      const subTokens = token.raw.match(/\s+|[^\s\w]+|\w+/g);
      if (subTokens) {
        result.push(...subTokens);
      }
    }
  }
}

/**
 * Tokenize text using Marked's inline AST lexer (`Lexer.lexInline`) so
 * markdown links and inline code spans remain atomic AST units (including when
 * nested inside container tokens such as `strong`, `em`, or `del`) while plain
 * text nodes are split into words, whitespace, and punctuation for
 * fine-grained inline diffing.
 */
export function tokenizeWords(text: string): string[] {
  if (!text) return [];
  const inlineTokens = Lexer.lexInline(text);
  const result: string[] = [];
  tokenizeInlineAstTokens(inlineTokens, result);
  return result;
}

const SAFE_URL_SCHEMES = new Set([
  'http:',
  'https:',
  'mailto:',
  'ftp:',
  'file:',
]);

/**
 * Sanitize a markdown link href so dangerous schemes (such as `javascript:`,
 * `data:`, or `vbscript:`) cannot execute when rendered in an `<a>` tag.
 */
export function sanitizeMarkdownUrl(url: string): string {
  const trimmed = url.trim();
  if (!trimmed) return '';
  try {
    const parsed = new URL(trimmed);
    if (SAFE_URL_SCHEMES.has(parsed.protocol.toLowerCase())) {
      return trimmed;
    }
    return 'about:invalid#zClosurez';
  } catch {
    // Relative URL (no explicit scheme); reject control-character-obfuscated schemes.
    // eslint-disable-next-line no-control-regex
    const normalized = trimmed.replace(/[\u0000-\u0020]+/g, '');
    if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(normalized)) {
      return 'about:invalid#zClosurez';
    }
    return trimmed;
  }
}

function renderCodeSpanToken(token: Tokens.Codespan): TemplateResult {
  return html`<code>${token.text}</code>`;
}

function renderInlineContainerTag(
  type: 'strong' | 'em' | 'del',
  content: unknown
): TemplateResult {
  switch (type) {
    case 'strong':
      return html`<strong>${content}</strong>`;
    case 'em':
      return html`<em>${content}</em>`;
    case 'del':
      return html`<del>${content}</del>`;
  }
}

function renderHighlightTag(
  tag: 'del' | 'ins',
  highlightClass: string,
  content: unknown
): TemplateResult {
  return tag === 'del'
    ? html`<del class=${highlightClass}>${content}</del>`
    : html`<ins class=${highlightClass}>${content}</ins>`;
}

function renderWrapperTag(tag: WrapperTag, content: unknown): TemplateResult {
  switch (tag) {
    case 'h1':
      return html`<h1>${content}</h1>`;
    case 'h2':
      return html`<h2>${content}</h2>`;
    case 'h3':
      return html`<h3>${content}</h3>`;
    case 'h4':
      return html`<h4>${content}</h4>`;
    case 'h5':
      return html`<h5>${content}</h5>`;
    case 'h6':
      return html`<h6>${content}</h6>`;
    case 'li':
      return html`<li>${content}</li>`;
    case 'th':
      return html`<th>${content}</th>`;
    case 'td':
      return html`<td>${content}</td>`;
    case 'p':
      return html`<p>${content}</p>`;
  }
}

function renderInlineAstTokens(
  tokens: Token[],
  options: {includeLinks: boolean}
): TemplateResult {
  const parts: Array<TemplateResult | string> = [];
  for (const token of tokens) {
    if (token.type === 'codespan') {
      parts.push(renderCodeSpanToken(token as Tokens.Codespan));
    } else if (options.includeLinks && isMarkdownLinkToken(token)) {
      const labelContent =
        token.tokens && token.tokens.length > 0
          ? renderInlineAstTokens(token.tokens, {
              includeLinks: false,
            })
          : token.text || token.href;
      const safeHref = sanitizeMarkdownUrl(token.href);
      parts.push(html`<a href=${safeHref}>${labelContent}</a>`);
    } else if (isInlineContainerToken(token)) {
      parts.push(
        renderInlineContainerTag(
          token.type,
          renderInlineAstTokens(token.tokens, options)
        )
      );
    } else if (token.type === 'br') {
      parts.push(html`<br />`);
    } else if (
      'tokens' in token &&
      token.tokens &&
      token.type !== 'image' &&
      !isMarkdownLinkToken(token)
    ) {
      parts.push(renderInlineAstTokens(token.tokens, options));
    } else {
      parts.push(token.raw);
    }
  }
  return html`${parts}`;
}

/**
 * Render text with inline backtick code spans (`...` or ``...``) converted to
 * <code> Lit template elements using Marked's inline AST lexer.
 */
export function renderInlineCodeSpans(text: string): TemplateResult {
  if (!text) return html``;
  return renderInlineAstTokens(Lexer.lexInline(text), {includeLinks: false});
}

/**
 * Render text with markdown links ([label](url)) converted to <a> Lit template
 * elements and inline backtick code spans converted to <code> Lit template
 * elements using Marked's inline AST lexer.
 */
export function renderInlineMarkdown(text: string): TemplateResult {
  if (!text) return html``;
  return renderInlineAstTokens(Lexer.lexInline(text), {includeLinks: true});
}

/** Compute LCS-based diff between two arrays of strings (e.g. words or lines). */
export function computeSequenceDiff(
  seqA: string[],
  seqB: string[]
): InlineDiffSegment[] {
  const m = seqA.length;
  const n = seqB.length;
  // DP table for LCS lengths
  const dp: number[][] = Array.from({length: m + 1}, () =>
    new Array(n + 1).fill(0)
  );

  for (let i = 0; i < m; i++) {
    for (let j = 0; j < n; j++) {
      if (seqA[i] === seqB[j]) {
        dp[i + 1][j + 1] = dp[i][j] + 1;
      } else {
        dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
  }

  // Backtrack to build diff segments
  const segments: InlineDiffSegment[] = [];
  let i = m;
  let j = n;

  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && seqA[i - 1] === seqB[j - 1]) {
      segments.unshift({text: seqA[i - 1], type: 'common'});
      i--;
      j--;
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      segments.unshift({text: seqB[j - 1], type: 'added'});
      j--;
    } else if (i > 0 && (j === 0 || dp[i][j - 1] < dp[i - 1][j])) {
      segments.unshift({text: seqA[i - 1], type: 'deleted'});
      i--;
    }
  }

  return segments;
}

/**
 * Tokenize a single line of code into placeholders (<...>), CLI flags,
 * identifiers, whitespace, and punctuation for within-line code diffing.
 */
export function tokenizeCodeWords(line: string): string[] {
  const codeTokenRegex =
    /<[A-Za-z0-9_.-]+>|--?[A-Za-z0-9_.-]+|\w+|\s+|[^\s\w]/g;
  return line.match(codeTokenRegex) || [];
}

/**
 * Compute the normalized token edit distance in [0.0, 1.0] between two token
 * sequences, ignoring pure-whitespace tokens so spaces do not artificially
 * inflate similarity between unrelated sentences.
 */
export function computeNormalizedEditDistance(
  seqA: string[],
  seqB: string[]
): number {
  const contentA = seqA.filter(t => !/^\s+$/.test(t));
  const contentB = seqB.filter(t => !/^\s+$/.test(t));
  if (contentA.length === 0 && contentB.length === 0) return 0;
  if (contentA.length === 0 || contentB.length === 0) return 1;

  const diff = computeSequenceDiff(contentA, contentB);
  let commonCount = 0;
  for (const seg of diff) {
    if (seg.type === 'common') {
      commonCount++;
    }
  }
  const diceDistance =
    1 - (2 * commonCount) / (contentA.length + contentB.length);
  const overlapDistance =
    1 - commonCount / Math.min(contentA.length, contentB.length);
  // If one sequence is an extension of the other (high overlap with the shorter
  // sequence and at least 2 shared non-whitespace tokens), treat them as
  // homologous so shared prefixes/clauses remain unhighlighted.
  if (commonCount >= 2 && overlapDistance <= DEFAULT_MAX_HOMOLOGOUS_DISTANCE) {
    return Math.min(diceDistance, overlapDistance);
  }
  return diceDistance;
}

/**
 * Coalesce fragmented inline diff segments (inspired by dandavison/delta's
 * within-line edit coalescing):
 * 1. Merge consecutive segments of the same type.
 * 2. Absorb pure-whitespace 'common' tokens sandwiched between edit segments
 *    so multi-word edits form a single continuous highlight span.
 * 3. Absorb isolated non-word punctuation at the boundary of a multi-word
 *    clause edit so trailing periods do not detach from rewritten sentences.
 */
export function coalesceInlineDiffSegments(
  segments: InlineDiffSegment[]
): InlineDiffSegment[] {
  const mergeAdjacent = (segs: InlineDiffSegment[]): InlineDiffSegment[] => {
    const merged: InlineDiffSegment[] = [];
    for (const seg of segs) {
      if (!seg.text) continue;
      const last = merged[merged.length - 1];
      if (last && last.type === seg.type) {
        last.text += seg.text;
      } else {
        merged.push({...seg});
      }
    }
    return merged;
  };

  // Group segments into alternating 'common' and edit ('deleted' + 'added') runs.
  type Run =
    | {kind: 'common'; text: string}
    | {kind: 'edit'; delText: string; addText: string};

  const buildRuns = (segs: InlineDiffSegment[]): Run[] => {
    const runs: Run[] = [];
    for (const seg of mergeAdjacent(segs)) {
      if (seg.type === 'common') {
        const last = runs[runs.length - 1];
        if (last && last.kind === 'common') {
          last.text += seg.text;
        } else {
          runs.push({kind: 'common', text: seg.text});
        }
      } else {
        let last = runs[runs.length - 1];
        if (!last || last.kind !== 'edit') {
          last = {kind: 'edit', delText: '', addText: ''};
          runs.push(last);
        }
        if (seg.type === 'deleted') {
          last.delText += seg.text;
        } else {
          last.addText += seg.text;
        }
      }
    }
    return runs;
  };

  const runs = buildRuns(segments);

  // Coalesce pure-whitespace common runs sandwiched between two edit runs
  // when both edits have deleted text or both edits have added text.
  let changed = true;
  while (changed) {
    changed = false;
    for (let i = 1; i < runs.length - 1; i++) {
      const prev = runs[i - 1];
      const curr = runs[i];
      const next = runs[i + 1];
      if (
        curr.kind === 'common' &&
        /^\s+$/.test(curr.text) &&
        prev.kind === 'edit' &&
        next.kind === 'edit'
      ) {
        const bothHaveDel = prev.delText.length > 0 && next.delText.length > 0;
        const bothHaveAdd = prev.addText.length > 0 && next.addText.length > 0;
        const prevIsPair = prev.delText.length > 0 && prev.addText.length > 0;
        const nextIsPair = next.delText.length > 0 && next.addText.length > 0;
        if (
          (bothHaveDel && !prev.addText && !next.addText) ||
          (bothHaveAdd && !prev.delText && !next.delText) ||
          (prevIsPair && nextIsPair)
        ) {
          if (prev.delText || next.delText) {
            prev.delText += curr.text + next.delText;
          }
          if (prev.addText || next.addText) {
            prev.addText += curr.text + next.addText;
          }
          runs.splice(i, 2);
          changed = true;
          break;
        }
      }
    }
  }

  // Absorb a trailing punctuation-only common run (e.g. a lone '.') when it
  // immediately follows a multi-word replacement edit that itself contains
  // sentence punctuation or whitespace.
  if (runs.length >= 2) {
    const last = runs[runs.length - 1];
    const prev = runs[runs.length - 2];
    if (
      last.kind === 'common' &&
      /^[.,;!?]+$/.test(last.text) &&
      prev.kind === 'edit' &&
      prev.delText.length > 0 &&
      prev.addText.length > 0 &&
      (/\s/.test(prev.delText) || /\s/.test(prev.addText)) &&
      prev.delText.length + prev.addText.length > 20
    ) {
      prev.delText += last.text;
      prev.addText += last.text;
      runs.pop();
    }
  }

  const out: InlineDiffSegment[] = [];
  for (const r of runs) {
    if (r.kind === 'common') {
      out.push({text: r.text, type: 'common'});
    } else {
      if (r.delText) out.push({text: r.delText, type: 'deleted'});
      if (r.addText) out.push({text: r.addText, type: 'added'});
    }
  }
  return mergeAdjacent(out);
}

/**
 * Align two lists of items using dynamic programming over normalized edit
 * distance, pairing items only when their distance is <= maxDistance.
 */
export function alignHomologousItems(
  itemsA: string[],
  itemsB: string[],
  tokenizer: (text: string) => string[] = tokenizeWords,
  maxDistance = DEFAULT_MAX_HOMOLOGOUS_DISTANCE
): Array<
  | {
      type: 'unchanged';
      textA: string;
      textB: string;
      aIdx: number;
      bIdx: number;
    }
  | {type: 'modified'; textA: string; textB: string; aIdx: number; bIdx: number}
  | {type: 'deleted'; textA: string; aIdx: number}
  | {type: 'added'; textB: string; bIdx: number}
> {
  const m = itemsA.length;
  const n = itemsB.length;
  const tokensCacheA = itemsA.map(tokenizer);
  const tokensCacheB = itemsB.map(tokenizer);

  // Score matrix: pairing (i, j) yields a positive score of (1 - distance)
  // when distance <= maxDistance.
  const pairScore: number[][] = Array.from({length: m}, (_, i) =>
    Array.from({length: n}, (__, j) => {
      if (itemsA[i] === itemsB[j]) return 2; // Strongly prefer exact matches
      const dist = computeNormalizedEditDistance(
        tokensCacheA[i],
        tokensCacheB[j]
      );
      return dist <= maxDistance ? 1 - dist : -1;
    })
  );

  const dp: number[][] = Array.from({length: m + 1}, () =>
    new Array(n + 1).fill(0)
  );

  for (let i = 0; i < m; i++) {
    for (let j = 0; j < n; j++) {
      let best = Math.max(dp[i][j + 1], dp[i + 1][j]);
      if (pairScore[i][j] >= 0) {
        best = Math.max(best, dp[i][j] + pairScore[i][j]);
      }
      dp[i + 1][j + 1] = best;
    }
  }

  const result: Array<
    | {
        type: 'unchanged';
        textA: string;
        textB: string;
        aIdx: number;
        bIdx: number;
      }
    | {
        type: 'modified';
        textA: string;
        textB: string;
        aIdx: number;
        bIdx: number;
      }
    | {type: 'deleted'; textA: string; aIdx: number}
    | {type: 'added'; textB: string; bIdx: number}
  > = [];

  let i = m;
  let j = n;
  while (i > 0 || j > 0) {
    if (
      i > 0 &&
      j > 0 &&
      pairScore[i - 1][j - 1] >= 0 &&
      Math.abs(dp[i][j] - (dp[i - 1][j - 1] + pairScore[i - 1][j - 1])) < 1e-9
    ) {
      const aIdx = i - 1;
      const bIdx = j - 1;
      result.unshift({
        type: itemsA[aIdx] === itemsB[bIdx] ? 'unchanged' : 'modified',
        textA: itemsA[aIdx],
        textB: itemsB[bIdx],
        aIdx,
        bIdx,
      });
      i--;
      j--;
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      result.unshift({type: 'added', textB: itemsB[j - 1], bIdx: j - 1});
      j--;
    } else {
      result.unshift({type: 'deleted', textA: itemsA[i - 1], aIdx: i - 1});
      i--;
    }
  }

  return result;
}

/** Render a single marked block token to a Lit TemplateResult. */
export function renderTokenToHtml(token?: Token): TemplateResult {
  if (!token) return html``;
  switch (token.type) {
    case 'heading': {
      const heading = token as Tokens.Heading;
      const depth = Math.min(Math.max(heading.depth, 1), 6);
      const tag = `h${depth}` as WrapperTag;
      const content = heading.tokens
        ? renderInlineAstTokens(heading.tokens, {includeLinks: true})
        : renderInlineMarkdown(heading.text);
      return renderWrapperTag(tag, content);
    }
    case 'paragraph': {
      const para = token as Tokens.Paragraph;
      const content = para.tokens
        ? renderInlineAstTokens(para.tokens, {includeLinks: true})
        : renderInlineMarkdown(para.text);
      return html`<p>${content}</p>`;
    }
    case 'text': {
      const textTok = token as Tokens.Text;
      return textTok.tokens
        ? renderInlineAstTokens(textTok.tokens, {includeLinks: true})
        : renderInlineMarkdown(textTok.text);
    }
    case 'code': {
      const codeTok = token as Tokens.Code;
      const langClass = codeTok.lang ? `language-${codeTok.lang}` : undefined;
      return langClass
        ? html`<pre><code class=${langClass}>${codeTok.text}</code></pre>`
        : html`<pre><code>${codeTok.text}</code></pre>`;
    }
    case 'blockquote': {
      const bq = token as Tokens.Blockquote;
      const children = bq.tokens
        ? bq.tokens.map(t => renderTokenToHtml(t))
        : html`<p>${renderInlineMarkdown(bq.text.trim())}</p>`;
      return html`<blockquote>${children}</blockquote>`;
    }
    case 'list': {
      const listTok = token as Tokens.List;
      const items = listTok.items.map(item => {
        const itemContent = item.tokens
          ? item.tokens.map(t =>
              t.type === 'text'
                ? (t as Tokens.Text).tokens
                  ? renderInlineAstTokens((t as Tokens.Text).tokens!, {
                      includeLinks: true,
                    })
                  : renderInlineMarkdown((t as Tokens.Text).text)
                : renderTokenToHtml(t)
            )
          : renderInlineMarkdown(item.text);
        return html`<li>${itemContent}</li>`;
      });
      return listTok.ordered
        ? html`<ol>
            ${items}
          </ol>`
        : html`<ul>
            ${items}
          </ul>`;
    }
    case 'table': {
      const tableTok = token as Tokens.Table;
      const headerCells = tableTok.header.map(cell => {
        const content = cell.tokens
          ? renderInlineAstTokens(cell.tokens, {includeLinks: true})
          : renderInlineMarkdown(cell.text);
        return html`<th>${content}</th>`;
      });
      const bodyRows = tableTok.rows.map(row => {
        const cells = row.map(cell => {
          const content = cell.tokens
            ? renderInlineAstTokens(cell.tokens, {includeLinks: true})
            : renderInlineMarkdown(cell.text);
          return html`<td>${content}</td>`;
        });
        return html`<tr>
          ${cells}
        </tr>`;
      });
      return html`<table>
        <thead>
          <tr>
            ${headerCells}
          </tr>
        </thead>
        <tbody>
          ${bodyRows}
        </tbody>
      </table>`;
    }
    case 'hr':
      return html`<hr />`;
    case 'space':
    case 'def':
      return html``;
    default:
      return html`<p>${token.raw}</p>`;
  }
}

function renderTokensWithDiffRanges(
  tokens: Token[],
  baseOffset: number,
  isEditedAt: boolean[],
  highlightTag: 'del' | 'ins',
  highlightClass: string
): TemplateResult {
  const result: Array<TemplateResult | string> = [];
  let activeHighlight: Array<TemplateResult | string> = [];
  const flushHighlight = () => {
    if (activeHighlight.length > 0) {
      result.push(
        renderHighlightTag(
          highlightTag,
          highlightClass,
          html`${activeHighlight}`
        )
      );
      activeHighlight = [];
    }
  };

  let offset = baseOffset;
  for (const token of tokens) {
    const tokStart = offset;
    const tokEnd = offset + token.raw.length;
    offset = tokEnd;

    let anyEdited = false;
    let allEdited = token.raw.length > 0;
    for (let p = tokStart; p < tokEnd; p++) {
      if (isEditedAt[p]) {
        anyEdited = true;
      } else {
        allEdited = false;
      }
    }

    if (allEdited) {
      activeHighlight.push(
        renderInlineAstTokens([token], {includeLinks: true})
      );
    } else if (!anyEdited) {
      flushHighlight();
      result.push(renderInlineAstTokens([token], {includeLinks: true}));
    } else if (isInlineContainerToken(token)) {
      const {openDelim, closeDelim} = getContainerDelimiters(token);
      const innerStart = tokStart + openDelim.length;
      const innerEnd = tokEnd - closeDelim.length;
      let innerAnyEdited = false;
      for (let p = innerStart; p < innerEnd; p++) {
        if (isEditedAt[p]) {
          innerAnyEdited = true;
          break;
        }
      }
      if (!innerAnyEdited) {
        activeHighlight.push(
          renderInlineAstTokens([token], {includeLinks: true})
        );
      } else {
        flushHighlight();
        const innerTemplate = renderTokensWithDiffRanges(
          token.tokens,
          innerStart,
          isEditedAt,
          highlightTag,
          highlightClass
        );
        result.push(renderInlineContainerTag(token.type, innerTemplate));
      }
    } else {
      let runStart = tokStart;
      while (runStart < tokEnd) {
        const runEdited = isEditedAt[runStart];
        let runEnd = runStart + 1;
        while (runEnd < tokEnd && isEditedAt[runEnd] === runEdited) {
          runEnd++;
        }
        const sliceText = token.raw.slice(
          runStart - tokStart,
          runEnd - tokStart
        );
        if (runEdited) {
          activeHighlight.push(sliceText);
        } else {
          flushHighlight();
          result.push(sliceText);
        }
        runStart = runEnd;
      }
    }
  }
  flushHighlight();
  return html`${result}`;
}

function renderSideInlineDiff(
  fullText: string,
  segments: InlineDiffSegment[],
  editType: 'deleted' | 'added',
  highlightTag: 'del' | 'ins',
  highlightClass: string
): TemplateResult {
  if (!fullText) return html``;
  const isEditedAt = new Array<boolean>(fullText.length).fill(false);
  let pos = 0;
  for (const seg of segments) {
    if (seg.type === 'common' || seg.type === editType) {
      if (seg.type === editType) {
        for (let i = 0; i < seg.text.length && pos + i < fullText.length; i++) {
          isEditedAt[pos + i] = true;
        }
      }
      pos += seg.text.length;
    }
  }
  return renderTokensWithDiffRanges(
    Lexer.lexInline(fullText),
    0,
    isEditedAt,
    highlightTag,
    highlightClass
  );
}

/** Render an inline diff for modified text in headings or paragraphs. */
export function renderInlineTextDiff(
  textA: string,
  textB: string,
  wrapperTag: WrapperTag = 'p',
  rightWrapperTag: WrapperTag = wrapperTag
): {leftHtml: TemplateResult; rightHtml: TemplateResult} {
  const wordsA = tokenizeWords(textA);
  const wordsB = tokenizeWords(textB);
  const distance = computeNormalizedEditDistance(wordsA, wordsB);

  let mergedSegments: InlineDiffSegment[];
  if (distance > DEFAULT_MAX_HOMOLOGOUS_DISTANCE) {
    // Non-homologous lines/blocks: highlight as whole replacements rather than
    // producing noisy "ransom-note" diffs across incidental stop words.
    mergedSegments = [];
    if (textA) mergedSegments.push({text: textA, type: 'deleted'});
    if (textB) mergedSegments.push({text: textB, type: 'added'});
  } else {
    const diff = computeSequenceDiff(wordsA, wordsB);
    mergedSegments = coalesceInlineDiffSegments(diff);
  }

  const leftContent = renderSideInlineDiff(
    textA,
    mergedSegments,
    'deleted',
    'del',
    'diff-highlight-del'
  );
  const rightContent = renderSideInlineDiff(
    textB,
    mergedSegments,
    'added',
    'ins',
    'diff-highlight-add'
  );

  return {
    leftHtml: renderWrapperTag(wrapperTag, leftContent),
    rightHtml: renderWrapperTag(rightWrapperTag, rightContent),
  };
}

/**
 * Render a modified code block with two-tier dandavison/delta-style highlights:
 * homologous modified lines get a subtle full-line background plus within-line
 * token emphasis on the specific tokens that changed.
 */
export function renderCodeBlockDiff(
  tokenA: Tokens.Code,
  tokenB: Tokens.Code
): {leftHtml: TemplateResult; rightHtml: TemplateResult} {
  const linesA = tokenA.text.split('\n');
  const linesB = tokenB.text.split('\n');
  const aligned = alignHomologousItems(
    linesA,
    linesB,
    tokenizeCodeWords,
    DEFAULT_MAX_HOMOLOGOUS_DISTANCE
  );

  const leftLineEntries: Array<TemplateResult | string> = [];
  const rightLineEntries: Array<TemplateResult | string> = [];

  for (const item of aligned) {
    if (item.type === 'unchanged') {
      leftLineEntries.push(item.textA);
      rightLineEntries.push(item.textA);
    } else if (item.type === 'deleted') {
      leftLineEntries.push(
        html`<span class="diff-highlight-del">${item.textA}</span>`
      );
    } else if (item.type === 'added') {
      rightLineEntries.push(
        html`<span class="diff-highlight-add">${item.textB}</span>`
      );
    } else {
      const wordsA = tokenizeCodeWords(item.textA);
      const wordsB = tokenizeCodeWords(item.textB);
      const rawDiff = computeSequenceDiff(wordsA, wordsB);
      const commonNonWsCount = rawDiff.filter(
        s => s.type === 'common' && !/^\s+$/.test(s.text)
      ).length;

      if (commonNonWsCount < 2) {
        leftLineEntries.push(
          html`<span class="diff-highlight-del">${item.textA}</span>`
        );
        rightLineEntries.push(
          html`<span class="diff-highlight-add">${item.textB}</span>`
        );
      } else {
        const coalesced = coalesceInlineDiffSegments(rawDiff);
        const leftParts: Array<TemplateResult | string> = [];
        const rightParts: Array<TemplateResult | string> = [];
        for (const seg of coalesced) {
          if (seg.type === 'common') {
            leftParts.push(seg.text);
            rightParts.push(seg.text);
          } else if (seg.type === 'deleted') {
            leftParts.push(
              html`<del class="diff-highlight-del">${seg.text}</del>`
            );
          } else if (seg.type === 'added') {
            rightParts.push(
              html`<ins class="diff-highlight-add">${seg.text}</ins>`
            );
          }
        }
        leftLineEntries.push(
          html`<span class="diff-code-line-del">${leftParts}</span>`
        );
        rightLineEntries.push(
          html`<span class="diff-code-line-add">${rightParts}</span>`
        );
      }
    }
  }

  const joinLinesWithNewline = (
    entries: Array<TemplateResult | string>
  ): Array<TemplateResult | string> => {
    const out: Array<TemplateResult | string> = [];
    for (let i = 0; i < entries.length; i++) {
      if (i > 0) out.push('\n');
      out.push(entries[i]);
    }
    return out;
  };

  const leftCodeContent = joinLinesWithNewline(leftLineEntries);
  const rightCodeContent = joinLinesWithNewline(rightLineEntries);
  const langA = tokenA.lang ? `language-${tokenA.lang}` : undefined;
  const langB = tokenB.lang ? `language-${tokenB.lang}` : undefined;

  return {
    leftHtml: langA
      ? html`<pre><code class=${langA}>${leftCodeContent}</code></pre>`
      : html`<pre><code>${leftCodeContent}</code></pre>`,
    rightHtml: langB
      ? html`<pre><code class=${langB}>${rightCodeContent}</code></pre>`
      : html`<pre><code>${rightCodeContent}</code></pre>`,
  };
}

/**
 * Render a modified markdown list (<ul> or <ol>) by aligning homologous list
 * items and highlighting within-item word/code/link diffs as well as added or
 * deleted items. Emits placeholder <li> elements on the opposing side so
 * side-by-side alignment and <ol> numbering stay in sync.
 */
export function renderListDiff(
  tokenA: Tokens.List,
  tokenB: Tokens.List
): {leftHtml: TemplateResult; rightHtml: TemplateResult} {
  const itemsA = tokenA.items.map(item => item.text);
  const itemsB = tokenB.items.map(item => item.text);
  const aligned = alignHomologousItems(
    itemsA,
    itemsB,
    tokenizeWords,
    DEFAULT_MAX_HOMOLOGOUS_DISTANCE
  );

  const leftItems: TemplateResult[] = [];
  const rightItems: TemplateResult[] = [];

  for (const item of aligned) {
    if (item.type === 'unchanged') {
      const rendered = html`<li>${renderInlineMarkdown(item.textA)}</li>`;
      leftItems.push(rendered);
      rightItems.push(rendered);
    } else if (item.type === 'modified') {
      const {leftHtml, rightHtml} = renderInlineTextDiff(
        item.textA,
        item.textB,
        'li'
      );
      leftItems.push(leftHtml);
      rightItems.push(rightHtml);
    } else if (item.type === 'deleted') {
      leftItems.push(
        html`<li class="diff-list-item-del">
          <del class="diff-highlight-del"
            >${renderInlineMarkdown(item.textA)}</del
          >
        </li>`
      );
      rightItems.push(
        html`<li class="diff-list-item-placeholder" aria-hidden="true"></li>`
      );
    } else if (item.type === 'added') {
      leftItems.push(
        html`<li class="diff-list-item-placeholder" aria-hidden="true"></li>`
      );
      rightItems.push(
        html`<li class="diff-list-item-add">
          <ins class="diff-highlight-add"
            >${renderInlineMarkdown(item.textB)}</ins
          >
        </li>`
      );
    }
  }

  return {
    leftHtml: tokenA.ordered
      ? html`<ol>
          ${leftItems}
        </ol>`
      : html`<ul>
          ${leftItems}
        </ul>`,
    rightHtml: tokenB.ordered
      ? html`<ol>
          ${rightItems}
        </ol>`
      : html`<ul>
          ${rightItems}
        </ul>`,
  };
}

/** Render a modified blockquote with inline word/code/link diff highlights. */
export function renderBlockquoteDiff(
  tokenA: Tokens.Blockquote,
  tokenB: Tokens.Blockquote
): {leftHtml: TemplateResult; rightHtml: TemplateResult} {
  const {leftHtml, rightHtml} = renderInlineTextDiff(
    tokenA.text.trim(),
    tokenB.text.trim(),
    'p'
  );
  return {
    leftHtml: html`<blockquote>${leftHtml}</blockquote>`,
    rightHtml: html`<blockquote>${rightHtml}</blockquote>`,
  };
}

/**
 * Render a modified markdown table by aligning homologous rows and performing
 * cell-by-cell inline diffs on modified header and body cells, emitting
 * placeholder cells and rows on the opposing side when columns or rows are
 * added or deleted.
 */
export function renderTableDiff(
  tokenA: Tokens.Table,
  tokenB: Tokens.Table
): {leftHtml: TemplateResult; rightHtml: TemplateResult} {
  const colCount = Math.max(
    tokenA.header.length,
    tokenB.header.length,
    ...tokenA.rows.map(r => r.length),
    ...tokenB.rows.map(r => r.length)
  );
  const leftHeaderCells: TemplateResult[] = [];
  const rightHeaderCells: TemplateResult[] = [];

  for (let c = 0; c < colCount; c++) {
    const cellA = tokenA.header[c]?.text;
    const cellB = tokenB.header[c]?.text;
    if (cellA !== undefined && cellB !== undefined) {
      if (cellA === cellB) {
        const th = html`<th>${renderInlineMarkdown(cellA)}</th>`;
        leftHeaderCells.push(th);
        rightHeaderCells.push(th);
      } else {
        const {leftHtml, rightHtml} = renderInlineTextDiff(cellA, cellB, 'th');
        leftHeaderCells.push(leftHtml);
        rightHeaderCells.push(rightHtml);
      }
    } else if (cellA !== undefined) {
      leftHeaderCells.push(
        html`<th>
          <del class="diff-highlight-del">${renderInlineMarkdown(cellA)}</del>
        </th>`
      );
      rightHeaderCells.push(
        html`<th class="diff-table-cell-placeholder" aria-hidden="true"></th>`
      );
    } else if (cellB !== undefined) {
      leftHeaderCells.push(
        html`<th class="diff-table-cell-placeholder" aria-hidden="true"></th>`
      );
      rightHeaderCells.push(
        html`<th>
          <ins class="diff-highlight-add">${renderInlineMarkdown(cellB)}</ins>
        </th>`
      );
    } else {
      leftHeaderCells.push(
        html`<th class="diff-table-cell-placeholder" aria-hidden="true"></th>`
      );
      rightHeaderCells.push(
        html`<th class="diff-table-cell-placeholder" aria-hidden="true"></th>`
      );
    }
  }

  const rowsTextA = tokenA.rows.map(r => r.map(c => c.text).join(' | '));
  const rowsTextB = tokenB.rows.map(r => r.map(c => c.text).join(' | '));
  const alignedRows = alignHomologousItems(
    rowsTextA,
    rowsTextB,
    tokenizeWords,
    DEFAULT_MAX_HOMOLOGOUS_DISTANCE
  );

  const leftBodyRows: TemplateResult[] = [];
  const rightBodyRows: TemplateResult[] = [];

  for (const row of alignedRows) {
    if (row.type === 'unchanged') {
      const trLeft: TemplateResult[] = [];
      const trRight: TemplateResult[] = [];
      const cellsA = tokenA.rows[row.aIdx];
      const cellsB = tokenB.rows[row.bIdx];
      for (let c = 0; c < colCount; c++) {
        const cellA = cellsA[c]?.text;
        const cellB = cellsB[c]?.text;
        trLeft.push(
          cellA !== undefined
            ? html`<td>${renderInlineMarkdown(cellA)}</td>`
            : html`<td
                class="diff-table-cell-placeholder"
                aria-hidden="true"
              ></td>`
        );
        trRight.push(
          cellB !== undefined
            ? html`<td>${renderInlineMarkdown(cellB)}</td>`
            : html`<td
                class="diff-table-cell-placeholder"
                aria-hidden="true"
              ></td>`
        );
      }
      leftBodyRows.push(
        html`<tr>
          ${trLeft}
        </tr>`
      );
      rightBodyRows.push(
        html`<tr>
          ${trRight}
        </tr>`
      );
    } else if (row.type === 'modified') {
      const cellsA = tokenA.rows[row.aIdx];
      const cellsB = tokenB.rows[row.bIdx];
      const trLeft: TemplateResult[] = [];
      const trRight: TemplateResult[] = [];
      for (let c = 0; c < colCount; c++) {
        const textA = cellsA[c]?.text;
        const textB = cellsB[c]?.text;
        if (textA !== undefined && textB !== undefined) {
          if (textA === textB) {
            const td = html`<td>${renderInlineMarkdown(textA)}</td>`;
            trLeft.push(td);
            trRight.push(td);
          } else {
            const {leftHtml, rightHtml} = renderInlineTextDiff(
              textA,
              textB,
              'td'
            );
            trLeft.push(leftHtml);
            trRight.push(rightHtml);
          }
        } else if (textA !== undefined) {
          trLeft.push(
            html`<td>
              <del class="diff-highlight-del"
                >${renderInlineMarkdown(textA)}</del
              >
            </td>`
          );
          trRight.push(
            html`<td
              class="diff-table-cell-placeholder"
              aria-hidden="true"
            ></td>`
          );
        } else if (textB !== undefined) {
          trLeft.push(
            html`<td
              class="diff-table-cell-placeholder"
              aria-hidden="true"
            ></td>`
          );
          trRight.push(
            html`<td>
              <ins class="diff-highlight-add"
                >${renderInlineMarkdown(textB)}</ins
              >
            </td>`
          );
        } else {
          trLeft.push(
            html`<td
              class="diff-table-cell-placeholder"
              aria-hidden="true"
            ></td>`
          );
          trRight.push(
            html`<td
              class="diff-table-cell-placeholder"
              aria-hidden="true"
            ></td>`
          );
        }
      }
      leftBodyRows.push(
        html`<tr>
          ${trLeft}
        </tr>`
      );
      rightBodyRows.push(
        html`<tr>
          ${trRight}
        </tr>`
      );
    } else if (row.type === 'deleted') {
      const cellsA = tokenA.rows[row.aIdx];
      const trLeft: TemplateResult[] = [];
      const trRight: TemplateResult[] = [];
      for (let c = 0; c < colCount; c++) {
        const cellA = cellsA[c]?.text;
        trLeft.push(
          cellA !== undefined
            ? html`<td>
                <del class="diff-highlight-del"
                  >${renderInlineMarkdown(cellA)}</del
                >
              </td>`
            : html`<td
                class="diff-table-cell-placeholder"
                aria-hidden="true"
              ></td>`
        );
        trRight.push(
          html`<td class="diff-table-cell-placeholder" aria-hidden="true"></td>`
        );
      }
      leftBodyRows.push(
        html`<tr class="diff-table-row-del">
          ${trLeft}
        </tr>`
      );
      rightBodyRows.push(
        html`<tr class="diff-table-row-placeholder" aria-hidden="true">
          ${trRight}
        </tr>`
      );
    } else if (row.type === 'added') {
      const cellsB = tokenB.rows[row.bIdx];
      const trLeft: TemplateResult[] = [];
      const trRight: TemplateResult[] = [];
      for (let c = 0; c < colCount; c++) {
        const cellB = cellsB[c]?.text;
        trLeft.push(
          html`<td class="diff-table-cell-placeholder" aria-hidden="true"></td>`
        );
        trRight.push(
          cellB !== undefined
            ? html`<td>
                <ins class="diff-highlight-add"
                  >${renderInlineMarkdown(cellB)}</ins
                >
              </td>`
            : html`<td
                class="diff-table-cell-placeholder"
                aria-hidden="true"
              ></td>`
        );
      }
      leftBodyRows.push(
        html`<tr class="diff-table-row-placeholder" aria-hidden="true">
          ${trLeft}
        </tr>`
      );
      rightBodyRows.push(
        html`<tr class="diff-table-row-add">
          ${trRight}
        </tr>`
      );
    }
  }

  return {
    leftHtml: html`<table>
      <thead>
        <tr>
          ${leftHeaderCells}
        </tr>
      </thead>
      <tbody>
        ${leftBodyRows}
      </tbody>
    </table>`,
    rightHtml: html`<table>
      <thead>
        <tr>
          ${rightHeaderCells}
        </tr>
      </thead>
      <tbody>
        ${rightBodyRows}
      </tbody>
    </table>`,
  };
}

/**
 * Align base (A) and revision (B) markdown block tokens into rows.
 * Matches exact blocks as anchors, pairs modified blocks of compatible types,
 * and leaves unmatched blocks as added or deleted with empty partner cells.
 */
export function alignMarkdownTokens(
  tokensA: MarkdownToken[],
  tokensB: MarkdownToken[]
): AlignedDiffRow[] {
  // Filter out whitespace-only 'space' tokens between blocks
  const blocksA = tokensA.filter(t => t.type !== 'space');
  const blocksB = tokensB.filter(t => t.type !== 'space');

  const m = blocksA.length;
  const n = blocksB.length;

  // DP table to find LCS of exact matches
  const dp: number[][] = Array.from({length: m + 1}, () =>
    new Array(n + 1).fill(0)
  );

  for (let i = 0; i < m; i++) {
    for (let j = 0; j < n; j++) {
      if (blocksA[i].raw === blocksB[j].raw) {
        dp[i + 1][j + 1] = dp[i][j] + 1;
      } else {
        dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
  }

  // Backtrack to extract exact match pairs
  const anchorPairs: {aIdx: number; bIdx: number}[] = [];
  let i = m;
  let j = n;
  while (i > 0 && j > 0) {
    if (blocksA[i - 1].raw === blocksB[j - 1].raw) {
      anchorPairs.unshift({aIdx: i - 1, bIdx: j - 1});
      i--;
      j--;
    } else if (dp[i][j - 1] >= dp[i - 1][j]) {
      j--;
    } else {
      i--;
    }
  }

  const rows: AlignedDiffRow[] = [];
  let lastA = 0;
  let lastB = 0;

  function renderPairedBlocks(
    tokA: MarkdownToken,
    tokB: MarkdownToken
  ): {leftHtml: TemplateResult; rightHtml: TemplateResult} {
    if (tokA.type === 'code' && tokB.type === 'code') {
      return renderCodeBlockDiff(tokA as Tokens.Code, tokB as Tokens.Code);
    }
    if (tokA.type === 'heading' && tokB.type === 'heading') {
      const depthA = Math.min(Math.max((tokA as Tokens.Heading).depth, 1), 6);
      const depthB = Math.min(Math.max((tokB as Tokens.Heading).depth, 1), 6);
      return renderInlineTextDiff(
        tokA.text,
        tokB.text,
        `h${depthA}` as WrapperTag,
        `h${depthB}` as WrapperTag
      );
    }
    if (tokA.type === 'paragraph' && tokB.type === 'paragraph') {
      return renderInlineTextDiff(tokA.text, tokB.text, 'p');
    }
    if (tokA.type === 'list' && tokB.type === 'list') {
      return renderListDiff(tokA as Tokens.List, tokB as Tokens.List);
    }
    if (tokA.type === 'blockquote' && tokB.type === 'blockquote') {
      return renderBlockquoteDiff(
        tokA as Tokens.Blockquote,
        tokB as Tokens.Blockquote
      );
    }
    if (tokA.type === 'table' && tokB.type === 'table') {
      return renderTableDiff(tokA as Tokens.Table, tokB as Tokens.Table);
    }
    return {
      leftHtml: renderTokenToHtml(tokA),
      rightHtml: renderTokenToHtml(tokB),
    };
  }

  function processInterval(endA: number, endB: number) {
    const unalignedA = blocksA.slice(lastA, endA);
    const unalignedB = blocksB.slice(lastB, endB);
    const uA = unalignedA.length;
    const uB = unalignedB.length;
    if (uA === 0 && uB === 0) return;

    const wordsCacheA = unalignedA.map(t => tokenizeWords(t.raw));
    const wordsCacheB = unalignedB.map(t => tokenizeWords(t.raw));

    // Score compatible block pairs by homologous similarity so inserted or
    // deleted blocks do not shift adjacent modified blocks out of alignment.
    const pairScore: number[][] = Array.from({length: uA}, (_, a) =>
      Array.from({length: uB}, (__, b) => {
        if (unalignedA[a].type !== unalignedB[b].type) return -1;
        const dist = computeNormalizedEditDistance(
          wordsCacheA[a],
          wordsCacheB[b]
        );
        if (dist <= DEFAULT_MAX_HOMOLOGOUS_DISTANCE) {
          return 1 + (1 - dist);
        }
        return uA === uB ? 0.01 + 0.1 * (1 - dist) : -1;
      })
    );

    const subDp: number[][] = Array.from({length: uA + 1}, () =>
      new Array(uB + 1).fill(0)
    );
    for (let a = 0; a < uA; a++) {
      for (let b = 0; b < uB; b++) {
        let best = Math.max(subDp[a][b + 1], subDp[a + 1][b]);
        if (pairScore[a][b] >= 0) {
          best = Math.max(best, subDp[a][b] + pairScore[a][b]);
        }
        subDp[a + 1][b + 1] = best;
      }
    }

    const intervalRows: AlignedDiffRow[] = [];
    let a = uA;
    let b = uB;
    while (a > 0 || b > 0) {
      if (
        a > 0 &&
        b > 0 &&
        pairScore[a - 1][b - 1] >= 0 &&
        Math.abs(
          subDp[a][b] - (subDp[a - 1][b - 1] + pairScore[a - 1][b - 1])
        ) < 1e-9
      ) {
        const tokA = unalignedA[a - 1];
        const tokB = unalignedB[b - 1];
        const {leftHtml, rightHtml} = renderPairedBlocks(tokA, tokB);
        intervalRows.unshift({
          status: 'modified',
          leftToken: tokA,
          rightToken: tokB,
          leftHtml,
          rightHtml,
          leftStartLine: tokA.startLine,
          leftEndLine: tokA.endLine,
          rightStartLine: tokB.startLine,
          rightEndLine: tokB.endLine,
        });
        a--;
        b--;
      } else if (b > 0 && (a === 0 || subDp[a][b - 1] >= subDp[a - 1][b])) {
        const tokB = unalignedB[b - 1];
        intervalRows.unshift({
          status: 'added',
          rightToken: tokB,
          rightHtml: renderTokenToHtml(tokB),
          rightStartLine: tokB.startLine,
          rightEndLine: tokB.endLine,
        });
        b--;
      } else {
        const tokA = unalignedA[a - 1];
        intervalRows.unshift({
          status: 'deleted',
          leftToken: tokA,
          leftHtml: renderTokenToHtml(tokA),
          leftStartLine: tokA.startLine,
          leftEndLine: tokA.endLine,
        });
        a--;
      }
    }

    rows.push(...intervalRows);
  }

  // Interleave intervals between anchor pairs
  for (const anchor of anchorPairs) {
    processInterval(anchor.aIdx, anchor.bIdx);
    const tokA = blocksA[anchor.aIdx];
    const tokB = blocksB[anchor.bIdx];
    const htmlA = renderTokenToHtml(tokA);
    const htmlB = renderTokenToHtml(tokB);
    rows.push({
      status: 'unchanged',
      leftToken: tokA,
      rightToken: tokB,
      leftHtml: htmlA,
      rightHtml: htmlB,
      leftStartLine: tokA.startLine,
      leftEndLine: tokA.endLine,
      rightStartLine: tokB.startLine,
      rightEndLine: tokB.endLine,
    });
    lastA = anchor.aIdx + 1;
    lastB = anchor.bIdx + 1;
  }

  // Trailing interval
  processInterval(m, n);

  return rows;
}

/** Determine which side of the diff (LEFT or RIGHT) a comment thread belongs to. */
export function getThreadDiffSide(
  thread: CommentThread,
  patchRange?: PatchRange
): Side {
  if (!patchRange) {
    return thread.commentSide === CommentSide.PARENT ? Side.LEFT : Side.RIGHT;
  }
  const commentProps = {
    patch_set: thread.patchNum,
    side: thread.commentSide,
    parent: thread.mergeParentNum,
  };
  if (isInBaseOfPatchRange(commentProps, patchRange)) {
    return Side.LEFT;
  }
  return Side.RIGHT;
}

/**
 * Assigns comment threads to their corresponding markdown diff rows based on line
 * numbers and diff side. File-level comments (or threads without line numbers) are
 * grouped separately.
 */
export function attachThreadsToRows(
  rows: AlignedDiffRow[],
  threads: CommentThread[] = [],
  patchRange?: PatchRange
): {
  rowsWithThreads: AlignedDiffRowWithThreads[];
  fileLevelThreads: CommentThread[];
} {
  const rowsWithThreads: AlignedDiffRowWithThreads[] = rows.map(r => {
    return {
      ...r,
      leftThreads: [],
      rightThreads: [],
    };
  });

  const fileLevelThreads: CommentThread[] = [];

  if (rowsWithThreads.length === 0) {
    return {
      rowsWithThreads,
      fileLevelThreads: [...threads],
    };
  }

  for (const thread of threads) {
    const line = thread.line;
    if (line === undefined || line === 'FILE') {
      fileLevelThreads.push(thread);
      continue;
    }

    const lineNum = typeof line === 'number' ? line : Number(line);
    if (isNaN(lineNum)) {
      fileLevelThreads.push(thread);
      continue;
    }

    const side = getThreadDiffSide(thread, patchRange);

    if (side === Side.LEFT) {
      const leftRows = rowsWithThreads.filter(
        r => r.leftStartLine !== undefined
      );
      if (leftRows.length === 0) {
        rowsWithThreads[0].leftThreads.push(thread);
        continue;
      }

      const exactRow = leftRows.find(
        r => lineNum >= r.leftStartLine! && lineNum <= r.leftEndLine!
      );
      if (exactRow) {
        exactRow.leftThreads.push(thread);
        continue;
      }

      if (lineNum < leftRows[0].leftStartLine!) {
        leftRows[0].leftThreads.push(thread);
        continue;
      }

      let targetRow = leftRows[leftRows.length - 1];
      for (let i = 0; i < leftRows.length - 1; i++) {
        if (
          lineNum > leftRows[i].leftEndLine! &&
          lineNum < leftRows[i + 1].leftStartLine!
        ) {
          targetRow = leftRows[i];
          break;
        }
      }
      targetRow.leftThreads.push(thread);
    } else {
      const rightRows = rowsWithThreads.filter(
        r => r.rightStartLine !== undefined
      );
      if (rightRows.length === 0) {
        rowsWithThreads[0].rightThreads.push(thread);
        continue;
      }

      const exactRow = rightRows.find(
        r => lineNum >= r.rightStartLine! && lineNum <= r.rightEndLine!
      );
      if (exactRow) {
        exactRow.rightThreads.push(thread);
        continue;
      }

      if (lineNum < rightRows[0].rightStartLine!) {
        rightRows[0].rightThreads.push(thread);
        continue;
      }

      let targetRow = rightRows[rightRows.length - 1];
      for (let i = 0; i < rightRows.length - 1; i++) {
        if (
          lineNum > rightRows[i].rightEndLine! &&
          lineNum < rightRows[i + 1].rightStartLine!
        ) {
          targetRow = rightRows[i];
          break;
        }
      }
      targetRow.rightThreads.push(thread);
    }
  }

  return {rowsWithThreads, fileLevelThreads};
}
