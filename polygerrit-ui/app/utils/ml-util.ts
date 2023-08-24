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
    // TODO(milutin): change from edwin, double check, maybe because extra \n
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

export function getContentInCommentRange(
  fileContent: string,
  range?: CommentRange,
  line?: number
) {
  const lines = fileContent.split('\n');
  if (range) {
    return lines.slice(range.start_line - 1, range.end_line).join('\n');
  }
  return lines[line! - 1];
}

export function transformC2CSuggestionsToProviderSuggestions(
  suggestions?: C2CSuggestion[],
  suggestCodeRequest?: SuggestCodeRequest
): Suggestion[] {
  if (!suggestions || !suggestCodeRequest) return [];

  // Currently we are only interested in one suggestion
  const suggestion = suggestions[0];
  if (!suggestion) return [];

  const isSomeReplacementsInRange = suggestion.replacements.some(r =>
    isReplacementInRange(r, suggestCodeRequest)
  );
  if (!isSomeReplacementsInRange) return [];

  const content = getContentInCommentRange(
    suggestion.new_content,
    suggestCodeRequest.range,
    suggestCodeRequest.lineNumber
  );

  return [{replacement: content}];
}

export function isReplacementInRange(
  replacement: C2CReplacement,
  suggestCodeRequest: SuggestCodeRequest
) {
  if (replacement.range && suggestCodeRequest.range) {
    const replacementRange = textRangeToCommentRange(replacement.range);
    return (
      replacementRange.start_line <= suggestCodeRequest.range.end_line &&
      replacementRange.start_line >= suggestCodeRequest.range.start_line &&
      replacementRange.end_line <= suggestCodeRequest.range.end_line
    );
  } else {
    return false;
  }
}
