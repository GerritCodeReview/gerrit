/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {CommentRange} from '../api/rest-api';
import {SuggestCodeRequest, Suggestion} from '../api/suggestions';

export function isDefined<T>(x: T): x is NonNullable<T> {
  return x !== undefined && x !== null;
}

// https://source.corp.google.com/piper///depot/google3/java/com/google/devtools/gerritcodereview/plugins/comments2code/RangeUtil.java
export function textRangeToCommentRange(
  range: C2CCommentRange
): C2CCommentRange {
  return {
    start_line: range.start_line,
    start_char: range.start_char - 1,
    end_line: range.end_line - 1,
    end_char: range.end_char,
  };
}

/** Suggestion type received from gerrit c2c backend plugin */
export declare interface C2CSuggestion {
  path: string;
  comment_uuid: string;
  log_probability: number;
  new_content: string;
  replacements: C2CReplacement[];
}
/** Replacement type received from gerrit c2c backend plugin */
export declare interface C2CReplacement {
  new_content: string;
  patch_set_number: number;
  path: string;
  range: C2CCommentRange;
  operation: string;
}

export declare interface C2CCommentRange {
  /** The start line number of the range. (1-based) */
  start_line: number;

  /** The character position in the start line. (0-based) */
  start_char: number;

  /** The end line number of the range. (1-based) */
  end_line: number;

  /** The character position in the end line. (0-based) */
  end_char: number;
}

export function isReplacementInRange(
  replacement: C2CReplacement,
  requestRange: CommentRange
) {
  if (!replacement.range) return false;
  const replacementRange = textRangeToCommentRange(replacement.range);
  return (
    // requestRange.end_line + 1, replacement can be just addition after
    // request range.
    replacementRange.start_line <= requestRange.end_line + 1 &&
    replacementRange.start_line >= requestRange.start_line &&
    replacementRange.end_line <= requestRange.end_line
  );
}

export function getContentInCommentRange(
  fileContent: string,
  fromLine: number,
  numLines: number
) {
  const lines = fileContent.split('\n');
  return lines.slice(fromLine - 1, fromLine - 1 + numLines).join('\n');
}

export function transformC2CSuggestionsToProviderSuggestions(
  suggestions: C2CSuggestion[],
  suggestCodeRequest: SuggestCodeRequest
): Suggestion[] {
  // In current prototype we are only interested in one suggestion
  const suggestion = suggestions?.[0];
  if (!suggestion) return [];

  // TODO(milutin): accept replacement out of range, once we are able to change
  // comment range
  const requestRange = getNormalizedRange(suggestCodeRequest);
  let numAddedLinesBeforeRequestRange = 0;
  for (const replacement of suggestion.replacements) {
    const numLinesAddedByReplacement =
      getNumLinesAddedByReplacement(replacement);
    if (
      numLinesAddedByReplacement > 0 &&
      replacement.range.end_line <= requestRange.start_line
    ) {
      numAddedLinesBeforeRequestRange += numLinesAddedByReplacement;
    }

    if (isReplacementInRange(replacement, requestRange)) {
      const isOnlyAddingLines =
        replacement.range.start_line === requestRange.end_line + 1;
      let numLines: number;
      if (isOnlyAddingLines) {
        numLines =
          getReplacementLineLength(replacement) + rangeLineLength(requestRange);
      } else {
        numLines = Math.max(
          getReplacementLineLength(replacement),
          rangeLineLength(requestRange)
        );
      }

      const content = getContentInCommentRange(
        suggestion.new_content,
        requestRange.start_line + numAddedLinesBeforeRequestRange,
        numLines
      );
      return [{replacement: content}];
    }
  }
  return [];
}

function getNormalizedRange(suggestCodeRequest: SuggestCodeRequest) {
  return (
    suggestCodeRequest.range ??
    ({
      start_line: suggestCodeRequest.lineNumber,
      end_line: suggestCodeRequest.lineNumber,
      start_character: 0,
      end_character: 0,
    } as CommentRange)
  );
}

function getNumLinesAddedByReplacement(replacement: C2CReplacement) {
  const replacementRange = textRangeToCommentRange(replacement.range);
  return (
    getReplacementLineLength(replacement) -
    Math.max(0, replacementRange.end_line - replacementRange.start_line + 1)
  );
}

function getReplacementLineLength(replacement: C2CReplacement) {
  return replacement.new_content.split('\n').length - 1;
}

function rangeLineLength(range: CommentRange) {
  return range.end_line - range.start_line + 1;
}
