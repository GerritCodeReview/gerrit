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

export function countLines(diff?: DiffInfo, side?: Side) {
  if (!diff?.content || !side) return 0;
  return diff.content.reduce((sum, chunk) => {
    const sideChunk = side === Side.LEFT ? chunk.a : chunk.b;
    return sum + (sideChunk?.length ?? chunk.ab?.length ?? chunk.skip ?? 0);
  }, 0);
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
