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
import {htmlEscape} from '../../../utils/inner-html-util';
import {Marked, Token, Tokens} from 'marked';

export type DiffBlockStatus = 'unchanged' | 'added' | 'deleted' | 'modified';

export type MarkdownToken = Token & {
  startLine?: number;
  endLine?: number;
  text?: string;
  ordered?: boolean;
  itemNumber?: number;
  depth?: number;
  task?: boolean;
  checked?: boolean;
};

export interface AlignedDiffRow {
  status: DiffBlockStatus;
  leftToken?: MarkdownToken;
  rightToken?: MarkdownToken;
  leftHtml?: string;
  rightHtml?: string;
  unifiedHtml?: string;
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

/** Reconstruct the entire file content from diff chunks for the given side. */
export function reconstructFileContent(diff: DiffInfo, side: Side): string {
  return getDiffLines(diff, side).join('\n');
}

/** Extract clean text for a list item, excluding marker and task checkbox. */
export function getCleanListItemText(tok: MarkdownToken): string {
  const text = 'text' in tok && typeof tok.text === 'string' ? tok.text : '';
  if (text !== '') {
    return text.replace(/^\[[ xX]\]\s*/, '').trim();
  }
  return tok.raw.replace(/^\s*(\d+[.)]|[*\-+])(\s+\[[ xX]\])?\s*/, '').trim();
}

/** Wrap list item <li> HTML in <ol start="N" class="markdown-diff-list"> or <ul class="markdown-diff-list">. */
export function wrapListItem(liContent: string, tok: MarkdownToken): string {
  const depthClass = tok.depth ? ` depth-${tok.depth}` : '';
  const depthStyle = tok.depth
    ? ` style="margin-left: ${tok.depth * 24}px;"`
    : '';
  if (tok.ordered) {
    const start = tok.itemNumber ?? 1;
    return `<ol start="${start}" class="markdown-diff-list${depthClass}"${depthStyle}>${liContent}</ol>`;
  } else {
    return `<ul class="markdown-diff-list${depthClass}"${depthStyle}>${liContent}</ul>`;
  }
}

/** Decompose a marked list token into individual list item tokens with exact line bounds. */
function decomposeListToken(
  list: Tokens.List,
  startLine: number,
  depth = 0
): {tokens: MarkdownToken[]; nextLine: number} {
  const result: MarkdownToken[] = [];
  let lineCursor = startLine;
  let currentNumber =
    typeof list.start === 'number'
      ? list.start
      : parseInt(String(list.start), 10) || 1;

  for (const item of list.items) {
    const childLists = item.tokens
      ? (item.tokens.filter(t => t.type === 'list') as Tokens.List[])
      : [];

    if (childLists.length === 0) {
      const newlines = (item.raw.match(/\n/g) || []).length;
      const itemStart = lineCursor;
      const itemEnd = itemStart + newlines - (item.raw.endsWith('\n') ? 1 : 0);
      lineCursor += newlines === 0 ? 1 : newlines;

      const itemNumber = list.ordered ? currentNumber++ : undefined;
      const cleanText = getCleanListItemText(item as unknown as MarkdownToken);

      const itemToken: MarkdownToken = {
        ...item,
        type: 'list_item',
        raw: item.raw,
        text: cleanText,
        tokens: item.tokens,
        startLine: itemStart,
        endLine: Math.max(itemStart, itemEnd),
        ordered: list.ordered,
        itemNumber,
        depth,
        task: item.task,
        checked: item.checked,
      };
      result.push(itemToken);
    } else {
      // Child list present: separate parent sentence from child lists
      const firstChildRaw = childLists[0].raw;
      const childIdx = item.raw.indexOf(firstChildRaw);
      const parentRaw =
        childIdx !== -1 ? item.raw.slice(0, childIdx) : item.raw;
      const parentNewlines = (parentRaw.match(/\n/g) || []).length;
      const parentStart = lineCursor;
      const parentEnd =
        parentStart + parentNewlines - (parentRaw.endsWith('\n') ? 1 : 0);
      lineCursor += parentNewlines === 0 ? 1 : parentNewlines;

      const parentTokens = item.tokens
        ? item.tokens.filter(t => t.type !== 'list')
        : [];
      const parentItemNumber = list.ordered ? currentNumber++ : undefined;

      const cleanText = parentRaw
        .replace(/^\s*(\d+[.)]|[*\-+])(\s+\[[ xX]\])?\s*/, '')
        .trimEnd();

      const parentToken: MarkdownToken = {
        type: 'list_item',
        raw: parentRaw,
        text: cleanText,
        tokens: parentTokens,
        startLine: parentStart,
        endLine: Math.max(parentStart, parentEnd),
        ordered: list.ordered,
        itemNumber: parentItemNumber,
        depth,
        task: item.task,
        checked: item.checked,
        loose: item.loose,
      };
      result.push(parentToken);

      // Decompose each nested child list
      for (const childList of childLists) {
        const childRes = decomposeListToken(childList, lineCursor, depth + 1);
        result.push(...childRes.tokens);
        lineCursor = childRes.nextLine;
      }

      // Track any trailing newlines in item.raw after the last child list
      const lastChild = childLists[childLists.length - 1];
      const lastIdx = item.raw.indexOf(lastChild.raw);
      if (lastIdx !== -1) {
        const trailingRaw = item.raw.slice(lastIdx + lastChild.raw.length);
        const trailingNewlines = (trailingRaw.match(/\n/g) || []).length;
        lineCursor += trailingNewlines;
      }
    }
  }

  return {tokens: result, nextLine: lineCursor};
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

    if (t.type === 'space') {
      curLine += newlineCount;
      continue;
    }

    if (t.type === 'list') {
      const decomposed = decomposeListToken(t as Tokens.List, startLine, 0);
      result.push(...decomposed.tokens);
      curLine = Math.max(curLine + newlineCount, decomposed.nextLine);
      continue;
    }

    curLine += newlineCount;
    (t as MarkdownToken).startLine = startLine;
    (t as MarkdownToken).endLine = Math.max(startLine, endLine);
    result.push(t as MarkdownToken);
  }
  return result;
}

