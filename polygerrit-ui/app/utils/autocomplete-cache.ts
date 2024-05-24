/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
export interface AutocompletionContext {
  draftContent: string;
  draftContentLength?: number;
  commentCompletion: string;
  commentCompletionLength?: number;

  isFullCommentPrediction?: boolean;
  draftInSyncWithSuggestionLength?: number;
  modelVersion?: string;
  outcome?: number;
  requestDurationMs?: number;

  commentId?: string;
  commentNumber?: number;
  filePath?: string;
  fileExtension?: string;

  similarCharacters?: number;
  maxSimilarCharacters?: number;
  acceptedSuggestionsCount?: number;
  totalAcceptedCharacters?: number;
  savedDraftLength?: number;

  hasDraftChanged?: boolean;
}

/**
 * Caching for autocompleting text, e.g. comments.
 *
 * If the user continues typing text that matches the completion hint, then keep the hint.
 *
 * If the user backspaces, then continue using previous hint.
 */
export class AutocompleteCache {
  /**
   * We are using an ordered list instead of a map here, because we want to evict the oldest
   * entries, if the capacity is exceeded. And we want to prefer newer entries over older
   * entries, if both match the criteria for being reused.
   */
  private cache: AutocompletionContext[] = [];

  constructor(private readonly capacity = 10) {}

  get(content: string): AutocompletionContext | undefined {
    if (content === '') return undefined;
    for (let i = this.cache.length - 1; i >= 0; i--) {
      const cachedContext = this.cache[i];
      const completionContent = cachedContext.draftContent;
      const completionHint = cachedContext.commentCompletion;
      const completionFull = completionContent + completionHint;
      if (completionContent.length > content.length) continue;
      if (!completionFull.startsWith(content)) continue;
      if (completionFull === content) continue;
      const hint = completionFull.substring(content.length);
      return {
        ...cachedContext,
        draftContent: content,
        commentCompletion: hint,
        draftInSyncWithSuggestionLength:
          content.length - completionContent.length,
      };
    }
    return undefined;
  }

  set(context: AutocompletionContext) {
    const index = this.cache.findIndex(
      c => c.draftContent === context.draftContent
    );
    if (index !== -1) {
      this.cache.splice(index, 1);
    } else if (this.cache.length >= this.capacity) {
      this.cache.shift();
    }
    this.cache.push(context);
  }
}
