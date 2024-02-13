/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChangeInfo, CommentRange, RevisionPatchSetNum} from './rest-api';
import {FixSuggestionInfo} from './common';

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

export declare interface SuggestionsProvider {
  /**
   * Gerrit calls these methods when ...
   * - ... user types a comment draft
   */
  suggestCode?(commentData: SuggestCodeRequest): Promise<SuggestCodeResponse>;
  suggestFix?(commentData: SuggestCodeRequest): Promise<SuggestedFixResponse>;
  /**
   * List of supported file extensions. If undefined, all file extensions supported.
   */
  supportedFileExtensions?: string[];
}

export declare interface SuggestCodeResponse {
  responseCode: ResponseCode;
  suggestions: Suggestion[];
}

export declare interface SuggestedFixResponse {
  responseCode: ResponseCode;
  fix_suggestions: FixSuggestionInfo;
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
