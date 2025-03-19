/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../../types/types';
import {
  ChangeInfo,
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
export interface SuggestionsService extends Finalizable {
  suggestionsServiceUpdated$: Observable<boolean>;

  isGeneratedSuggestedFixEnabled(path?: string): boolean;

  isGeneratedSuggestedFixEnabledForComment(comment?: Comment): boolean;

  generateSuggestedFix(data: {
    prompt: string;
    changeInfo: ChangeInfo;
    patchsetNumber: RevisionPatchSetNum;
    filePath: string;
    range?: CommentRange;
    lineNumber?: number;
    generatedSuggestionId?: string;
    commentId?: string;
  }): Promise<FixSuggestionInfo | undefined>;

  generateSuggestedFixForComment(
    comment?: Comment,
    commentText?: string,
    generatedSuggestionId?: string
  ): Promise<FixSuggestionInfo | undefined>;

  autocompleteComment(
    comment?: Comment,
    commentText?: string,
    comments?: Comment[]
  ): Promise<AutocompletionContext | undefined>;
}
