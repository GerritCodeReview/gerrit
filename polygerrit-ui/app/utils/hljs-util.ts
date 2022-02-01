/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {SyntaxLayerRange, UNCLOSED} from '../types/worker-api';

/**
 * Note that this utility is mostly used by the hljs-worker, which is a Web
 * Worker and can thus not depend on document, the DOM or any related
 * functionality.
 */

/**
 * With these expressions you can match excatly what HighlightJS produces. It
 * is really that simple:
 * https://github.com/highlightjs/highlight.js/blob/main/src/lib/html_renderer.js
 */
const openingSpan = new RegExp('<span class="(.*?)">');
const closingSpan = new RegExp('</span>');

/**
 * HighlightJS produces one long HTML string with HTML elements spanning
 * multiple lines. <gr-diff> is line based, needs all elements closed at the end
 * of the line, and is not interested in the HTML that HighlightJS produces.
 *
 * So we are splitting the HTML string up into lines and process them one by
 * one. Each <span> is detected, converted into a SyntaxLayerRange and removed.
 * Unclosed spans will be carried over to the next line.
 */
export function highlightedStringToRanges(
  highlightedCode: string
): SyntaxLayerRange[][] {
  // What the function eventually returns.
  const rangesPerLine: SyntaxLayerRange[][] = [];
  // The unclosed ranges that are carried over from one line to the next.
  let carryOverRanges: SyntaxLayerRange[] = [];

  for (let line of highlightedCode.split('\n')) {
    const ranges: SyntaxLayerRange[] = [...carryOverRanges];
    carryOverRanges = [];
    rangesPerLine.push(ranges);

    // Remove all span tags one after another from left to right.
    // For each opening <span ...> push a new (unclosed) range.
    // For each closing </span> close the latest unclosed range.
    let removal: SpanRemoval | undefined;
    while ((removal = removeFirstSpan(line)) !== undefined) {
      if (removal.type === SpanType.OPENING) {
        ranges.push({
          start: removal.offset,
          length: UNCLOSED,
          className: removal.class ?? '',
        });
      } else {
        const unclosed = lastUnclosed(ranges);
        unclosed.length = removal.offset - unclosed.start;
      }
      line = removal.lineAfter;
    }

    // All unclosed spans need to have the length set such that they extend to
    // the end of the line. And they have to be carried over to the next line
    // as cloned objects with start:0.
    const lineLength = line.length;
    for (const range of ranges) {
      if (range.length === UNCLOSED) {
        carryOverRanges.push({...range, start: 0});
        range.length = lineLength - range.start;
      }
    }
  }
  if (carryOverRanges.length > 0) {
    throw new Error('unclosed <span>s in highlighted code');
  }
  return rangesPerLine;
}

function isUnclosed(range: SyntaxLayerRange) {
  return range.length === UNCLOSED;
}

function lastUnclosed(ranges: SyntaxLayerRange[]) {
  const unclosed = [...ranges].reverse().find(isUnclosed);
  if (!unclosed) throw new Error('no unclosed range found');
  return unclosed;
}

export enum SpanType {
  OPENING,
  CLOSING,
}

export interface SpanRemoval {
  type: SpanType;
  /** The line string after removing the matched span tag. */
  lineAfter: string;
  /** The matched css class for OPENING spans. undefined for CLOSING. */
  class?: string;
  /** At which char in the line did the removed span tag start? */
  offset: number;
}

/**
 * Finds the first <span ...> or </span>, removes it from the line and returns
 * details about the removal. Returns `undefined`, if neither is found.
 */
export function removeFirstSpan(line: string): SpanRemoval | undefined {
  const o = openingSpan.exec(line);
  const oIndex = o?.index ?? Number.MAX_VALUE;
  const c = closingSpan.exec(line);
  const cIndex = c?.index ?? Number.MAX_VALUE;
  if (oIndex === Number.MAX_VALUE && cIndex === Number.MAX_VALUE) {
    return undefined;
  }
  const type = oIndex < cIndex ? SpanType.OPENING : SpanType.CLOSING;
  const offset = type === SpanType.OPENING ? oIndex : cIndex;
  const match = type === SpanType.OPENING ? o : c;
  if (match === null) return undefined;
  const length = match[0].length;
  const removal: SpanRemoval = {
    type,
    lineAfter: line.slice(0, offset) + line.slice(offset + length),
    offset,
    class: type === SpanType.OPENING ? match[1] : undefined,
  };
  return removal;
}
