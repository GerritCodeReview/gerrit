/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {CommentRange} from '../api/rest-api';
import {ResponseCode, SuggestCodeRequest, Suggestion} from '../api/suggestions';

export function isDefined<T>(x: T): x is NonNullable<T> {
  return x !== undefined && x !== null;
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
/**
 * Check if replacement is inside of suggested Code request range
 */
export function isReplacementInRange(
  replacement: C2CReplacement,
  requestRange: CommentRange
) {
  if (!replacement.range) return false;
  const replacementRange = textRangeToCommentRange(replacement.range);
  // TODO(milutin): Track number of suggestions excluded because of conditions
  return (
    // requestRange.end_line + 1, replacement can be just addition after
    // request range.
    replacementRange.start_line <= requestRange.end_line + 1 &&
    replacementRange.start_line >= requestRange.start_line &&
    replacementRange.end_line <= requestRange.end_line
  );
}

/**
 * convert {@link TextRangeProto.TextRange} into {@link Comment.Range}
 * Similar to comments2code/RangeUtil.java
 */
export function textRangeToCommentRange(range: C2CCommentRange): CommentRange {
  return {
    start_line: range.start_line,
    start_character: range.start_char - 1,
    end_line: range.end_line - 1,
    end_character: range.end_char,
  };
}

/**
 * extract full lines in range or on a line from full file content
 * similar to google3/third_party/javascript/polygerrit/utils/comment-util.ts
 **/
function getContentInCommentRange(
  fileContent: string,
  fromLine: number,
  numLines: number
) {
  const lines = fileContent.split('\n');
  return lines.slice(fromLine - 1, fromLine - 1 + numLines).join('\n');
}

/**
 * Transform C2C Suggestion to User Suggested Edit format
 * It expects full lines replacement defined by range or line
 **/
export function transformC2CSuggestionsToProviderSuggestions(
  suggestion: C2CSuggestion,
  suggestCodeRequest: SuggestCodeRequest,
  newRange?: CommentRange
): Suggestion[] {
  const requestRange = newRange ?? getNormalizedRange(suggestCodeRequest);
  let numAddedLinesBeforeRequestRange = 0;
  for (const replacement of suggestion.replacements) {
    const numLinesAddedByReplacement =
      getNumLinesAddedByReplacement(replacement);
    // if new lines are added and replacement is before requestedRange,
    // add number of new lines to total num of added lines before requested
    // range
    if (
      numLinesAddedByReplacement > 0 &&
      replacement.range.end_line <= requestRange.start_line
    ) {
      numAddedLinesBeforeRequestRange += numLinesAddedByReplacement;
    }

    if (!isReplacementInRange(replacement, requestRange)) continue;
    // TODO(milutin): accept replacement out of range, once we are able to
    // change comment range
    let addedLinesBeforeRange = 0;
    if (isAdditionBeforeRequestedRange(replacement, requestRange)) {
      addedLinesBeforeRange = getNumOfLinesInReplacement(replacement);
    }
    const content = getContentInCommentRange(
      suggestion.new_content,
      requestRange.start_line +
        numAddedLinesBeforeRequestRange -
        addedLinesBeforeRange,
      getNumLinesOfSuggestion(replacement, requestRange)
    );
    if (newRange) {
      return [{replacement: content, newRange}];
    } else {
      return [{replacement: content}];
    }
  }
  return [];
}

/**
 * Get normalized range from suggestCodeRequest
 **/
export function getNormalizedRange(
  suggestCodeRequest: SuggestCodeRequest
): CommentRange {
  return (
    suggestCodeRequest.range ?? {
      start_line: suggestCodeRequest.lineNumber!,
      end_line: suggestCodeRequest.lineNumber!,
      start_character: 0,
      end_character: 0,
    }
  );
}

function getNumLinesAddedByReplacement(replacement: C2CReplacement) {
  const replacementRange = textRangeToCommentRange(replacement.range);
  return (
    getNumOfLinesInReplacement(replacement) -
    Math.max(0, numOfLinesInRange(replacementRange))
  );
}

function getNumOfLinesInReplacement(replacement: C2CReplacement) {
  // Comments2code replacement always ends with \n that we skip by -1
  return replacement.new_content.split('\n').length - 1;
}

function numOfLinesInRange(range: CommentRange) {
  return range.end_line - range.start_line + 1;
}

function getNumLinesOfSuggestion(
  replacement: C2CReplacement,
  requestRange: CommentRange
) {
  const isOnlyAddingLines =
    replacement.range.start_line === requestRange.end_line + 1;
  if (isOnlyAddingLines) {
    return (
      getNumOfLinesInReplacement(replacement) + numOfLinesInRange(requestRange)
    );
  } else {
    return Math.max(
      getNumOfLinesInReplacement(replacement),
      numOfLinesInRange(requestRange)
    );
  }
}

export function getNewRange(
  c2cSuggestion: C2CSuggestion
): CommentRange | undefined {
  let newRange: CommentRange | undefined = undefined;
  let largestReplacement = 0; // Replacement needs to have 3 lines or more
  for (const replacement of c2cSuggestion.replacements) {
    const numOfLinesInReplacement = getNumOfLinesInReplacement(replacement);
    if (largestReplacement < numOfLinesInReplacement) {
      largestReplacement = numOfLinesInReplacement;
      newRange = textRangeToCommentRange(replacement.range);
    }
  }
  return newRange;
}

function isAdditionBeforeRequestedRange(
  replacement: C2CReplacement,
  requestRange: CommentRange
) {
  return replacement.range.end_line === requestRange.start_line;
}

export function suggestCode(
  commentData: SuggestCodeRequest,
  suggestion: C2CSuggestion
) {
  if (!commentData.prompt) {
    return {
      suggestions: [],
      responseCode: ResponseCode.ERROR,
    };
  }
  // In current prototype we are only interested in one suggestion
  const c2cSuggestion = suggestion;
  if (!c2cSuggestion) {
    return {suggestions: [], responseCode: ResponseCode.NO_SUGGESTION};
  }

  const isSomeReplacementsInRange = c2cSuggestion.replacements.some(r =>
    isReplacementInRange(r, getNormalizedRange(commentData))
  );
  let newRange = undefined;
  if (!isSomeReplacementsInRange) {
    newRange = getNewRange(c2cSuggestion);
    if (!newRange) {
      return {
        suggestions: [],
        responseCode: ResponseCode.OUT_OF_RANGE,
      };
    }
  }

  const suggestions = transformC2CSuggestionsToProviderSuggestions(
    c2cSuggestion,
    commentData,
    newRange
  );
  return {
    suggestions,
    responseCode: ResponseCode.OK,
  };
}
