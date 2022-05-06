/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {
  getAccountDisplayName,
  getGroupDisplayName,
} from '../../utils/display-name-util';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {
  AccountInfo,
  isReviewerAccountSuggestion,
  isReviewerGroupSuggestion,
  NumericChangeId,
  ServerInfo,
  SuggestedReviewerInfo,
  Suggestion,
} from '../../types/common';
import {assertNever, intersection} from '../../utils/common-util';
import {AutocompleteSuggestion} from '../../elements/shared/gr-autocomplete/gr-autocomplete';
import {allSettled, isFulfilled} from '../../utils/async-util';
import {notUndefined} from '../../types/types';

// TODO(TS): enum name doesn't follow typescript style guid rules
// Rename it
export enum SUGGESTIONS_PROVIDERS_USERS_TYPES {
  REVIEWER = 'reviewers',
  CC = 'ccs',
  ANY = 'any',
}

export function isAccountSuggestions(s: Suggestion): s is AccountInfo {
  return (s as AccountInfo)._account_id !== undefined;
}

type ApiCallCallback = (input: string) => Promise<Suggestion[] | void>;

function areSameSuggestions(
  a: SuggestedReviewerInfo,
  b: SuggestedReviewerInfo
): boolean {
  if (isReviewerAccountSuggestion(a) && isReviewerAccountSuggestion(b)) {
    return a.account._account_id === b.account._account_id;
  } else if (isReviewerGroupSuggestion(a) && isReviewerGroupSuggestion(b)) {
    return a.group.id === b.group.id;
  }
  return false;
}
export interface ReviewerSuggestionsProvider {
  init(): void;
  getSuggestions(input: string): Promise<Suggestion[]>;
  makeSuggestionItem(
    suggestion: Suggestion
  ): AutocompleteSuggestion<SuggestedReviewerInfo>;
}

export class GrReviewerSuggestionsProvider
  implements ReviewerSuggestionsProvider
{
  static create(
    restApi: RestApiService,
    userType: SUGGESTIONS_PROVIDERS_USERS_TYPES,
    ...changeNumbers: NumericChangeId[]
  ) {
    switch (userType) {
      case SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER:
        return new GrReviewerSuggestionsProvider(restApi, async input => {
          const allResults = await allSettled(
            changeNumbers.map(changeNumber =>
              restApi.getChangeSuggestedReviewers(changeNumber, input)
            )
          );
          const allSuggestions = allResults
            .filter(isFulfilled)
            .map(result => result.value)
            .filter(notUndefined);
          return intersection(allSuggestions, areSameSuggestions);
        });
      case SUGGESTIONS_PROVIDERS_USERS_TYPES.CC:
        return new GrReviewerSuggestionsProvider(restApi, async input => {
          const allResults = await allSettled(
            changeNumbers.map(changeNumber =>
              restApi.getChangeSuggestedCCs(changeNumber, input)
            )
          );
          const allSuggestions = allResults
            .filter(isFulfilled)
            .map(result => result.value)
            .filter(notUndefined);
          return intersection(allSuggestions, areSameSuggestions);
        });
      case SUGGESTIONS_PROVIDERS_USERS_TYPES.ANY:
        return new GrReviewerSuggestionsProvider(restApi, async input => {
          const allResults = await allSettled(
            changeNumbers.map(changeNumber =>
              restApi.getSuggestedAccounts(`cansee:${changeNumber} ${input}`)
            )
          );
          const allSuggestions = allResults
            .filter(isFulfilled)
            .map(result => result.value)
            .filter(notUndefined);
          return intersection(
            allSuggestions,
            (a, b) => a._account_id === b._account_id
          );
        });
      default:
        throw new Error(`Unknown users type: ${userType}`);
    }
  }

  private initPromise?: Promise<void>;

  config?: ServerInfo;

  loggedIn = false;

  private initialized = false;

  private constructor(
    private readonly _restAPI: RestApiService,
    private readonly _apiCall: ApiCallCallback
  ) {}

  init() {
    if (this.initPromise) {
      return this.initPromise;
    }
    const getConfigPromise = this._restAPI.getConfig().then(cfg => {
      this.config = cfg;
    });
    const getLoggedInPromise = this._restAPI.getLoggedIn().then(loggedIn => {
      this.loggedIn = loggedIn;
    });
    this.initPromise = Promise.all([getConfigPromise, getLoggedInPromise]).then(
      () => {
        this.initialized = true;
      }
    );
    return this.initPromise;
  }

  getSuggestions(input: string): Promise<Suggestion[]> {
    if (!this.initialized || !this.loggedIn) {
      return Promise.resolve([]);
    }

    return this._apiCall(input).then(reviewers => reviewers || []);
  }

  makeSuggestionItem(
    suggestion: Suggestion
  ): AutocompleteSuggestion<SuggestedReviewerInfo> {
    if (isReviewerAccountSuggestion(suggestion)) {
      // Reviewer is an account suggestion from getChangeSuggestedReviewers.
      return {
        name: getAccountDisplayName(this.config, suggestion.account),
        value: suggestion,
      };
    }

    if (isReviewerGroupSuggestion(suggestion)) {
      // Reviewer is a group suggestion from getChangeSuggestedReviewers.
      return {
        name: getGroupDisplayName(suggestion.group),
        value: suggestion,
      };
    }

    if (isAccountSuggestions(suggestion)) {
      // Reviewer is an account suggestion from getSuggestedAccounts.
      return {
        name: getAccountDisplayName(this.config, suggestion),
        value: {account: suggestion, count: 1},
      };
    }
    assertNever(suggestion, 'Received an incorrect suggestion');
  }
}
