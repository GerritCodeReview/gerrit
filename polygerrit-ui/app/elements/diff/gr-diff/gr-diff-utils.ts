/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {CommentRange} from '../../../types/common';
import {FILE, LineNumber} from './gr-diff-line';
import {Side} from '../../../constants/constants';
import {DiffInfo} from '../../../types/diff';

// If any line of the diff is more than the character limit, then disable
// syntax highlighting for the entire file.
export const SYNTAX_MAX_LINE_LENGTH = 500;

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
    node = node.previousSibling ?? node.parentElement ?? undefined;
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
  // TODO(dhruvsri): Remove check for comment-side once all users of gr-diff
  // start setting diff-side
  const sideAtt =
    threadEl.getAttribute('diff-side') || threadEl.getAttribute('comment-side');
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

const VISIBLE_TEXT_NODE_TYPES = [Node.TEXT_NODE, Node.ELEMENT_NODE];

export function getPreviousContentNodes(node?: Node | null) {
  const sibs = [];
  while (node) {
    const {parentNode, previousSibling} = node;
    const topContentLevel =
      parentNode &&
      (parentNode as HTMLElement).classList.contains('contentText');
    let previousEl: Node | undefined | null;
    if (previousSibling) {
      previousEl = previousSibling;
    } else if (!topContentLevel) {
      previousEl = parentNode?.previousSibling;
    }
    if (previousEl && VISIBLE_TEXT_NODE_TYPES.includes(previousEl.nodeType)) {
      sibs.push(previousEl);
    }
    node = previousEl;
  }
  return sibs;
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
