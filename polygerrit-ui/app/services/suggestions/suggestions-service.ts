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
import {
  ChangeInfo,
  CommentRange,
  CommentSide,
  FixSuggestionInfo,
  RevisionPatchSetNum,
} from '../../api/rest-api';
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
    path?: string
  ): boolean {
    return (
      !!suggestionsProvider &&
      !!change &&
      !!path &&
      path !== SpecialFilePath.PATCHSET_LEVEL_COMMENTS &&
      path !== SpecialFilePath.COMMIT_MESSAGE &&
      change.is_private !== true &&
      (!suggestionsProvider.supportedFileExtensions ||
        suggestionsProvider.supportedFileExtensions.includes(
          getFileExtension(path)
        ))
    );
  }

  public isGeneratedSuggestedFixEnabledForComment(
    suggestionsProvider?: SuggestionsProvider,
    change?: ChangeInfo,
    comment?: Comment
  ): boolean {
    return (
      this.isGeneratedSuggestedFixEnabled(
        suggestionsProvider,
        change,
        comment?.path
      ) &&
      // Disable for comments on the left side of the diff, files can be deleted
      // or such suggestions cannot be applied.
      comment?.side !== CommentSide.PARENT &&
      !!comment &&
      !isFileLevelComment(comment) &&
      !hasUserSuggestion(comment)
    );
  }

  public async generateSuggestedFix(
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
  ): Promise<FixSuggestionInfo | undefined> {
    if (!suggestionsProvider.suggestFix) return;
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_REQUEST, {
      uuid: data.generatedSuggestionId,
      type: 'suggest-fix',
      commentId: data.commentId,
      fileExtension: getFileExtension(data.filePath ?? ''),
    });
    const suggestionResponse = await suggestionsProvider.suggestFix({
      prompt: data.prompt,
      changeInfo: data.changeInfo,
      patchsetNumber: data.patchsetNumber,
      filePath: data.filePath,
      range: data.range,
      lineNumber: data.lineNumber,
    });

    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_RESPONSE, {
      uuid: data.generatedSuggestionId,
      type: 'suggest-fix',
      commentId: data.commentId,
      response: suggestionResponse.responseCode,
      numSuggestions: suggestionResponse.fix_suggestions.length,
      fileExtension: getFileExtension(data.filePath ?? ''),
      logProbability: suggestionResponse.fix_suggestions?.[0]?.log_probability,
    });

    const suggestion = suggestionResponse.fix_suggestions?.[0];
    if (!suggestion?.replacements || suggestion.replacements.length === 0) {
      return;
    }
    return suggestion;
  }

  public async generateSuggestedFixForComment(
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

    return this.generateSuggestedFix(suggestionsProvider, {
      prompt: commentText,
      changeInfo: change,
      patchsetNumber: comment.patch_set,
      filePath: comment.path,
      range: comment.range,
      lineNumber: comment.line,
      generatedSuggestionId,
      commentId: comment.id,
    });
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
