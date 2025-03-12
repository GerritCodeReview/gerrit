/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../../types/types';
import {define} from '../../models/dependency';
import {ReportingService} from '../gr-reporting/gr-reporting';
import {
  AutocompleteCommentResponse,
  SuggestionsProvider,
} from '../../api/suggestions';
import {Interaction, Timing} from '../../constants/reporting';
import {ChangeInfo, CommentSide, FixSuggestionInfo} from '../../api/rest-api';
import {getFileExtension} from '../../utils/file-util';
import {Comment} from '../../types/common';
import {SpecialFilePath} from '../../constants/constants';
import {hasUserSuggestion, isFileLevelComment} from '../../utils/comment-util';
import {id} from '../../utils/comment-util';
import {AutocompletionContext} from '../../utils/autocomplete-cache';

export const suggestionsServiceToken = define<SuggestionsService>(
  'suggestions-service'
);

export class SuggestionsService implements Finalizable {
  constructor(readonly reporting: ReportingService) {}

  finalize() {}

  public isGeneratedSuggestedFixEnabled(
    suggestionsProvider?: SuggestionsProvider,
    change?: ChangeInfo,
    comment?: Comment
  ): boolean {
    return (
      !!suggestionsProvider &&
      !!comment?.path &&
      comment.path !== SpecialFilePath.PATCHSET_LEVEL_COMMENTS &&
      comment.path !== SpecialFilePath.COMMIT_MESSAGE &&
      // Disable for comments on the left side of the diff, files can be deleted
      // or such suggestions cannot be applied.
      comment?.side !== CommentSide.PARENT &&
      !isFileLevelComment(comment) &&
      !hasUserSuggestion(comment) &&
      (!suggestionsProvider.supportedFileExtensions ||
        suggestionsProvider.supportedFileExtensions.includes(
          getFileExtension(comment.path)
        )) &&
      !!change &&
      change.is_private !== true
    );
  }

  public async generateSuggestedFix(
    suggestionsProvider?: SuggestionsProvider,
    change?: ChangeInfo,
    comment?: Comment,
    commentText?: string,
    generatedSuggestionId?: string
  ): Promise<FixSuggestionInfo | undefined> {
    if (
      !comment ||
      !comment.path ||
      !comment.patch_set ||
      !suggestionsProvider?.suggestFix ||
      !change ||
      !commentText
    ) {
      return;
    }
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_REQUEST, {
      uuid: generatedSuggestionId,
      type: 'suggest-fix',
      commentId: comment.id,
      fileExtension: getFileExtension(comment.path ?? ''),
    });
    const suggestionResponse = await suggestionsProvider.suggestFix({
      prompt: commentText,
      changeInfo: change,
      patchsetNumber: comment.patch_set,
      filePath: comment.path,
      range: comment.range,
      lineNumber: comment.line,
    });
    // TODO(milutin): The suggestionResponse can contain multiple suggestion
    // options. We pick the first one for now. In future we shouldn't ignore
    // other suggestions.
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_RESPONSE, {
      uuid: generatedSuggestionId,
      type: 'suggest-fix',
      commentId: comment.id,
      response: suggestionResponse.responseCode,
      numSuggestions: suggestionResponse.fix_suggestions.length,
      fileExtension: getFileExtension(comment.path ?? ''),
      logProbability: suggestionResponse.fix_suggestions?.[0]?.log_probability,
    });

    const suggestion = suggestionResponse.fix_suggestions?.[0];
    if (!suggestion?.replacements || suggestion.replacements.length === 0) {
      return;
    }
    return suggestion;
  }

  public async autocompleteComment(
    suggestionsProvider?: SuggestionsProvider,
    change?: ChangeInfo,
    comment?: Comment,
    commentText?: string,
    comments?: Comment[]
  ) {
    if (
      !comment ||
      !comment.path ||
      !comment.patch_set ||
      !commentText ||
      commentText.length === 0 ||
      !change ||
      !suggestionsProvider?.autocompleteComment
    ) {
      return;
    }
    this.reporting.time(Timing.COMMENT_COMPLETION);
    const response = await suggestionsProvider.autocompleteComment({
      id: id(comment),
      commentText,
      changeInfo: change,
      patchsetNumber: comment?.patch_set,
      filePath: comment.path,
      range: comment.range,
      lineNumber: comment.line,
    });
    const elapsed = this.reporting.timeEnd(Timing.COMMENT_COMPLETION);
    const context = this.createAutocompletionContext(
      commentText,
      response,
      elapsed,
      comment,
      comments
    );
    if (!response?.completion) return;
    return context;
  }

  private createAutocompletionContext(
    draftContent: string,
    response: AutocompleteCommentResponse,
    requestDurationMs: number,
    comment: Comment,
    comments?: Comment[]
  ): AutocompletionContext {
    const commentCompletion = response.completion ?? '';
    return {
      ...this.createAutocompletionBaseContext(comment, comments),

      draftContent,
      draftContentLength: draftContent.length,
      commentCompletion,
      commentCompletionLength: commentCompletion.length,

      isFullCommentPrediction: draftContent.length === 0,
      draftInSyncWithSuggestionLength: 0,
      modelVersion: response.modelVersion ?? '',
      outcome: response.outcome,
      requestDurationMs,
    };
  }

  private createAutocompletionBaseContext(
    comment: Comment,
    comments?: Comment[]
  ): Partial<AutocompletionContext> {
    return {
      commentId: id(comment),
      commentNumber: comments?.length ?? 0,
      filePath: comment.path,
      fileExtension: getFileExtension(comment.path ?? ''),
    };
  }
}
