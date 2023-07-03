/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {CommentRange} from './rest-api';

export declare interface SuggestionsPluginApi {
  /**
   * Must only be called once. You cannot register twice. You cannot unregister.
   */
  register(provider: SuggestionsProvider): void;
}

export declare interface CommentDataForSuggestion {
  changeNumber: number;
  patchsetNumber: number;
  prompt: string;
  range: CommentRange;
  commentedCode: string;
}

export declare interface SuggestionsProvider {
  /**
   * Gerrit calls this method when ...
   * - ... user types a comment draft
   */
  comment2SuggestedEdit(
    commentData: CommentDataForSuggestion
  ): Promise<Comment2SuggestedEditResponse>;
}

declare interface Comment2SuggestedEditResponse {
  responseCode: ResponseCode;

  suggestions?: Suggestion[];
}

export declare interface Suggestion {
  replacement: string;
}

export enum ResponseCode {
  OK = 'OK',
  ERROR = 'ERROR',
  LANGUAGE_NOT_SUPPORTED = 'LANGUAGE_NOT_SUPPORTED',
}
