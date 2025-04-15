/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Side} from '../constants/constants';
import {DiffInfo} from '../types/diff';

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

export function isLineUnchanged(
  diff?: DiffInfo,
  side?: Side,
  line?: number
): boolean {
  if (!diff?.content || !side || !line) return false;
  let currentLine = 0;
  for (const chunk of diff.content) {
    if (chunk.skip) {
      currentLine += chunk.skip;
      if (currentLine >= line) return false;
    } else if (chunk.ab) {
      currentLine += chunk.ab.length;
      if (currentLine >= line) return true;
    } else {
      const chunkLength =
        (side === Side.LEFT ? chunk.a?.length : chunk.b?.length) ?? 0;
      currentLine += chunkLength;
      if (currentLine >= line) {
        return chunk.common ?? false;
      }
    }
  }
  return false;
}

/**
 * Get the lines of the diff for a given side.
 */
export function getDiffLines(diff: DiffInfo, side: Side): string[] {
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
  return (
    !!diff?.meta_a?.content_type.startsWith('image/') ||
    !!diff?.meta_b?.content_type.startsWith('image/')
  );
}
