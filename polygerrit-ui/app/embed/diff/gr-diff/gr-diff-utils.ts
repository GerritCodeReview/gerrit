/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BlameInfo, CommentRange} from '../../../types/common';
import {FILE, LineNumber} from './gr-diff-line';
import {Side} from '../../../constants/constants';
import {DiffInfo} from '../../../types/diff';
import {
  DiffPreferencesInfo,
  DiffResponsiveMode,
  RenderPreferences,
} from '../../../api/diff';
import {getBaseUrl} from '../../../utils/url-util';

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

// If any line of the diff is more than the character limit, then disable
// syntax highlighting for the entire file.
export const SYNTAX_MAX_LINE_LENGTH = 500;

export function countLines(diff?: DiffInfo, side?: Side) {
  if (!diff?.content || !side) return 0;
  return diff.content.reduce((sum, chunk) => {
    const sideChunk = side === Side.LEFT ? chunk.a : chunk.b;
    return sum + (sideChunk?.length ?? chunk.ab?.length ?? chunk.skip ?? 0);
  }, 0);
}

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
  if (!lineNumber) return 0;
  if (lineNumber === 'LOST') return 0;
  if (lineNumber === 'FILE') return 0;
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
  if (lineNumberStr === 'LOST') return 'LOST';
  const lineNumber = Number(lineNumberStr);
  return Number.isInteger(lineNumber) ? lineNumber : null;
}

export function getLine(threadEl: HTMLElement): LineNumber {
  const lineAtt = threadEl.getAttribute('line-num');
  if (lineAtt === 'LOST') return lineAtt;
  if (!lineAtt || lineAtt === 'FILE') return FILE;
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
  if (!range.start_line) throw new Error(`invalid range: ${rangeAtt}`);
  return range;
}

// TODO: This type should be exposed to gr-diff clients in a separate type file.
// For Gerrit these are instances of GrCommentThread, but other gr-diff users
// have different HTML elements in use for comment threads.
// TODO: Also document the required HTML attributes that thread elements must
// have, e.g. 'diff-side', 'range', 'line-num'.
export interface GrDiffThreadElement extends HTMLElement {
  rootId: string;
}

export function isThreadEl(node: Node): node is GrDiffThreadElement {
  return (
    node.nodeType === Node.ELEMENT_NODE &&
    (node as Element).classList.contains('comment-thread')
  );
}

/**
 * @return whether any of the lines in diff are longer
 * than SYNTAX_MAX_LINE_LENGTH.
 */
export function anyLineTooLong(diff?: DiffInfo) {
  if (!diff) return false;
  return diff.content.some(section => {
    const lines = section.ab
      ? section.ab
      : (section.a || []).concat(section.b || []);
    return lines.some(line => line.length >= SYNTAX_MAX_LINE_LENGTH);
  });
}

/**
 * Simple helper method for creating element classes in the context of
 * gr-diff. This is just a super simple convenience function.
 */
export function diffClasses(...additionalClasses: string[]) {
  return ['gr-diff', ...additionalClasses].join(' ');
}

/**
 * Simple helper method for creating elements in the context of gr-diff.
 * This is just a super simple convenience function.
 */
export function createElementDiff(
  tagName: string,
  classStr?: string
): HTMLElement {
  const el = document.createElement(tagName);

  el.classList.add('gr-diff');
  if (classStr) {
    for (const className of classStr.split(' ')) {
      el.classList.add(className);
    }
  }
  return el;
}

export function createElementDiffWithText(
  tagName: string,
  textContent: string
) {
  const element = createElementDiff(tagName);
  element.textContent = textContent;
  return element;
}

export function createLineBreak(mode: DiffResponsiveMode) {
  return isResponsive(mode)
    ? createElementDiff('wbr')
    : createElementDiff('span', 'br');
}

/**
 * Returns a <span> element holding a '\t' character, that will visually
 * occupy |tabSize| many columns.
 *
 * @param tabSize The effective size of this tab stop.
 */
export function createTabWrapper(tabSize: number): HTMLElement {
  // Force this to be a number to prevent arbitrary injection.
  const result = createElementDiff('span', 'tab');
  result.setAttribute(
    'style',
    `tab-size: ${tabSize}; -moz-tab-size: ${tabSize};`
  );
  result.innerText = '\t';
  return result;
}

