/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  ChangeInfo,
  CommentRange,
  FixSuggestionInfo,
  RevisionPatchSetNum,
} from './rest-api';

export declare interface SuggestionsPluginApi {
  /**
   * Must only be called once. You cannot register twice. You cannot unregister.
   */
  register(provider: SuggestionsProvider): void;
}

export declare interface SuggestCodeRequest {
  prompt: string;
  changeInfo: ChangeInfo;
  patchsetNumber: RevisionPatchSetNum;
  filePath: string;
  range?: CommentRange;
  lineNumber?: number;
}

export declare interface AutocompleteCommentRequest {
  id: string;
  commentText: string;
  changeInfo: ChangeInfo;
  patchsetNumber: RevisionPatchSetNum;
  filePath: string;
  range?: CommentRange;
  lineNumber?: number;
}

export declare interface SuggestionsProvider {
  autocompleteComment?(
    req: AutocompleteCommentRequest
  ): Promise<AutocompleteCommentResponse>;

  /**
   * Gerrit calls these methods when ...
   * - ... user types a comment draft
   */
  suggestCode?(commentData: SuggestCodeRequest): Promise<SuggestCodeResponse>;
  suggestFix?(commentData: SuggestCodeRequest): Promise<SuggestedFixResponse>;
  /**
   * Gets the title to display on the fix suggestion preview.
   *
   * @param fix_suggestions A list of suggested fixes.
   * @return The title string or empty to use the default title.
   */
  getFixSuggestionTitle?(fix_suggestions?: FixSuggestionInfo[]): string;
  /**
   * Gets a link to documentation for icon help next to title
   *
   * @param fix_suggestions A list of suggested fixes.
   * @return The documentation URL string or empty to use the default link to
   * gerrit documentation about fix suggestions.
   */
  getDocumentationLink?(fix_suggestions?: FixSuggestionInfo[]): string;
  /**
   * List of supported file extensions. If undefined, all file extensions supported.
   */
  supportedFileExtensions?: string[];
  /**
   * Sends feedback on autocompletion suggestions.
   * This method allows the plugin to report which suggestions were accepted or
   * rejected by the user, which can be used for improving future suggestions.
   */
  sendAutocompleteFeedback?(feedbackEntries: SuggestionFeedbackEntry[]): void;
  /**
   * Sends feedback on 'suggest fix' (code repair) suggestions.
   * This method allows the plugin to report which suggestions were accepted or
   * rejected by the user, which can be used for improving future suggestions.
   *
   */
  sendSuggestFixFeedback?(feedbackEntries: SuggestionFeedbackEntry[]): void;
}

/**
 * Represents a 128-bit unique identifier for a suggestion, used for tracking feedback.
 * The ID is split into two 64-bit BigInt components.
 */
export declare interface Feedback {
  /** The least significant 64 bits of the 128-bit identifier. */
  id1: bigint;
  /** The most significant 64 bits of the 128-bit identifier. */
  id2: bigint;
}

export declare interface SuggestionFeedbackEntry {
  /** The unique identifier for the suggestion. */
  feedbackId: Feedback;
  /** True if the suggestion was accepted by the user, false otherwise. */
  accepted: boolean;
}

export declare interface AutocompleteCommentResponse {
  responseCode: ResponseCode;
  completion?: string;
  modelVersion?: string;
  outcome?: number;
  feedback?: Feedback;
}

export declare interface SuggestCodeResponse {
  responseCode: ResponseCode;
  suggestions: Suggestion[];
}

export declare interface SuggestedFixResponse {
  responseCode: ResponseCode;
  fix_suggestions: FixSuggestionInfo[];
}

export declare interface Suggestion {
  replacement: string;
  newRange?: CommentRange;
}

export enum ResponseCode {
  OK = 'OK',
  NO_SUGGESTION = 'NO_SUGGESTION',
  OUT_OF_RANGE = 'OUT_OF_RANGE',
  ERROR = 'ERROR',
}
