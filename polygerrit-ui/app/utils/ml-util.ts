/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {SuggestCodeRequest, Suggestion} from '../api/suggestions';

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
  return suggestions.map(s => {
    return {replacement: s.replacements[0].new_content};
  });
}