/** Tokenize text into words, whitespace, and punctuation for fine-grained inline diffing. */
export function tokenizeWords(text: string): string[] {
  return text.match(/\s+|[^\s\w]+|\w+/g) || [];
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

/** Render a single marked token to standard HTML using marked. */
export function renderTokenToHtml(token?: Token): string {
  if (!token) return '';
  if (token.type === 'list_item') {
    const mdToken = token as MarkdownToken;
    const cleanText = getCleanListItemText(mdToken);
    const marked = new Marked();
    const parsedText = marked.parseInline(cleanText);
    const checkedAttr = mdToken.checked ? ' checked=""' : '';
    const check = mdToken.task
      ? `<input type="checkbox" disabled=""${checkedAttr}> `
      : '';
    const taskClass = mdToken.task ? ' class="task-list-item"' : '';
    const li = `<li${taskClass}>${check}${parsedText}</li>`;
    return wrapListItem(li, mdToken);
  }
  const marked = new Marked();
  return marked.parse(token.raw, {async: false}) || '';
}

/** Render an inline diff for modified text in headings or paragraphs. */
export function renderInlineTextDiff(
  textA: string,
  textB: string,
  wrapperTag = 'p'
): {leftHtml: string; rightHtml: string; unifiedHtml: string} {
  const wordsA = tokenizeWords(textA);
  const wordsB = tokenizeWords(textB);
  const diff = computeSequenceDiff(wordsA, wordsB);

  // Merge consecutive segments of the same type
  const mergedSegments: InlineDiffSegment[] = [];
  for (const seg of diff) {
    const last = mergedSegments[mergedSegments.length - 1];
    if (last && last.type === seg.type) {
      last.text += seg.text;
    } else {
      mergedSegments.push({...seg});
    }
  }

  let leftContent = '';
  let rightContent = '';
  let unifiedContent = '';

  for (const seg of mergedSegments) {
    const escaped = htmlEscape(seg.text).toString();
    if (seg.type === 'common') {
      leftContent += escaped;
      rightContent += escaped;
      unifiedContent += escaped;
    } else if (seg.type === 'deleted') {
      leftContent += `<del class="diff-highlight-del">${escaped}</del>`;
      unifiedContent += `<del class="diff-highlight-del">${escaped}</del>`;
    } else if (seg.type === 'added') {
      rightContent += `<ins class="diff-highlight-add">${escaped}</ins>`;
      unifiedContent += `<ins class="diff-highlight-add">${escaped}</ins>`;
    }
  }

  return {
    leftHtml: `<${wrapperTag}>${leftContent}</${wrapperTag}>`,
    rightHtml: `<${wrapperTag}>${rightContent}</${wrapperTag}>`,
    unifiedHtml: `<${wrapperTag}>${unifiedContent}</${wrapperTag}>`,
  };
}

/** Render a modified code block with line-by-line diff highlights. */
export function renderCodeBlockDiff(
  tokenA: Tokens.Code,
  tokenB: Tokens.Code
): {leftHtml: string; rightHtml: string} {
  const linesA = tokenA.text.split('\n');
  const linesB = tokenB.text.split('\n');
  const lineDiff = computeSequenceDiff(linesA, linesB);

  let leftLines = '';
  let rightLines = '';

  for (const seg of lineDiff) {
    const escaped = htmlEscape(seg.text).toString();
    if (seg.type === 'common') {
      leftLines += `${escaped}\n`;
      rightLines += `${escaped}\n`;
    } else if (seg.type === 'deleted') {
      leftLines += `<span class="diff-highlight-del">${escaped}</span>\n`;
    } else if (seg.type === 'added') {
      rightLines += `<span class="diff-highlight-add">${escaped}</span>\n`;
    }
  }

  const langClass = tokenB.lang
    ? ` class="language-${htmlEscape(tokenB.lang)}"`
    : '';
  return {
    leftHtml: `<pre><code${langClass}>${leftLines.trimEnd()}</code></pre>`,
    rightHtml: `<pre><code${langClass}>${rightLines.trimEnd()}</code></pre>`,
  };
}

/** Render an inline diff for modified list items with <del> and <ins> tags. */
export function renderListItemDiff(
  tokA: MarkdownToken,
  tokB: MarkdownToken
): {leftHtml: string; rightHtml: string; unifiedHtml: string} {
  const textA = getCleanListItemText(tokA);
  const textB = getCleanListItemText(tokB);
  const wordsA = tokenizeWords(textA);
  const wordsB = tokenizeWords(textB);
  const diff = computeSequenceDiff(wordsA, wordsB);

  // Merge consecutive segments of the same type
  const mergedSegments: InlineDiffSegment[] = [];
  for (const seg of diff) {
    const last = mergedSegments[mergedSegments.length - 1];
    if (last && last.type === seg.type) {
      last.text += seg.text;
    } else {
      mergedSegments.push({...seg});
    }
  }

  let leftContent = '';
  let rightContent = '';
  let unifiedContent = '';

  for (const seg of mergedSegments) {
    const escaped = htmlEscape(seg.text).toString();
    if (seg.type === 'common') {
      leftContent += escaped;
      rightContent += escaped;
      unifiedContent += escaped;
    } else if (seg.type === 'deleted') {
      leftContent += `<del class="diff-highlight-del">${escaped}</del>`;
      unifiedContent += `<del class="diff-highlight-del">${escaped}</del>`;
    } else if (seg.type === 'added') {
      rightContent += `<ins class="diff-highlight-add">${escaped}</ins>`;
      unifiedContent += `<ins class="diff-highlight-add">${escaped}</ins>`;
    }
  }

  const checkedA = tokA.checked ? ' checked=""' : '';
  const checkA = tokA.task
    ? `<input type="checkbox" disabled=""${checkedA}> `
    : '';
  const checkedB = tokB.checked ? ' checked=""' : '';
  const checkB = tokB.task
    ? `<input type="checkbox" disabled=""${checkedB}> `
    : '';
  const checkedUnified = (tokB.task ? tokB.checked : tokA.checked)
    ? ' checked=""'
    : '';
  const checkUnified =
    tokB.task || tokA.task
      ? `<input type="checkbox" disabled=""${checkedUnified}> `
      : '';

  const classA = tokA.task ? ' class="task-list-item"' : '';
  const classB = tokB.task ? ' class="task-list-item"' : '';
  const classUnified = tokB.task || tokA.task ? ' class="task-list-item"' : '';

  const leftLi = `<li${classA}>${checkA}${leftContent}</li>`;
  const rightLi = `<li${classB}>${checkB}${rightContent}</li>`;
  const unifiedLi = `<li${classUnified}>${checkUnified}${unifiedContent}</li>`;

  return {
    leftHtml: wrapListItem(leftLi, tokA),
    rightHtml: wrapListItem(rightLi, tokB),
    unifiedHtml: wrapListItem(unifiedLi, tokB),
  };
}

/** Check if two tokens represent the same unchanged block content. */
export function areTokensEqual(
  tokA: MarkdownToken,
  tokB: MarkdownToken
): boolean {
  if (tokA.type !== tokB.type) return false;
  if (tokA.type === 'list_item') {
    if (Boolean(tokA.ordered) !== Boolean(tokB.ordered)) return false;
    if ((tokA.depth ?? 0) !== (tokB.depth ?? 0)) return false;
    if (Boolean(tokA.task) !== Boolean(tokB.task)) return false;
    if (tokA.task && tokA.checked !== tokB.checked) return false;
    return getCleanListItemText(tokA) === getCleanListItemText(tokB);
  }
  return tokA.raw === tokB.raw;
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
      if (areTokensEqual(blocksA[i], blocksB[j])) {
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
    if (areTokensEqual(blocksA[i - 1], blocksB[j - 1])) {
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

  function processInterval(endA: number, endB: number) {
    const unalignedA = blocksA.slice(lastA, endA);
    const unalignedB = blocksB.slice(lastB, endB);

    let idxA = 0;
    let idxB = 0;

    // Greedily pair up adjacent tokens of compatible type as 'modified'
    while (idxA < unalignedA.length && idxB < unalignedB.length) {
      const tokA = unalignedA[idxA];
      const tokB = unalignedB[idxB];

      const canPair =
        (tokA.type === 'list_item' &&
          tokB.type === 'list_item' &&
          Boolean(tokA.ordered) === Boolean(tokB.ordered) &&
          (tokA.depth ?? 0) === (tokB.depth ?? 0)) ||
        (tokA.type === 'paragraph' && tokB.type === 'paragraph') ||
        (tokA.type === 'heading' && tokB.type === 'heading') ||
        (tokA.type === 'code' && tokB.type === 'code');

      if (canPair) {
        let leftHtml: string;
        let rightHtml: string;
        let unifiedHtml: string | undefined;

        if (tokA.type === 'list_item' && tokB.type === 'list_item') {
          const diff = renderListItemDiff(tokA, tokB);
          leftHtml = diff.leftHtml;
          rightHtml = diff.rightHtml;
          unifiedHtml = diff.unifiedHtml;
        } else if (tokA.type === 'code' && tokB.type === 'code') {
          const diff = renderCodeBlockDiff(
            tokA as Tokens.Code,
            tokB as Tokens.Code
          );
          leftHtml = diff.leftHtml;
          rightHtml = diff.rightHtml;
        } else if (tokA.type === 'heading' && tokB.type === 'heading') {
          const depth = (tokB as Tokens.Heading).depth;
          const diff = renderInlineTextDiff(
            (tokA as Tokens.Heading).text ?? '',
            (tokB as Tokens.Heading).text ?? '',
            `h${depth}`
          );
          leftHtml = diff.leftHtml;
          rightHtml = diff.rightHtml;
          unifiedHtml = diff.unifiedHtml;
        } else if (tokA.type === 'paragraph' && tokB.type === 'paragraph') {
          const diff = renderInlineTextDiff(
            (tokA as Tokens.Paragraph).text ?? '',
            (tokB as Tokens.Paragraph).text ?? '',
            'p'
          );
          leftHtml = diff.leftHtml;
          rightHtml = diff.rightHtml;
          unifiedHtml = diff.unifiedHtml;
        } else {
          leftHtml = renderTokenToHtml(tokA);
          rightHtml = renderTokenToHtml(tokB);
        }

        rows.push({
          status: 'modified',
          leftToken: tokA,
          rightToken: tokB,
          leftHtml,
          rightHtml,
          unifiedHtml,
          leftStartLine: tokA.startLine,
          leftEndLine: tokA.endLine,
          rightStartLine: tokB.startLine,
          rightEndLine: tokB.endLine,
        });
        idxA++;
        idxB++;
      } else {
        // Output deleted block on left
        rows.push({
          status: 'deleted',
          leftToken: tokA,
          leftHtml: renderTokenToHtml(tokA),
          leftStartLine: tokA.startLine,
          leftEndLine: tokA.endLine,
        });
        idxA++;
      }
    }

    // Remaining unmatched in A
    while (idxA < unalignedA.length) {
      const tokA = unalignedA[idxA];
      rows.push({
        status: 'deleted',
        leftToken: tokA,
        leftHtml: renderTokenToHtml(tokA),
        leftStartLine: tokA.startLine,
        leftEndLine: tokA.endLine,
      });
      idxA++;
    }

    // Remaining unmatched in B
    while (idxB < unalignedB.length) {
      const tokB = unalignedB[idxB];
      rows.push({
        status: 'added',
        rightToken: tokB,
        rightHtml: renderTokenToHtml(tokB),
        rightStartLine: tokB.startLine,
        rightEndLine: tokB.endLine,
      });
      idxB++;
    }
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
      unifiedHtml: htmlB,
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
