/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
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
import {ReportSource, SuggestionsService} from './suggestions-service';
import {BehaviorSubject} from 'rxjs';
import {PluginsModel} from '../../models/plugins/plugins-model';
import {ChangeModel} from '../../models/change/change-model';

export class GrSuggestionsService implements SuggestionsService {
  private suggestionsProvider?: SuggestionsProvider;

  private change?: ChangeInfo;

  constructor(
    readonly reporting: ReportingService,
    private readonly pluginsModel: PluginsModel,
    private readonly changeModel: ChangeModel
  ) {
    this.pluginsModel.suggestionsPlugins$.subscribe(suggestionsPlugins => {
      this.suggestionsProvider = suggestionsPlugins?.[0]?.provider;
      this.suggestionsServiceUpdated$.next(true);
    });

    this.changeModel.change$.subscribe(change => {
      this.change = change as ChangeInfo;
      this.suggestionsServiceUpdated$.next(true);
    });
  }

  finalize() {}

  public suggestionsServiceUpdated$ = new BehaviorSubject<boolean>(false);

  public isGeneratedSuggestedFixEnabled(path?: string): boolean {
    return (
      !!this.suggestionsProvider &&
      !!this.change &&
      !!path &&
      path !== SpecialFilePath.PATCHSET_LEVEL_COMMENTS &&
      path !== SpecialFilePath.COMMIT_MESSAGE &&
      path !== SpecialFilePath.MERGE_LIST &&
      this.change.is_private !== true &&
      (!this.suggestionsProvider.supportedFileExtensions ||
        this.suggestionsProvider.supportedFileExtensions.includes(
          getFileExtension(path)
        ))
    );
  }

  public isGeneratedSuggestedFixEnabledForComment(comment?: Comment): boolean {
    return (
      this.isGeneratedSuggestedFixEnabled(comment?.path) &&
      // Disable for comments on the left side of the diff, files can be deleted
      // or such suggestions cannot be applied.
      comment?.side !== CommentSide.PARENT &&
      !!comment &&
      !isFileLevelComment(comment) &&
      !hasUserSuggestion(comment)
    );
  }

  public async generateSuggestedFix(data: {
    prompt: string;
    patchsetNumber: RevisionPatchSetNum;
    filePath: string;
    range?: CommentRange;
    lineNumber?: number;
    generatedSuggestionId?: string;
    commentId?: string;
    reportSource?: ReportSource;
  }): Promise<FixSuggestionInfo | undefined> {
    if (!this.suggestionsProvider?.suggestFix || !this.change) return;
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_REQUEST, {
      uuid: data.generatedSuggestionId,
      type: 'suggest-fix',
      commentId: data.commentId,
      source: data.reportSource,
      fileExtension: getFileExtension(data.filePath ?? ''),
    });
    const suggestionResponse = await this.suggestionsProvider.suggestFix({
      prompt: data.prompt,
      changeInfo: this.change,
      patchsetNumber: data.patchsetNumber,
      filePath: data.filePath,
      range: data.range,
      lineNumber: data.lineNumber,
    });

    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_RESPONSE, {
      uuid: data.generatedSuggestionId,
      type: 'suggest-fix',
      commentId: data.commentId,
      source: data.reportSource,
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
    comment?: Comment,
    commentText?: string,
    generatedSuggestionId?: string,
    reportSource?: ReportSource
  ): Promise<FixSuggestionInfo | undefined> {
    if (
      !comment ||
      !comment.path ||
      !comment.patch_set ||
      !this.suggestionsProvider?.suggestFix ||
      !this.change ||
      !commentText
    ) {
      return;
    }

    return this.generateSuggestedFix({
      prompt: commentText,
      patchsetNumber: comment.patch_set,
      filePath: comment.path,
      range: comment.range,
      lineNumber: comment.line,
      generatedSuggestionId,
      commentId: id(comment),
      reportSource,
    });
  }

  public async autocompleteComment(
    comment?: Comment,
    commentText?: string,
    comments?: Comment[]
  ): Promise<AutocompletionContext | undefined> {
    if (
      !comment ||
      !comment.path ||
      !comment.patch_set ||
      !commentText ||
      commentText.length === 0 ||
      !this.change ||
      !this.suggestionsProvider?.autocompleteComment
    ) {
      return;
    }
    this.reporting.time(Timing.COMMENT_COMPLETION);
    const response = await this.suggestionsProvider.autocompleteComment({
      id: id(comment),
      commentText,
      changeInfo: this.change,
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
      responseCode: response.responseCode,
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
