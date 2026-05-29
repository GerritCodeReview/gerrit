/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';
import {AutocompleteSuggestion} from '../utils/autocomplete-util';
import {getAppContext} from '../services/app-context';
import {RepoName} from '../types/common';
import {LabelDefinitionInfo} from '../api/rest-api';

export class LabelSuggestionsProvider {
  private repoName?: RepoName;

  private cachedLabelsPromise?: Promise<LabelDefinitionInfo[] | undefined>;

  constructor(readonly restApiService: RestApiService) {}

  setRepoName(repoName?: RepoName) {
    if (this.repoName !== repoName) {
      this.repoName = repoName;
      this.cachedLabelsPromise = undefined; // Invalidate cache for new repo
    }
  }

  async getSuggestions(
    predicate: string,
    expression: string
  ): Promise<AutocompleteSuggestion[]> {
    if (!this.repoName) return [];

    if (!this.cachedLabelsPromise) {
      this.cachedLabelsPromise = this.restApiService
        .getRepoLabels(this.repoName)
        .catch(err => {
          getAppContext().reportingService.error(
            'LabelSuggestionsProvider',
            err
          );
          return undefined; // Ensure caught errors resolve safely and don't break the cache
        });
    }

    return this.cachedLabelsPromise.then(labels => {
      if (!labels) return [];
      return labels
        .map(label => label.name)
        .filter(name => name.toLowerCase().includes(expression.toLowerCase()))
        .map(name => {
          return {text: `${predicate}:${name}`};
        });
    });
  }
}
