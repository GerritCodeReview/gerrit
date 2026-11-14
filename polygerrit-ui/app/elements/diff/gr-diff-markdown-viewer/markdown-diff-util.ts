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
import {getDefaults, Lexer, Marked, Token, Tokens} from 'marked';

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
    result.push(t);
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
  if (!innerRaw) {
    return {openDelim: '', closeDelim: ''};
  }
  const symIdx = Math.floor((token.raw.length - innerRaw.length) / 2);
  if (
    symIdx > 0 &&
    token.raw.slice(symIdx, symIdx + innerRaw.length) === innerRaw
  ) {
    return {
      openDelim: token.raw.slice(0, symIdx),
      closeDelim: token.raw.slice(symIdx + innerRaw.length),
    };
  }
  const idx = token.raw.indexOf(innerRaw);
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

function lexInlineIsolated(text: string): Token[] {
  return Lexer.lexInline(text, getDefaults());
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
  const inlineTokens = lexInlineIsolated(text);
  const result: string[] = [];
  tokenizeInlineAstTokens(inlineTokens, result);
  return result;
}

const SAFE_URL_SCHEMES = new Set(['http:', 'https:']);
const INVALID_URL = 'about:invalid#zClosurez';
const RELATIVE_URL_BASE = 'https://relative.invalid/';

/**
 * Sanitize a fenced code block language info string into a single safe
 * `language-*` CSS class name. Prevents whitespace-delimited class injection
 * (e.g., ```` ```lean selection-action-box diff-cell ````) from hijacking
 * viewer styles or Shadow DOM queries.
 */
