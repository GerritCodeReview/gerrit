/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';
import {AutocompleteSuggestion} from './autocomplete-util';
import {getAppContext} from '../services/app-context';
import {RepoName} from '../types/common';

export class LabelSuggestionsProvider {
  private readonly restApiService: RestApiService;

  private repoName?: RepoName;

  constructor(restApiService: RestApiService) {
    this.restApiService = restApiService;
  }

  setRepoName(repoName?: RepoName) {
    this.repoName = repoName;
  }

  getSuggestions = (
    predicate: string,
    expression: string
  ): Promise<AutocompleteSuggestion[]> => {
    if (!this.repoName) return Promise.resolve([]);

    return this.restApiService
      .getRepoLabels(this.repoName)
      .then(labels => {
        if (!labels) return [];
        return labels
          .map(label => label.name)
          .filter(name => name.toLowerCase().includes(expression.toLowerCase()))
          .map(name => {
            return {text: `${predicate}:${name}`};
          });
      })
      .catch(err => {
        getAppContext().reportingService.error('LabelSuggestionsProvider', err);
        return [];
      });
  };
}
