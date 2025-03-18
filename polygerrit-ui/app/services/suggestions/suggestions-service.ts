/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../../types/types';
import {SuggestionsProvider} from '../../api/suggestions';
import {
  ChangeInfo,
  CommentRange,
  FixSuggestionInfo,
  RevisionPatchSetNum,
} from '../../api/rest-api';
import {Comment} from '../../types/common';
import {AutocompletionContext} from '../../utils/autocomplete-cache';
import {define} from '../../models/dependency';

export const suggestionsServiceToken = define<SuggestionsService>(
  'suggestions-service'
);
export interface SuggestionsService extends Finalizable {
  isGeneratedSuggestedFixEnabled(
    suggestionsProvider?: SuggestionsProvider,
    change?: ChangeInfo,
    path?: string
  ): boolean;

  isGeneratedSuggestedFixEnabledForComment(
    suggestionsProvider?: SuggestionsProvider,
    change?: ChangeInfo,
    comment?: Comment
  ): boolean;

  generateSuggestedFix(
    suggestionsProvider: SuggestionsProvider,
    data: {
      prompt: string;
      changeInfo: ChangeInfo;
      patchsetNumber: RevisionPatchSetNum;
      filePath: string;
      range?: CommentRange;
      lineNumber?: number;
      generatedSuggestionId?: string;
      commentId?: string;
    }
  ): Promise<FixSuggestionInfo | undefined>;

  generateSuggestedFix(
    suggestionsProvider: SuggestionsProvider,
    data: {
      prompt: string;
      changeInfo: ChangeInfo;
      patchsetNumber: RevisionPatchSetNum;
      filePath: string;
      range?: CommentRange;
      lineNumber?: number;
      generatedSuggestionId?: string;
      commentId?: string;
    }
  ): Promise<FixSuggestionInfo | undefined>;

  generateSuggestedFixForComment(
    suggestionsProvider?: SuggestionsProvider,
    change?: ChangeInfo,
    comment?: Comment,
    commentText?: string,
    generatedSuggestionId?: string
  ): Promise<FixSuggestionInfo | undefined>;

  autocompleteComment(
    suggestionsProvider?: SuggestionsProvider,
    change?: ChangeInfo,
    comment?: Comment,
    commentText?: string,
    comments?: Comment[]
  ): Promise<AutocompletionContext | undefined>;
}