/**
 * Returns a 'div' element containing the supplied |text| as its innerText,
 * with '\t' characters expanded to a width determined by |tabSize|, and the
 * text wrapped at column |lineLimit|, which may be Infinity if no wrapping is
 * desired.
 *
 * @param text The text to be formatted.
 * @param responsiveMode The responsive mode of the diff.
 * @param tabSize The width of each tab stop.
 * @param lineLimit The column after which to wrap lines.
 */
export function formatText(
  text: string,
  responsiveMode: DiffResponsiveMode,
  tabSize: number,
  lineLimit: number,
  elementId: string
): HTMLElement {
  const contentText = createElementDiff('div', 'contentText');
  // <gr-legacy-text> is not defined anywhere, so this behave just as a <div>
  // would. We use this during the migration to lit based diff elements to
  // match <gr-diff-text>. We define a css rule with `display:contents` making
  // sure that this extra element is basically a no-op.
  const legacyText = document.createElement('gr-legacy-text');
  contentText.appendChild(legacyText);
  contentText.id = elementId;
  let columnPos = 0;
  let textOffset = 0;
  for (const segment of text.split(REGEX_TAB_OR_SURROGATE_PAIR)) {
    if (segment) {
      // |segment| contains only normal characters. If |segment| doesn't fit
      // entirely on the current line, append chunks of |segment| followed by
      // line breaks.
      let rowStart = 0;
      let rowEnd = lineLimit - columnPos;
      while (rowEnd < segment.length) {
        legacyText.appendChild(
          document.createTextNode(segment.substring(rowStart, rowEnd))
        );
        legacyText.appendChild(createLineBreak(responsiveMode));
        columnPos = 0;
        rowStart = rowEnd;
        rowEnd += lineLimit;
      }
      // Append the last part of |segment|, which fits on the current line.
      legacyText.appendChild(
        document.createTextNode(segment.substring(rowStart))
      );
      columnPos += segment.length - rowStart;
      textOffset += segment.length;
    }
    if (textOffset < text.length) {
      // Handle the special character at |textOffset|.
      if (text.startsWith('\t', textOffset)) {
        // Append a single '\t' character.
        let effectiveTabSize = tabSize - (columnPos % tabSize);
        if (columnPos + effectiveTabSize > lineLimit) {
          legacyText.appendChild(createLineBreak(responsiveMode));
          columnPos = 0;
          effectiveTabSize = tabSize;
        }
        legacyText.appendChild(createTabWrapper(effectiveTabSize));
        columnPos += effectiveTabSize;
        textOffset++;
      } else {
        // Append a single surrogate pair.
        if (columnPos >= lineLimit) {
          legacyText.appendChild(createLineBreak(responsiveMode));
          columnPos = 0;
        }
        legacyText.appendChild(
          document.createTextNode(text.substring(textOffset, textOffset + 2))
        );
        textOffset += 2;
        columnPos += 1;
      }
    }
  }
  return contentText;
}

/**
 * Given the number of a base line and the BlameInfo create a <span> element
 * with a hovercard. This is supposed to be put into a <td> cell of the diff.
 */
export function createBlameElement(
  lineNum: LineNumber,
  commit: BlameInfo
): HTMLElement {
  const isStartOfRange = commit.ranges.some(r => r.start === lineNum);

  const date = new Date(commit.time * 1000).toLocaleDateString();
  const blameNode = createElementDiff(
    'span',
    isStartOfRange ? 'startOfRange' : ''
  );

  const shaNode = createElementDiff('a', 'blameDate');
  shaNode.innerText = `${date}`;
  shaNode.setAttribute('href', `${getBaseUrl()}/q/${commit.id}`);
  blameNode.appendChild(shaNode);

  const shortName = commit.author.split(' ')[0];
  const authorNode = createElementDiff('span', 'blameAuthor');
  authorNode.innerText = ` ${shortName}`;
  blameNode.appendChild(authorNode);

  const hoverCardFragment = createElementDiff('span', 'blameHoverCard');
  hoverCardFragment.innerText = `Commit ${commit.id}
Author: ${commit.author}
Date: ${date}

${commit.commit_msg}`;
  const hovercard = createElementDiff('gr-hovercard');
  hovercard.appendChild(hoverCardFragment);
  blameNode.appendChild(hovercard);

  return blameNode;
}

/**
 * Get the approximate length of the diff as the sum of the maximum
 * length of the chunks.
 */
export function getDiffLength(diff?: DiffInfo) {
  if (!diff) return 0;
  return diff.content.reduce((sum, sec) => {
    if (sec.ab) {
      return sum + sec.ab.length;
    } else {
      return sum + Math.max(sec.a?.length ?? 0, sec.b?.length ?? 0);
    }
  }, 0);
}
