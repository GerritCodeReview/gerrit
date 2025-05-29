/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BlameInfo, CommentRange} from '../../../types/common';
import {Side, SpecialFilePath} from '../../../constants/constants';
import {
  DiffContextExpandedExternalDetail,
  DiffPreferencesInfo,
  DiffResponsiveMode,
  DisplayLine,
  FILE,
  LineNumber,
  LOST,
  RenderPreferences,
} from '../../../api/diff';
import {GrDiffGroup} from './gr-diff-group';

/**
 * In JS, unicode code points above 0xFFFF occupy two elements of a string.
 * For example '𐀏'.length is 2. An occurrence of such a code point is called a
 * surrogate pair.
 *
 * This regex segments a string along tabs ('\t') and surrogate pairs, since
 * these are two cases where '1 char' does not automatically imply '1 column'.
 *
 * TODO: For human languages whose orthographies use combining marks, this
 * approach won't correctly identify the grapheme boundaries. In those cases,
 * a grapheme consists of multiple code points that should count as only one
 * character against the column limit. Getting that correct (if it's desired)
 * is probably beyond the limits of a regex, but there are nonstandard APIs to
 * do this, and proposed (but, as of Nov 2017, unimplemented) standard APIs.
 *
 * Further reading:
 *   On Unicode in JS: https://mathiasbynens.be/notes/javascript-unicode
 *   Graphemes: http://unicode.org/reports/tr29/#Grapheme_Cluster_Boundaries
 *   A proposed JS API: https://github.com/tc39/proposal-intl-segmenter
 */
export const REGEX_TAB_OR_SURROGATE_PAIR = /\t|[\uD800-\uDBFF][\uDC00-\uDFFF]/;

export function getResponsiveMode(
  prefs?: DiffPreferencesInfo,
  renderPrefs?: RenderPreferences
): DiffResponsiveMode {
  if (renderPrefs?.responsive_mode) {
    return renderPrefs.responsive_mode;
  }
  // Backwards compatibility to the line_wrapping param.
  if (prefs?.line_wrapping) {
    return 'FULL_RESPONSIVE';
  }
  return 'NONE';
}

export function isResponsive(responsiveMode?: DiffResponsiveMode) {
  return (
    responsiveMode === 'FULL_RESPONSIVE' || responsiveMode === 'SHRINK_ONLY'
  );
}

/**
 * Compare two ranges. Either argument may be falsy, but will only return
 * true if both are falsy or if neither are falsy and have the same position
 * values.
 */
export function rangesEqual(a?: CommentRange, b?: CommentRange): boolean {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  return (
    a.start_line === b.start_line &&
    a.start_character === b.start_character &&
    a.end_line === b.end_line &&
    a.end_character === b.end_character
  );
}

export function isLongCommentRange(range: CommentRange): boolean {
  return range.end_line - range.start_line > 10;
}

export function getLineNumberByChild(node?: Node) {
  return getLineNumber(getLineElByChild(node));
}

export function lineNumberToNumber(lineNumber?: LineNumber | null): number {
  if (typeof lineNumber !== 'number') return 0;
  return lineNumber;
}

export function getLineElByChild(node?: Node): HTMLElement | null {
  while (node) {
    if (node instanceof Element) {
      if (node.classList.contains('lineNum')) {
        return node as HTMLElement;
      }
      if (node.classList.contains('section')) {
        return null;
      }
    }
    node =
      (node as Element).assignedSlot ??
      (node as ShadowRoot).host ??
      node.previousSibling ??
      node.parentNode ??
      undefined;
  }
  return null;
}

export function getSideByLineEl(lineEl: Element) {
  return lineEl.classList.contains(Side.RIGHT) ? Side.RIGHT : Side.LEFT;
}

export function getLineNumber(lineEl?: Element | null): LineNumber | null {
  if (!lineEl) return null;
  const lineNumberStr = lineEl.getAttribute('data-value');
  if (!lineNumberStr) return null;
  if (lineNumberStr === FILE) return FILE;
  if (lineNumberStr === LOST) return LOST;
  const lineNumber = Number(lineNumberStr);
  return Number.isInteger(lineNumber) ? lineNumber : null;
}

export function getLine(threadEl: HTMLElement): LineNumber {
  const lineAtt = threadEl.getAttribute('line-num');
  if (lineAtt === LOST) return lineAtt;
  if (!lineAtt || lineAtt === FILE) return FILE;
  const line = Number(lineAtt);
  if (isNaN(line)) throw new Error(`cannot parse line number: ${lineAtt}`);
  if (line < 1) throw new Error(`line number smaller than 1: ${line}`);
  return line;
}

export function getSide(threadEl: HTMLElement): Side | undefined {
  const sideAtt = threadEl.getAttribute('diff-side');
  if (!sideAtt) {
    console.warn('comment thread without side');
    return undefined;
  }
  if (sideAtt !== Side.LEFT && sideAtt !== Side.RIGHT)
    throw Error(`unexpected value for side: ${sideAtt}`);
  return sideAtt as Side;
}

export function getRange(threadEl: HTMLElement): CommentRange | undefined {
  const rangeAtt = threadEl.getAttribute('range');
  if (!rangeAtt) return undefined;
  const range = JSON.parse(rangeAtt) as CommentRange;
  if (!range.start_line) return undefined;
  return range;
}

