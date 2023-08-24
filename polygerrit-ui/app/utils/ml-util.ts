/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

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

export function transformC2CSuggestionsToProviderSuggestions(
  suggestions?: C2CSuggestion[],
  suggestCodeRequest?: SuggestCodeRequest
): Suggestion[] {
  if (!suggestions || !suggestCodeRequest) return [];

  return suggestions
    .map(s => {
      const mlSuggestion = s.replacements.filter(r =>
        isReplacementInRange(r, suggestCodeRequest)
      );
      if (mlSuggestion.length === 0) return;
      return {replacement: mlSuggestion[0].new_content};
    })
    .filter(isDefined);
}

export function isReplacementInRange(
  replacement: C2CReplacement,
  suggestCodeRequest: SuggestCodeRequest
) {
  if (replacement.range && suggestCodeRequest.range) {
    const replacementRange = textRangeToCommentRange(replacement.range);
    return (
      replacementRange.start_line >= suggestCodeRequest.range.start_line &&
      replacementRange.end_line <= suggestCodeRequest.range.end_line
    );
  } else {
    return false;
  }
}
