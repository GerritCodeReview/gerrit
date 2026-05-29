/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../../types/types';
import {
  CommentRange,
  FixSuggestionInfo,
  RevisionPatchSetNum,
} from '../../api/rest-api';
import {Comment} from '../../types/common';
import {AutocompletionContext} from '../../utils/autocomplete-cache';
import {define} from '../../models/dependency';
import {Observable} from 'rxjs';

export const suggestionsServiceToken = define<SuggestionsService>(
  'suggestions-service'
);

export enum ReportSource {
  GET_AI_FIX_FOR_CHECK = 'GET_AI_FIX_FOR_CHECK',
  GET_AI_FIX_FOR_COMMENT = 'GET_AI_FIX_FOR_COMMENT',
  FIX_FOR_REVIEWER_COMMENT = 'FIX_FOR_REVIEWER_COMMENT',
}

export interface SuggestionsService extends Finalizable {
  /**
   * Emits a boolean value whenever the enablement state of any suggestion
   * feature changes. Consumers should subscribe to this observable and call
   * `this.requestUpdate()` (or equivalent UI update mechanism) when it emits
   * to ensure the UI reflects the correct enablement state of generate methods.
   */
  suggestionsServiceUpdated$: Observable<boolean>;

  /**
   * Checks if the feature to generate suggested fixes is enabled.
   * The enablement can change, so components should subscribe to
   * `suggestionsServiceUpdated$` and call `this.requestUpdate()` to
   * re-evaluate.
   */
  isGeneratedSuggestedFixEnabled(path?: string): boolean;

  /**
   * Checks if the feature to generate suggested fixes for a specific comment
   * is enabled. The enablement can change, so components should subscribe to
   * `suggestionsServiceUpdated$` and call `this.requestUpdate()` to
   * re-evaluate.
   */
  isGeneratedSuggestedFixEnabledForComment(comment?: Comment): boolean;

  /**
   * Generates a suggested fix.
   *
   * **Important:** This method should only be called if
   * `isGeneratedSuggestedFixEnabled(data.filePath)` returns `true`.
   * The enablement can change, so components should subscribe to
   * `suggestionsServiceUpdated$` and call `this.requestUpdate()` when it
   * emits to ensure they only call this method when enabled.
   */
  generateSuggestedFix(data: {
    prompt: string;
    patchsetNumber: RevisionPatchSetNum;
    filePath: string;
    range?: CommentRange;
    lineNumber?: number;
    generatedSuggestionId?: string;
    commentId?: string;
    reportSource?: ReportSource;
  }): Promise<FixSuggestionInfo | undefined>;

  /**
   * Generates a suggested fix specifically for a comment.
   *
   * **Important:** This method should only be called if
   * `isGeneratedSuggestedFixEnabledForComment(comment)` returns `true`.
   * The enablement can change, so components should subscribe to
   * `suggestionsServiceUpdated$` and call `this.requestUpdate()` when it
   * emits to ensure they only call this method when enabled.
   */
  generateSuggestedFixForComment(
    comment?: Comment,
    commentText?: string,
    generatedSuggestionId?: string,
    reportSource?: ReportSource
  ): Promise<FixSuggestionInfo | undefined>;

  /**
   * Provides autocompletion suggestions for comments.
   */
  autocompleteComment(
    comment?: Comment,
    commentText?: string,
    comments?: Comment[]
  ): Promise<AutocompletionContext | undefined>;
}
