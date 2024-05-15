/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
export interface Autocompletion {
  completionContent: string;
  completionHint: string;
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
  private cache: Autocompletion[] = [];

  constructor(private readonly capacity = 10) {}

  get(content: string): string | undefined {
    if (content === '') return undefined;
    for (let i = this.cache.length - 1; i >= 0; i--) {
      const {completionContent, completionHint} = this.cache[i];
      const completionFull = completionContent + completionHint;
      if (completionContent.length > content.length) continue;
      if (!completionFull.startsWith(content)) continue;
      if (completionFull === content) continue;
      return completionFull.substring(content.length);
    }
    return undefined;
  }

  set(content: string, hint: string) {
    const completion = {completionContent: content, completionHint: hint};
    const index = this.cache.findIndex(c => c.completionContent === content);
    if (index !== -1) {
      this.cache.splice(index, 1);
    } else if (this.cache.length >= this.capacity) {
      this.cache.shift();
    }
    this.cache.push(completion);
  }
}
