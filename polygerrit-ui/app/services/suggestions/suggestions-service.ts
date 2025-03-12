/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../../types/types';
import {define} from '../../models/dependency';
import {ReportingService} from '../gr-reporting/gr-reporting';
import {SuggestionsProvider} from '../../api/suggestions';
import {PluginsModel} from '../../models/plugins/plugins-model';
import {Interaction} from '../../constants/reporting';
import {ChangeInfo, FixSuggestionInfo} from '../../api/rest-api';
import {getFileExtension} from '../../utils/file-util';
import {Comment} from '../../types/common';

export const suggestionsServiceToken = define<SuggestionsService>(
  'suggestions-service'
);

export class SuggestionsService implements Finalizable {
  suggestionsProvider?: SuggestionsProvider;

  constructor(
    readonly reporting: ReportingService,
    private readonly pluginsModel: PluginsModel
  ) {
    this.pluginsModel.suggestionsPlugins$.subscribe(
      suggestionsPlugins =>
        (this.suggestionsProvider = suggestionsPlugins?.[0]?.provider)
    );
  }

  finalize() {}

  public enableGeneratedSuggestedFix(comment?: Comment): boolean {
    return !!(
      this.suggestionsProvider &&
      comment?.path &&
      (!this.suggestionsProvider.supportedFileExtensions ||
        this.suggestionsProvider.supportedFileExtensions.includes(
          getFileExtension(comment.path)
        ))
    );
  }

  public async generateSuggestedFix(
    comment: Comment | undefined,
    changeInfo: ChangeInfo,
    messageText: string,
    generatedSuggestionId: string
  ): Promise<FixSuggestionInfo | undefined> {
    if (
      !comment ||
      !comment.path ||
      !comment.patch_set ||
      !this.suggestionsProvider?.suggestFix
    ) {
      return;
    }
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_REQUEST, {
      uuid: generatedSuggestionId,
      type: 'suggest-fix',
      commentId: comment.id,
      fileExtension: getFileExtension(comment.path ?? ''),
    });
    const suggestionResponse = await this.suggestionsProvider.suggestFix({
      prompt: messageText,
      changeInfo,
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
}
