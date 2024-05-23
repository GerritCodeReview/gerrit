/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  ChangeInfo,
  CommentRange,
  RevisionPatchSetNum,
  FixSuggestionInfo,
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
}

export declare interface AutocompleteCommentResponse {
  responseCode: ResponseCode;
  completion?: string;
  modelVersion?: string;
  outcome?: number;
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