/**
 * This is all the data that gr-diff extracts from comment thread elements,
 * see `GrDiffThreadElement`. Otherwise gr-diff treats such elements as a black
 * box.
 */
export interface GrDiffCommentThread {
  side: Side;
  line: LineNumber;
  range?: CommentRange;
  rootId?: string;
}

/**
 * Retrieves all the data from a comment thread element that the gr-diff API
 * contract defines for such elements.
 */
export function getDataFromCommentThreadEl(
  threadEl?: EventTarget | null
): GrDiffCommentThread | undefined {
  if (!isThreadEl(threadEl)) return undefined;
  const side = getSide(threadEl);
  const line = getLine(threadEl);
  const range = getRange(threadEl);
  if (!side) return undefined;
  if (!line) return undefined;
  return {side, line, range, rootId: threadEl.rootId};
}

export interface KeyLocations {
  left: {[key: string]: boolean};
  right: {[key: string]: boolean};
}

/**
 * "Context" is the number of lines that we are showing around diff chunks and
 * commented lines. This typically comes from a user preference and is set to
 * something like 3 or 10.
 *
 * `FULL_CONTEXT` means that the user wants to see the entire file. We could
 * also call this "infinite context".
 */
export const FULL_CONTEXT = -1;

export enum FullContext {
  /** User has opted into showing the full context. */
  YES = 'YES',
  /** User has opted into showing only limited context. */
  NO = 'NO',
  /**
   * User has not decided yet. Will see a warning message with two options then,
   * if the file is too large.
   */
  UNDECIDED = 'UNDECIDED',
}

export function computeContext(
  prefsContext: number | undefined,
  showFullContext: FullContext,
  defaultContext: number
) {
  if (showFullContext === FullContext.YES) {
    return FULL_CONTEXT;
  }
  if (
    prefsContext !== undefined &&
    !(showFullContext === FullContext.NO && prefsContext === FULL_CONTEXT)
  ) {
    return prefsContext;
  }
  return defaultContext;
}

export function computeLineLength(
  prefs: DiffPreferencesInfo,
  path: string | undefined
): number {
  if (path === SpecialFilePath.COMMIT_MESSAGE) {
    return 72;
  }
  const lineLength = prefs.line_length;
  if (Number.isInteger(lineLength) && lineLength > 0) {
    return lineLength;
  }
  return 100;
}

export function computeKeyLocations(
  lineOfInterest: DisplayLine | undefined,
  comments: GrDiffCommentThread[]
) {
  const keyLocations: KeyLocations = {left: {}, right: {}};

  if (lineOfInterest) {
    keyLocations[lineOfInterest.side][lineOfInterest.lineNum] = true;
  }

  for (const comment of comments) {
    keyLocations[comment.side][comment.line] = true;
    if (comment.range?.start_line) {
      keyLocations[comment.side][comment.range.start_line] = true;
    }
  }

  return keyLocations;
}

export function compareComments(
  c1: GrDiffCommentThread,
  c2: GrDiffCommentThread
): number {
  if (c1.side !== c2.side) {
    return c1.side === Side.RIGHT ? 1 : -1;
  }

  if (c1.line !== c2.line) {
    if (c1.line === FILE && c2.line !== FILE) return -1;
    if (c1.line !== FILE && c2.line === FILE) return 1;
    if (c1.line === LOST && c2.line !== LOST) return -1;
    if (c1.line !== LOST && c2.line === LOST) return 1;
    return (c1.line as number) - (c2.line as number);
  }

  if (c1.rootId !== c2.rootId) {
    if (!c1.rootId) return -1;
    if (!c2.rootId) return 1;
    return c1.rootId > c2.rootId ? 1 : -1;
  }

  if (c1.range && c2.range) {
    const r1 = JSON.stringify(c1.range);
    const r2 = JSON.stringify(c2.range);
    return r1 > r2 ? 1 : -1;
  }
  if (c1.range) return 1;
  if (c2.range) return -1;

  return 0;
}

// TODO: This type should be exposed to gr-diff clients in a separate type file.
// For Gerrit these are instances of GrCommentThread, but other gr-diff users
// have different HTML elements in use for comment threads.
// TODO: Also document the required HTML attributes that thread elements must
// have, e.g. 'diff-side', 'range' (optional), 'line-num'.
// Comment widgets are also required to have `comment-thread` in their css
// class list.
export interface GrDiffThreadElement extends HTMLElement {
  rootId: string;
}

export function isThreadEl(
  node?: Node | EventTarget | null
): node is GrDiffThreadElement {
  return (
    !!node &&
    (node as Node).nodeType === Node.ELEMENT_NODE &&
    (node as Element).classList.contains('comment-thread')
  );
}

export interface DiffContextExpandedEventDetail
  extends DiffContextExpandedExternalDetail {
  /** The context control group that should be replaced by `groups`. */
  contextGroup: GrDiffGroup;
  groups: GrDiffGroup[];
  numLines: number;
}

export function findBlame(blameInfos: BlameInfo[], line?: LineNumber) {
  if (typeof line !== 'number') return undefined;
  return blameInfos.find(info =>
    info.ranges.find(range => range.start <= line && line <= range.end)
  );
}
