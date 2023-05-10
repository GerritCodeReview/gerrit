/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Side} from '../constants/constants';
import {DiffInfo} from '../types/diff';

// If any line of the diff is more than the character limit, then disable
// syntax highlighting for the entire file.
export const SYNTAX_MAX_LINE_LENGTH = 500;

export function otherSide(side: Side) {
  return side === Side.LEFT ? Side.RIGHT : Side.LEFT;
}

export function countLines(diff?: DiffInfo, side?: Side) {
  if (!diff?.content || !side) return 0;
  return diff.content.reduce((sum, chunk) => {
    const sideChunk = side === Side.LEFT ? chunk.a : chunk.b;
    return sum + (sideChunk?.length ?? chunk.ab?.length ?? chunk.skip ?? 0);
  }, 0);
}

function getDiffLines(diff: DiffInfo, side: Side): string[] {
  let lines: string[] = [];
  for (const chunk of diff.content) {
    if (chunk.skip) {
      lines = lines.concat(Array(chunk.skip).fill(''));
    } else if (chunk.ab) {
      lines = lines.concat(chunk.ab);
    } else if (side === Side.LEFT && chunk.a) {
      lines = lines.concat(chunk.a);
    } else if (side === Side.RIGHT && chunk.b) {
      lines = lines.concat(chunk.b);
    }
  }
  return lines;
}

export function getContentFromDiff(
  diff: DiffInfo,
  startLineNum: number,
  startOffset: number,
  endLineNum: number | undefined,
  endOffset: number,
  side: Side
) {
  const lines = getDiffLines(diff, side).slice(startLineNum - 1, endLineNum);
  if (lines.length) {
    lines[lines.length - 1] = lines[lines.length - 1].substring(0, endOffset);
    lines[0] = lines[0].substring(startOffset);
  }
  return lines.join('\n');
}

export function isFileUnchanged(diff: DiffInfo) {
  return !diff.content.some(
    content => (content.a && !content.common) || (content.b && !content.common)
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
 * Get the approximate length of the diff as the sum of the maximum
 * length of the chunks.
 */
export function getDiffLength(diff?: DiffInfo): number {
  if (!diff) return 0;
  return diff.content.reduce((sum, sec) => {
    if (sec.ab) {
      return sum + sec.ab.length;
    } else {
      return sum + Math.max(sec.a?.length ?? 0, sec.b?.length ?? 0);
    }
  }, 0);
}

export function isImageDiff(diff?: DiffInfo) {
  if (!diff) return false;

  const isA = diff.meta_a?.content_type.startsWith('image/');
  const isB = diff.meta_b?.content_type.startsWith('image/');

  return !!(diff.binary && (isA || isB));
}
