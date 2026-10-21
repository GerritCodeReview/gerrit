/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CommentRange} from '../api/rest-api';
import {Side} from '../constants/constants';
import {DiffInfo, SkipInfo, SkipObject} from '../types/diff';

export function normalizeSkipInfo(skip: SkipInfo | undefined): SkipObject {
  if (!skip) return {left: 0, right: 0};
  return typeof skip === 'number' ? {left: skip, right: skip} : skip;
}

export function otherSide(side: Side) {
  return side === Side.LEFT ? Side.RIGHT : Side.LEFT;
}

export function countLines(diff?: DiffInfo, side?: Side) {
  if (!diff?.content || !side) return 0;
  return diff.content.reduce((sum, chunk) => {
    const sideChunk = side === Side.LEFT ? chunk.a : chunk.b;
    return (
      sum +
      (sideChunk?.length ??
        chunk.ab?.length ??
        normalizeSkipInfo(chunk.skip)[side])
    );
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
      currentLine += normalizeSkipInfo(chunk.skip)[side];
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
 * Maps a 1-based line number from one side of a diff to the other side, if the
 * line is in an unchanged (or common) region. Returns undefined if the line was
 * modified or deleted.
 */
export function mapLineNumber(
  diff?: DiffInfo,
  line?: number,
  fromSide: Side = Side.LEFT
): number | undefined {
  if (!diff?.content || !line || line <= 0) return undefined;
  const toSide = otherSide(fromSide);
  let currentFrom = 0;
  let currentTo = 0;
  for (const chunk of diff.content) {
    if (chunk.skip) {
      const skip = normalizeSkipInfo(chunk.skip);
      const fromLen = skip[fromSide];
      const toLen = skip[toSide];
      if (currentFrom + fromLen >= line) {
        if (fromLen !== toLen) return undefined;
        return currentTo + (line - currentFrom);
      }
      currentFrom += fromLen;
      currentTo += toLen;
    } else if (chunk.ab) {
      const len = chunk.ab.length;
      if (currentFrom + len >= line) {
        return currentTo + (line - currentFrom);
      }
      currentFrom += len;
      currentTo += len;
    } else {
      const fromLen =
        (fromSide === Side.LEFT ? chunk.a?.length : chunk.b?.length) ?? 0;
      const toLen =
        (toSide === Side.LEFT ? chunk.a?.length : chunk.b?.length) ?? 0;
      if (currentFrom + fromLen >= line) {
        if (chunk.common && fromLen === toLen) {
          return currentTo + (line - currentFrom);
        }
        return undefined;
      }
      currentFrom += fromLen;
      currentTo += toLen;
    }
  }
  return undefined;
}

export function mapCommentRange(
  diff?: DiffInfo,
  range?: CommentRange,
  fromSide: Side = Side.LEFT
): CommentRange | undefined {
  if (!diff || !range) return undefined;
  const startLine = mapLineNumber(diff, range.start_line, fromSide);
  const endLine = mapLineNumber(diff, range.end_line, fromSide);
  if (startLine === undefined || endLine === undefined) return undefined;
  if (endLine - startLine !== range.end_line - range.start_line) {
    return undefined;
  }
  return {
    ...range,
    start_line: startLine,
    end_line: endLine,
  };
}

/**
 * Get the lines of the diff for a given side.
 */
export function getDiffLines(diff: DiffInfo, side: Side): string[] {
  let lines: string[] = [];
  for (const chunk of diff.content) {
    if (chunk.skip) {
      const skip = normalizeSkipInfo(chunk.skip);
      lines = lines.concat(Array(skip[side]).fill(''));
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

const MARKDOWN_FILE_EXTENSIONS = /\.(md|markdown|mdown|mkdn|mkd)$/i;

export function isMarkdownDiff(path?: string, diff?: DiffInfo): boolean {
  if (path && MARKDOWN_FILE_EXTENSIONS.test(path)) {
    return true;
  }
  const contentTypeA = diff?.meta_a?.content_type;
  const contentTypeB = diff?.meta_b?.content_type;
  return (
    !!contentTypeA?.startsWith('text/x-markdown') ||
    !!contentTypeB?.startsWith('text/x-markdown') ||
    !!contentTypeA?.startsWith('text/markdown') ||
    !!contentTypeB?.startsWith('text/markdown')
  );
}