export function sanitizeCodeLanguage(lang?: string): string | undefined {
  if (!lang) return undefined;
  const firstToken = lang.trim().split(/\s+/)[0] ?? '';
  const safe = firstToken.replace(/[^A-Za-z0-9_+#.-]/g, '').slice(0, 40);
  return safe ? `language-${safe}` : undefined;
}

/**
 * Sanitize a markdown link href so dangerous schemes (such as `javascript:`,
 * `data:`, `vbscript:`, `file:`), protocol-relative/backslash host redirects
 * (`//evil.com`, `/\evil.com`, `\/evil.com`, `\\evil.com`), control-character
 * obfuscation (`ht\ttps://`, `\u0000//`), and HTML entity-obfuscated schemes
 * cannot execute or hijack navigation when rendered in an `<a>` tag.
 * Fails closed to `about:invalid#zClosurez` on malformed inputs.
 */
export function sanitizeMarkdownUrl(url: string): string {
  const trimmed = url.trim();
  if (!trimmed) return '';
  // Reject any ASCII C0/C1 control characters or DEL anywhere in the URL.
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f-\u009f]/.test(trimmed)) {
    return INVALID_URL;
  }
  // Reject protocol-relative (`//host`), backslash-prefixed (`/\host`, `\/host`, `\\host`, `\host`).
  if (/^(?:[/\\]{2}|[/\\]\s*[/\\]|\\)/.test(trimmed)) {
    return INVALID_URL;
  }
  // Reject HTML entity references before any path/query/fragment delimiter
  // (e.g. `&#106;avascript:`, `javascript&colon;`).
  if (/^[^/?#]*&(?:#x?[0-9a-f]+|[a-z]+);/i.test(trimmed)) {
    return INVALID_URL;
  }

  // Explicit scheme check (anything with `:` before `/`, `?`, or `#`).
  if (/^[^/?#]*:/.test(trimmed)) {
    if (!/^https?:\/\//i.test(trimmed)) {
      return INVALID_URL;
    }
    try {
      const parsed = new URL(trimmed);
      if (!SAFE_URL_SCHEMES.has(parsed.protocol.toLowerCase())) {
        return INVALID_URL;
      }
      // Canonicalize if the raw URL contains whitespace or backslashes.
      if (/[\s\\]/.test(trimmed)) {
        return parsed.href;
      }
      return trimmed;
    } catch {
      return INVALID_URL;
    }
  }

  // Relative URL or same-page fragment: verify WHATWG URL parsing cannot
  // escape the synthetic origin (catches host-redirect edge cases).
  try {
    const parsed = new URL(trimmed, RELATIVE_URL_BASE);
    if (parsed.origin !== 'https://relative.invalid') {
      return INVALID_URL;
    }
    return trimmed;
  } catch {
    return INVALID_URL;
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
      const isExternal = /^https?:\/\//i.test(safeHref);
      parts.push(
        isExternal
          ? html`<a href=${safeHref} target="_blank" rel="noopener noreferrer"
              >${labelContent}</a
            >`
          : html`<a href=${safeHref} rel="noopener noreferrer"
              >${labelContent}</a
            >`
      );
    } else if (isInlineContainerToken(token)) {
      parts.push(
        renderInlineContainerTag(
          token.type,
          renderInlineAstTokens(token.tokens, options)
        )
      );
    } else if (token.type === 'br') {
      parts.push(html`<br />`);
    } else if (token.type === 'escape') {
      parts.push((token as Tokens.Escape).text);
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
  return renderInlineAstTokens(lexInlineIsolated(text), {includeLinks: false});
}

/**
 * Render text with markdown links ([label](url)) converted to <a> Lit template
 * elements and inline backtick code spans converted to <code> Lit template
 * elements using Marked's inline AST lexer.
 */
export function renderInlineMarkdown(text: string): TemplateResult {
  if (!text) return html``;
  return renderInlineAstTokens(lexInlineIsolated(text), {includeLinks: true});
}

const MAX_SEQUENCE_DIFF_CELLS = 250_000;
const MAX_EDIT_DISTANCE_CELLS = 40_000;
const MAX_ALIGN_ITEMS_CELLS = 10_000;

/** Compute LCS-based diff between two arrays of strings (e.g. words or lines). */
export function computeSequenceDiff(
  seqA: string[],
  seqB: string[]
): InlineDiffSegment[] {
  const m = seqA.length;
  const n = seqB.length;
  if (m === 0 && n === 0) return [];

  // Trim exact common prefix and suffix to keep near-identical large inputs O(N).
  let prefixLen = 0;
  while (
    prefixLen < m &&
    prefixLen < n &&
    seqA[prefixLen] === seqB[prefixLen]
  ) {
    prefixLen++;
  }
  let suffixLen = 0;
  while (
    suffixLen < m - prefixLen &&
    suffixLen < n - prefixLen &&
    seqA[m - 1 - suffixLen] === seqB[n - 1 - suffixLen]
  ) {
    suffixLen++;
  }

  const segments: InlineDiffSegment[] = [];
  for (let p = 0; p < prefixLen; p++) {
    segments.push({text: seqA[p], type: 'common'});
  }

  const midLenA = m - prefixLen - suffixLen;
  const midLenB = n - prefixLen - suffixLen;

  if (midLenA === 0) {
    for (let j = 0; j < midLenB; j++) {
      segments.push({text: seqB[prefixLen + j], type: 'added'});
    }
  } else if (midLenB === 0) {
    for (let i = 0; i < midLenA; i++) {
      segments.push({text: seqA[prefixLen + i], type: 'deleted'});
    }
  } else if (midLenA * midLenB > MAX_SEQUENCE_DIFF_CELLS) {
    // Bounded linear fallback for adversarial/huge unaligned sequences.
    for (let i = 0; i < midLenA; i++) {
      segments.push({text: seqA[prefixLen + i], type: 'deleted'});
    }
    for (let j = 0; j < midLenB; j++) {
      segments.push({text: seqB[prefixLen + j], type: 'added'});
    }
  } else {
    const cols = midLenB + 1;
    const dp = new Int32Array((midLenA + 1) * cols);

    for (let i = 0; i < midLenA; i++) {
      const aVal = seqA[prefixLen + i];
      const rowOffset = i * cols;
      const nextRowOffset = (i + 1) * cols;
      for (let j = 0; j < midLenB; j++) {
        if (aVal === seqB[prefixLen + j]) {
          dp[nextRowOffset + j + 1] = dp[rowOffset + j] + 1;
        } else {
          const left = dp[nextRowOffset + j];
          const up = dp[rowOffset + j + 1];
          dp[nextRowOffset + j + 1] = left >= up ? left : up;
        }
      }
    }

    const midSegments: InlineDiffSegment[] = [];
    let i = midLenA;
    let j = midLenB;
    while (i > 0 || j > 0) {
      if (
        i > 0 &&
        j > 0 &&
        seqA[prefixLen + i - 1] === seqB[prefixLen + j - 1]
      ) {
        midSegments.push({text: seqA[prefixLen + i - 1], type: 'common'});
        i--;
        j--;
      } else if (
        j > 0 &&
        (i === 0 || dp[i * cols + (j - 1)] >= dp[(i - 1) * cols + j])
      ) {
        midSegments.push({text: seqB[prefixLen + j - 1], type: 'added'});
        j--;
      } else {
        midSegments.push({text: seqA[prefixLen + i - 1], type: 'deleted'});
        i--;
      }
    }
    for (let k = midSegments.length - 1; k >= 0; k--) {
      segments.push(midSegments[k]);
    }
  }

  for (let s = 0; s < suffixLen; s++) {
    segments.push({text: seqA[m - suffixLen + s], type: 'common'});
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

const DEFAULT_CONTENT_TOKEN_RE = /[\p{L}\p{N}_]/u;

function defaultIsContentToken(tok: string): boolean {
  return DEFAULT_CONTENT_TOKEN_RE.test(tok);
}

/**
 * Compute the normalized token edit distance in [0.0, 1.0] between two token
 * sequences, ignoring pure-whitespace and pure-punctuation tokens so spaces and
 * punctuation do not artificially inflate similarity between unrelated
 * sentences.
 */
export function computeNormalizedEditDistance(
  seqA: string[],
  seqB: string[],
  isContentToken: (tok: string) => boolean = defaultIsContentToken
): number {
  const nonWsA = seqA.filter(t => !/^\s+$/.test(t));
  const nonWsB = seqB.filter(t => !/^\s+$/.test(t));
  if (nonWsA.length === 0 && nonWsB.length === 0) return 0;
  if (nonWsA.length === 0 || nonWsB.length === 0) return 1;

  const wordsA = nonWsA.filter(isContentToken);
  const wordsB = nonWsB.filter(isContentToken);
  const contentA = wordsA.length > 0 || wordsB.length > 0 ? wordsA : nonWsA;
  const contentB = wordsA.length > 0 || wordsB.length > 0 ? wordsB : nonWsB;
  if (contentA.length === 0 || contentB.length === 0) return 1;

  let commonCount = 0;
  if (contentA.length * contentB.length > MAX_EDIT_DISTANCE_CELLS) {
    // Linear O(m + n) multiset overlap approximation for very large token lists.
    const countsA = new Map<string, number>();
    for (const tok of contentA) {
      countsA.set(tok, (countsA.get(tok) ?? 0) + 1);
    }
    for (const tok of contentB) {
      const rem = countsA.get(tok) ?? 0;
      if (rem > 0) {
        commonCount++;
        countsA.set(tok, rem - 1);
      }
    }
  } else {
    const diff = computeSequenceDiff(contentA, contentB);
    for (const seg of diff) {
      if (seg.type === 'common') {
        commonCount++;
      }
    }
  }
  const minLen = Math.min(contentA.length, contentB.length);
  const maxLen = Math.max(contentA.length, contentB.length);
  const diceDistance =
    1 - (2 * commonCount) / (contentA.length + contentB.length);
  const overlapDistance = 1 - commonCount / minLen;
  // If one sequence is an extension of the other (high overlap with the shorter
  // sequence, bounded length ratio, and at least 3 shared content tokens),
  // treat them as homologous so shared prefixes/clauses remain unhighlighted.
  if (commonCount >= 3 && maxLen <= minLen * 5 && overlapDistance <= 0.4) {
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
  // in a single linear O(R) stack pass when both edits have deleted text or
  // both edits have added text.
  const coalescedRuns: Run[] = [];
  for (const run of runs) {
    if (coalescedRuns.length >= 2 && run.kind === 'edit') {
      const curr = coalescedRuns[coalescedRuns.length - 1];
      const prev = coalescedRuns[coalescedRuns.length - 2];
      if (
        curr.kind === 'common' &&
        /^\s+$/.test(curr.text) &&
        prev.kind === 'edit'
      ) {
        const bothHaveDel = prev.delText.length > 0 && run.delText.length > 0;
        const bothHaveAdd = prev.addText.length > 0 && run.addText.length > 0;
        const prevIsPair = prev.delText.length > 0 && prev.addText.length > 0;
        const nextIsPair = run.delText.length > 0 && run.addText.length > 0;
        if (
          (bothHaveDel && !prev.addText && !run.addText) ||
          (bothHaveAdd && !prev.delText && !run.delText) ||
          (prevIsPair && nextIsPair)
        ) {
          if (prev.delText || run.delText) {
            prev.delText += curr.text + run.delText;
          }
          if (prev.addText || run.addText) {
            prev.addText += curr.text + run.addText;
          }
          coalescedRuns.pop();
          continue;
        }
      }
    }
    coalescedRuns.push(run);
  }

  // Absorb a trailing punctuation-only common run (e.g. a lone '.') when it
  // immediately follows a multi-word replacement edit that itself contains
  // sentence punctuation or whitespace.
  if (coalescedRuns.length >= 2) {
    const last = coalescedRuns[coalescedRuns.length - 1];
    const prev = coalescedRuns[coalescedRuns.length - 2];
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
      coalescedRuns.pop();
    }
  }

  const out: InlineDiffSegment[] = [];
  for (const r of coalescedRuns) {
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
 * Patience-style exact-match anchor finder for large sequences where O(m * n)
 * DP exceeds the cell budget.
 *
 * 1. Peels matching prefixes and suffixes in each interval.
 * 2. Anchors on keys unique within the interval using Patience LIS
 *    (O(u log u)), preventing a deleted repeated line (e.g. `""`, `end`,
 *    `simp`) from greedily jumping the cursor to the bottom of the file.
 * 3. For residual sub-intervals with zero unique keys, runs small exact LCS DP
 *    within budget or falls back to a monotonic per-key index cursor
 *    (amortized O(gapA + gapB)) with no O(m * n) rescans.
 */
export function computeLinearExactAnchors(
  keysA: string[],
  keysB: string[]
): Array<{aIdx: number; bIdx: number}> {
  const m = keysA.length;
  const n = keysB.length;
  if (m === 0 || n === 0) return [];

  const anchors: Array<{aIdx: number; bIdx: number}> = [];

  const findUniqueLIS = (
    aStart: number,
    aEnd: number,
    bStart: number,
    bEnd: number
  ): Array<{aIdx: number; bIdx: number}> => {
    const countA = new Map<string, number>();
    for (let i = aStart; i < aEnd; i++) {
      const k = keysA[i];
      countA.set(k, (countA.get(k) ?? 0) + 1);
    }
    const posB = new Map<string, number>();
    for (let j = bStart; j < bEnd; j++) {
      const k = keysB[j];
      if (countA.get(k) !== 1) continue;
      if (posB.has(k)) {
        posB.set(k, -1);
      } else {
        posB.set(k, j);
      }
    }
    const pairs: Array<{aIdx: number; bIdx: number}> = [];
    for (let i = aStart; i < aEnd; i++) {
      const bIdx = posB.get(keysA[i]);
      if (bIdx !== undefined && bIdx >= 0) {
        pairs.push({aIdx: i, bIdx});
      }
    }
    if (pairs.length <= 1) return pairs;

    const tailPairIdx: number[] = [];
    const prevPairIdx = new Int32Array(pairs.length).fill(-1);
    for (let idx = 0; idx < pairs.length; idx++) {
      const bVal = pairs[idx].bIdx;
      let lo = 0;
      let hi = tailPairIdx.length;
      while (lo < hi) {
        const mid = (lo + hi) >>> 1;
        if (pairs[tailPairIdx[mid]].bIdx < bVal) {
          lo = mid + 1;
        } else {
          hi = mid;
        }
      }
      if (lo > 0) prevPairIdx[idx] = tailPairIdx[lo - 1];
      tailPairIdx[lo] = idx;
    }

    const lis: Array<{aIdx: number; bIdx: number}> = [];
    let cur = tailPairIdx[tailPairIdx.length - 1];
    while (cur >= 0) {
      lis.push(pairs[cur]);
      cur = prevPairIdx[cur];
    }
    lis.reverse();
    return lis;
  };

  type StackEntry =
    | {kind: 'emit'; pairs: Array<{aIdx: number; bIdx: number}>}
    | {
        kind: 'gap';
        aStart: number;
        aEnd: number;
        bStart: number;
        bEnd: number;
        depth: number;
      };

  const stack: StackEntry[] = [
    {kind: 'gap', aStart: 0, aEnd: m, bStart: 0, bEnd: n, depth: 0},
  ];
  const MAX_PATIENCE_DEPTH = 32;

  while (stack.length > 0) {
    const entry = stack.pop()!;
    if (entry.kind === 'emit') {
      for (const p of entry.pairs) anchors.push(p);
      continue;
    }

    let {aStart, aEnd, bStart, bEnd} = entry;
    const {depth} = entry;

    while (aStart < aEnd && bStart < bEnd && keysA[aStart] === keysB[bStart]) {
      anchors.push({aIdx: aStart, bIdx: bStart});
      aStart++;
      bStart++;
    }

    const suffixPairs: Array<{aIdx: number; bIdx: number}> = [];
    while (
      aEnd > aStart &&
      bEnd > bStart &&
      keysA[aEnd - 1] === keysB[bEnd - 1]
    ) {
      aEnd--;
      bEnd--;
      suffixPairs.push({aIdx: aEnd, bIdx: bEnd});
    }
    if (suffixPairs.length > 0) {
      suffixPairs.reverse();
      stack.push({kind: 'emit', pairs: suffixPairs});
    }

    const gapA = aEnd - aStart;
    const gapB = bEnd - bStart;
    if (gapA <= 0 || gapB <= 0) continue;

    const uniqueLis =
      depth < MAX_PATIENCE_DEPTH
        ? findUniqueLIS(aStart, aEnd, bStart, bEnd)
        : [];
    if (uniqueLis.length > 0) {
      const subEntries: StackEntry[] = [];
      let curA = aStart;
      let curB = bStart;
      for (const u of uniqueLis) {
        if (curA < u.aIdx && curB < u.bIdx) {
          subEntries.push({
            kind: 'gap',
            aStart: curA,
            aEnd: u.aIdx,
            bStart: curB,
            bEnd: u.bIdx,
            depth: depth + 1,
          });
        }
        subEntries.push({kind: 'emit', pairs: [u]});
        curA = u.aIdx + 1;
        curB = u.bIdx + 1;
      }
      if (curA < aEnd && curB < bEnd) {
        subEntries.push({
          kind: 'gap',
          aStart: curA,
          aEnd,
          bStart: curB,
          bEnd,
          depth: depth + 1,
        });
      }
      for (let idx = subEntries.length - 1; idx >= 0; idx--) {
        stack.push(subEntries[idx]);
      }
      continue;
    }

    if (gapA * gapB <= MAX_ALIGN_ITEMS_CELLS) {
      const cols = gapB + 1;
      const dp = new Int32Array((gapA + 1) * cols);
      for (let i = 0; i < gapA; i++) {
        const keyA = keysA[aStart + i];
        const rowOff = i * cols;
        const nextRowOff = (i + 1) * cols;
        for (let j = 0; j < gapB; j++) {
          if (keyA === keysB[bStart + j]) {
            dp[nextRowOff + j + 1] = dp[rowOff + j] + 1;
          } else {
            const left = dp[nextRowOff + j];
            const up = dp[rowOff + j + 1];
            dp[nextRowOff + j + 1] = left >= up ? left : up;
          }
        }
      }
      const gapAnchors: Array<{aIdx: number; bIdx: number}> = [];
      let i = gapA;
      let j = gapB;
      while (i > 0 && j > 0) {
        if (keysA[aStart + i - 1] === keysB[bStart + j - 1]) {
          gapAnchors.push({aIdx: aStart + i - 1, bIdx: bStart + j - 1});
          i--;
          j--;
        } else if (dp[i * cols + (j - 1)] >= dp[(i - 1) * cols + j]) {
          j--;
        } else {
          i--;
        }
      }
      gapAnchors.reverse();
      for (const p of gapAnchors) anchors.push(p);
      continue;
    }

    // Monotonic per-key index cursor fallback for huge residual gaps with zero
    // unique keys (strictly O(gapA + gapB) amortized).
    const positionsByKey = new Map<string, number[]>();
    for (let j = bStart; j < bEnd; j++) {
      const k = keysB[j];
      let list = positionsByKey.get(k);
      if (!list) {
        list = [];
        positionsByKey.set(k, list);
      }
      list.push(j);
    }
    const cursorByKey = new Map<string, number>();
    let bCursor = bStart;
    for (let i = aStart; i < aEnd; i++) {
      const k = keysA[i];
      const list = positionsByKey.get(k);
      if (!list) continue;
      let posIdx = cursorByKey.get(k) ?? 0;
      while (posIdx < list.length && list[posIdx] < bCursor) {
        posIdx++;
      }
      if (posIdx < list.length) {
        const matchB = list[posIdx];
        anchors.push({aIdx: i, bIdx: matchB});
        bCursor = matchB + 1;
        cursorByKey.set(k, posIdx + 1);
      } else {
        cursorByKey.set(k, posIdx);
      }
    }
  }

  return anchors;
}

/**
 * Align two lists of items using dynamic programming over normalized edit
 * distance, pairing items only when their distance is <= maxDistance.
 */
export function alignHomologousItems(
  itemsA: string[],
  itemsB: string[],
  tokenizer: (text: string) => string[] = tokenizeWords,
  maxDistance = DEFAULT_MAX_HOMOLOGOUS_DISTANCE,
  isContentToken: (tok: string) => boolean = defaultIsContentToken
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
  type AlignedItem =
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
    | {type: 'added'; textB: string; bIdx: number};

  const m = itemsA.length;
  const n = itemsB.length;
  if (m === 0 && n === 0) return [];

  // Trim exact common prefix and suffix first.
  let prefixLen = 0;
  while (
    prefixLen < m &&
    prefixLen < n &&
    itemsA[prefixLen] === itemsB[prefixLen]
  ) {
    prefixLen++;
  }
  let suffixLen = 0;
  while (
    suffixLen < m - prefixLen &&
    suffixLen < n - prefixLen &&
    itemsA[m - 1 - suffixLen] === itemsB[n - 1 - suffixLen]
  ) {
    suffixLen++;
  }

  const result: AlignedItem[] = [];
  for (let p = 0; p < prefixLen; p++) {
    result.push({
      type: 'unchanged',
      textA: itemsA[p],
      textB: itemsB[p],
      aIdx: p,
      bIdx: p,
    });
  }

  const mm = m - prefixLen - suffixLen;
  const nn = n - prefixLen - suffixLen;

  if (mm === 0) {
    for (let j = 0; j < nn; j++) {
      result.push({
        type: 'added',
        textB: itemsB[prefixLen + j],
        bIdx: prefixLen + j,
      });
    }
  } else if (nn === 0) {
    for (let i = 0; i < mm; i++) {
      result.push({
        type: 'deleted',
        textA: itemsA[prefixLen + i],
        aIdx: prefixLen + i,
      });
    }
  } else if (mm * nn > MAX_ALIGN_ITEMS_CELLS) {
    // Patience-style exact-anchor fallback to avoid O(m * n) DoS on huge blocks
    // while preventing deleted repeated lines from jumping bCursor to EOF.
    const midA = itemsA.slice(prefixLen, m - suffixLen);
    const midB = itemsB.slice(prefixLen, n - suffixLen);
    const anchors = computeLinearExactAnchors(midA, midB);
    let aCursor = 0;
    let bCursor = 0;
    for (const anchor of anchors) {
      while (aCursor < anchor.aIdx) {
        result.push({
          type: 'deleted',
          textA: itemsA[prefixLen + aCursor],
          aIdx: prefixLen + aCursor,
        });
        aCursor++;
      }
      while (bCursor < anchor.bIdx) {
        result.push({
          type: 'added',
          textB: itemsB[prefixLen + bCursor],
          bIdx: prefixLen + bCursor,
        });
        bCursor++;
      }
      result.push({
        type: 'unchanged',
        textA: itemsA[prefixLen + anchor.aIdx],
        textB: itemsB[prefixLen + anchor.bIdx],
        aIdx: prefixLen + anchor.aIdx,
        bIdx: prefixLen + anchor.bIdx,
      });
      aCursor = anchor.aIdx + 1;
      bCursor = anchor.bIdx + 1;
    }
    while (aCursor < mm) {
      result.push({
        type: 'deleted',
        textA: itemsA[prefixLen + aCursor],
        aIdx: prefixLen + aCursor,
      });
      aCursor++;
    }
    while (bCursor < nn) {
      result.push({
        type: 'added',
        textB: itemsB[prefixLen + bCursor],
        bIdx: prefixLen + bCursor,
      });
      bCursor++;
    }
  } else {
    const midA = itemsA.slice(prefixLen, m - suffixLen);
    const midB = itemsB.slice(prefixLen, n - suffixLen);
    const tokensCacheA = midA.map(tokenizer);
    const tokensCacheB = midB.map(tokenizer);

    // Score matrix: pairing (i, j) yields a positive score of (1 - distance)
    // when distance <= maxDistance.
    const pairScore: number[][] = Array.from({length: mm}, (_, i) =>
      Array.from({length: nn}, (__, j) => {
        if (midA[i] === midB[j]) return 2; // Strongly prefer exact matches
        const dist = computeNormalizedEditDistance(
          tokensCacheA[i],
          tokensCacheB[j],
          isContentToken
        );
        return dist <= maxDistance ? 1 - dist : -1;
      })
    );

    const dp: number[][] = Array.from({length: mm + 1}, () =>
      new Array(nn + 1).fill(0)
    );

    for (let i = 0; i < mm; i++) {
      for (let j = 0; j < nn; j++) {
        let best = Math.max(dp[i][j + 1], dp[i + 1][j]);
        if (pairScore[i][j] >= 0) {
          best = Math.max(best, dp[i][j] + pairScore[i][j]);
        }
        dp[i + 1][j + 1] = best;
      }
    }

    const midResult: AlignedItem[] = [];
    let i = mm;
    let j = nn;
    while (i > 0 || j > 0) {
      if (
        i > 0 &&
        j > 0 &&
        pairScore[i - 1][j - 1] >= 0 &&
        Math.abs(dp[i][j] - (dp[i - 1][j - 1] + pairScore[i - 1][j - 1])) < 1e-9
      ) {
        const aIdx = prefixLen + i - 1;
        const bIdx = prefixLen + j - 1;
        midResult.push({
          type: itemsA[aIdx] === itemsB[bIdx] ? 'unchanged' : 'modified',
          textA: itemsA[aIdx],
          textB: itemsB[bIdx],
          aIdx,
          bIdx,
        });
        i--;
        j--;
      } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
        const bIdx = prefixLen + j - 1;
        midResult.push({type: 'added', textB: itemsB[bIdx], bIdx});
        j--;
      } else {
        const aIdx = prefixLen + i - 1;
        midResult.push({type: 'deleted', textA: itemsA[aIdx], aIdx});
        i--;
      }
    }
    for (let k = midResult.length - 1; k >= 0; k--) {
      result.push(midResult[k]);
    }
  }

  for (let s = 0; s < suffixLen; s++) {
    const aIdx = m - suffixLen + s;
    const bIdx = n - suffixLen + s;
    result.push({
      type: 'unchanged',
      textA: itemsA[aIdx],
      textB: itemsB[bIdx],
      aIdx,
      bIdx,
    });
  }

  return result;
}

function renderListContainer(
  listTok: Tokens.List,
  items: TemplateResult[]
): TemplateResult {
  if (!listTok.ordered) {
    return html`<ul>
      ${items}
    </ul>`;
  }
  return typeof listTok.start === 'number' && listTok.start !== 1
    ? html`<ol start=${listTok.start}>
        ${items}
      </ol>`
    : html`<ol>
        ${items}
      </ol>`;
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
      const langClass = sanitizeCodeLanguage(codeTok.lang);
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
      return renderListContainer(listTok, items);
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

    if (allEdited || (anyEdited && token.type === 'escape')) {
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
    lexInlineIsolated(fullText),
    0,
    isEditedAt,
    highlightTag,
    highlightClass
  );
}

function renderInlineTextDiffContent(
  textA: string,
  textB: string
): {leftContent: TemplateResult; rightContent: TemplateResult} {
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

  return {leftContent, rightContent};
}

/** Render an inline diff for modified text in headings or paragraphs. */
export function renderInlineTextDiff(
  textA: string,
  textB: string,
  wrapperTag: WrapperTag = 'p',
  rightWrapperTag: WrapperTag = wrapperTag
): {leftHtml: TemplateResult; rightHtml: TemplateResult} {
  const {leftContent, rightContent} = renderInlineTextDiffContent(textA, textB);
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
  const langA = sanitizeCodeLanguage(tokenA.lang);
  const langB = sanitizeCodeLanguage(tokenB.lang);

  return {
    leftHtml: langA
      ? html`<pre><code class=${langA}>${leftCodeContent}</code></pre>`
      : html`<pre><code>${leftCodeContent}</code></pre>`,
    rightHtml: langB
      ? html`<pre><code class=${langB}>${rightCodeContent}</code></pre>`
      : html`<pre><code>${rightCodeContent}</code></pre>`,
  };
}

function splitListItemTokens(item: Tokens.ListItem): {
  headText: string;
  sublists: Tokens.List[];
  otherBlocks: Token[];
} {
  if (!item.tokens || item.tokens.length === 0) {
    return {headText: item.text, sublists: [], otherBlocks: []};
  }
  const headParts: string[] = [];
  const sublists: Tokens.List[] = [];
  const otherBlocks: Token[] = [];
  for (const t of item.tokens) {
    if (t.type === 'text' || t.type === 'paragraph') {
      headParts.push((t as Tokens.Text | Tokens.Paragraph).text);
    } else if (t.type === 'list') {
      sublists.push(t as Tokens.List);
    } else if (t.type !== 'space') {
      otherBlocks.push(t);
    }
  }
  return {
    headText: headParts.join('\n'),
    sublists,
    otherBlocks,
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
  const splitItemsA = tokenA.items.map(splitListItemTokens);
  const splitItemsB = tokenB.items.map(splitListItemTokens);
  const itemsA = splitItemsA.map(
    (s, idx) => s.headText || tokenA.items[idx].text
  );
  const itemsB = splitItemsB.map(
    (s, idx) => s.headText || tokenB.items[idx].text
  );
  const aligned = alignHomologousItems(
    itemsA,
    itemsB,
    tokenizeWords,
    DEFAULT_MAX_HOMOLOGOUS_DISTANCE
  );

  const leftItems: TemplateResult[] = [];
  const rightItems: TemplateResult[] = [];

  for (const item of aligned) {
    if (item.type === 'unchanged' || item.type === 'modified') {
      const splitA = splitItemsA[item.aIdx];
      const splitB = splitItemsB[item.bIdx];
      let leftHead: TemplateResult;
      let rightHead: TemplateResult;
      if (splitA.headText === splitB.headText) {
        leftHead = renderInlineMarkdown(splitA.headText);
        rightHead = leftHead;
      } else {
        const diffContent = renderInlineTextDiffContent(
          splitA.headText,
          splitB.headText
        );
        leftHead = diffContent.leftContent;
        rightHead = diffContent.rightContent;
      }

      const leftSublists: TemplateResult[] = [];
      const rightSublists: TemplateResult[] = [];
      const maxSub = Math.max(splitA.sublists.length, splitB.sublists.length);
      for (let s = 0; s < maxSub; s++) {
        const subA = splitA.sublists[s];
        const subB = splitB.sublists[s];
        if (subA && subB) {
          const subDiff = renderListDiff(subA, subB);
          leftSublists.push(subDiff.leftHtml);
          rightSublists.push(subDiff.rightHtml);
        } else if (subA) {
          leftSublists.push(
            renderListDiff(subA, {...subA, items: []}).leftHtml
          );
        } else if (subB) {
          rightSublists.push(
            renderListDiff({...subB, items: []}, subB).rightHtml
          );
        }
      }

      const leftOther = splitA.otherBlocks.map(t => renderTokenToHtml(t));
      const rightOther = splitB.otherBlocks.map(t => renderTokenToHtml(t));
      leftItems.push(html`<li>${leftHead}${leftOther}${leftSublists}</li>`);
      rightItems.push(html`<li>${rightHead}${rightOther}${rightSublists}</li>`);
    } else if (item.type === 'deleted') {
      const splitA = splitItemsA[item.aIdx];
      const sublists = splitA.sublists.map(
        sub => renderListDiff(sub, {...sub, items: []}).leftHtml
      );
      const other = splitA.otherBlocks.map(t => renderTokenToHtml(t));
      leftItems.push(
        html`<li class="diff-list-item-del">
          <del class="diff-highlight-del"
            >${renderInlineMarkdown(splitA.headText)}</del
          >${other}${sublists}
        </li>`
      );
      rightItems.push(
        html`<li class="diff-list-item-placeholder" aria-hidden="true"></li>`
      );
    } else if (item.type === 'added') {
      const splitB = splitItemsB[item.bIdx];
      const sublists = splitB.sublists.map(
        sub => renderListDiff({...sub, items: []}, sub).rightHtml
      );
      const other = splitB.otherBlocks.map(t => renderTokenToHtml(t));
      leftItems.push(
        html`<li class="diff-list-item-placeholder" aria-hidden="true"></li>`
      );
      rightItems.push(
        html`<li class="diff-list-item-add">
          <ins class="diff-highlight-add"
            >${renderInlineMarkdown(splitB.headText)}</ins
          >${other}${sublists}
        </li>`
      );
    }
  }

  return {
    leftHtml: renderListContainer(tokenA, leftItems),
    rightHtml: renderListContainer(tokenB, rightItems),
  };
}

/** Render a modified blockquote with inline word/code/link diff highlights. */
export function renderBlockquoteDiff(
  tokenA: Tokens.Blockquote,
  tokenB: Tokens.Blockquote
): {leftHtml: TemplateResult; rightHtml: TemplateResult} {
  const childTokensA = (
    tokenA.tokens && tokenA.tokens.length > 0
      ? tokenA.tokens
      : parseMarkdownBlocks(tokenA.text)
  ).filter(t => t.type !== 'space') as MarkdownToken[];
  const childTokensB = (
    tokenB.tokens && tokenB.tokens.length > 0
      ? tokenB.tokens
      : parseMarkdownBlocks(tokenB.text)
  ).filter(t => t.type !== 'space') as MarkdownToken[];

  if (
    childTokensA.length <= 1 &&
    childTokensB.length <= 1 &&
    (childTokensA[0]?.type ?? 'paragraph') === 'paragraph' &&
    (childTokensB[0]?.type ?? 'paragraph') === 'paragraph'
  ) {
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

  const alignedChildren = alignMarkdownTokens(childTokensA, childTokensB);
  const leftBlocks: TemplateResult[] = [];
  const rightBlocks: TemplateResult[] = [];
  for (const row of alignedChildren) {
    if (row.status === 'unchanged' || row.status === 'modified') {
      if (row.leftHtml) leftBlocks.push(row.leftHtml);
      if (row.rightHtml) rightBlocks.push(row.rightHtml);
    } else if (row.status === 'deleted' && row.leftToken) {
      if (row.leftToken.type === 'paragraph') {
        leftBlocks.push(
          renderInlineTextDiff(
            (row.leftToken as Tokens.Paragraph).text,
            '',
            'p'
          ).leftHtml
        );
      } else if (row.leftHtml) {
        leftBlocks.push(
          html`<del class="diff-highlight-del">${row.leftHtml}</del>`
        );
      }
    } else if (row.status === 'added' && row.rightToken) {
      if (row.rightToken.type === 'paragraph') {
        rightBlocks.push(
          renderInlineTextDiff(
            '',
            (row.rightToken as Tokens.Paragraph).text,
            'p'
          ).rightHtml
        );
      } else if (row.rightHtml) {
        rightBlocks.push(
          html`<ins class="diff-highlight-add">${row.rightHtml}</ins>`
        );
      }
    }
  }

  return {
    leftHtml: html`<blockquote>${leftBlocks}</blockquote>`,
    rightHtml: html`<blockquote>${rightBlocks}</blockquote>`,
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

  const rowsTextA = tokenA.rows.map(r => r.map(c => c.text).join(' '));
  const rowsTextB = tokenB.rows.map(r => r.map(c => c.text).join(' '));
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

  // Extract exact match anchor pairs (bounded to prevent O(m * n) DoS).
  const anchorPairs: {aIdx: number; bIdx: number}[] = [];
  if (m * n <= MAX_SEQUENCE_DIFF_CELLS) {
    const cols = n + 1;
    const dp = new Int32Array((m + 1) * cols);

    for (let i = 0; i < m; i++) {
      const rawA = blocksA[i].raw;
      const rowOffset = i * cols;
      const nextRowOffset = (i + 1) * cols;
      for (let j = 0; j < n; j++) {
        if (rawA === blocksB[j].raw) {
          dp[nextRowOffset + j + 1] = dp[rowOffset + j] + 1;
        } else {
          const left = dp[nextRowOffset + j];
          const up = dp[rowOffset + j + 1];
          dp[nextRowOffset + j + 1] = left >= up ? left : up;
        }
      }
    }

    let i = m;
    let j = n;
    while (i > 0 && j > 0) {
      if (blocksA[i - 1].raw === blocksB[j - 1].raw) {
        anchorPairs.push({aIdx: i - 1, bIdx: j - 1});
        i--;
        j--;
      } else if (dp[i * cols + (j - 1)] >= dp[(i - 1) * cols + j]) {
        j--;
      } else {
        i--;
      }
    }
    anchorPairs.reverse();
  } else {
    anchorPairs.push(
      ...computeLinearExactAnchors(
        blocksA.map(b => b.raw),
        blocksB.map(b => b.raw)
      )
    );
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

    if (uA * uB > MAX_ALIGN_ITEMS_CELLS) {
      for (const tokA of unalignedA) {
        rows.push({
          status: 'deleted',
          leftToken: tokA,
          leftHtml: renderTokenToHtml(tokA),
          leftStartLine: tokA.startLine,
          leftEndLine: tokA.endLine,
        });
      }
      for (const tokB of unalignedB) {
        rows.push({
          status: 'added',
          rightToken: tokB,
          rightHtml: renderTokenToHtml(tokB),
          rightStartLine: tokB.startLine,
          rightEndLine: tokB.endLine,
        });
      }
      return;
    }

    const tokenizeBlock = (t: MarkdownToken): string[] =>
      t.type === 'code'
        ? tokenizeCodeWords((t as Tokens.Code).text)
        : tokenizeWords(t.raw);
    const wordsCacheA = unalignedA.map(tokenizeBlock);
    const wordsCacheB = unalignedB.map(tokenizeBlock);

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
        return uA === 1 && uB === 1 ? 0.01 + 0.1 * (1 - dist) : -1;
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
