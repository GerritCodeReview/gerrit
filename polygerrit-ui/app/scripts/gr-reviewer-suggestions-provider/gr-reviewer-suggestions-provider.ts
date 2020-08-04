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
import {
  RestApiService,
  SuggestedReviewerAccountInfo,
  SuggestedReviewerGroupInfo,
  SuggestedReviewerInfo,
} from '../../services/services/gr-rest-api/gr-rest-api';
import {AccountInfo, NumericChangeId, ServerInfo} from '../../types/common';
import {assertNever} from '../../utils/common-util';

// TODO(TS): enum name doesn't follow typescript style guid rules
// Rename it
export enum SUGGESTIONS_PROVIDERS_USERS_TYPES {
  REVIEWER = 'reviewers',
  CC = 'ccs',
  ANY = 'any',
}

export type Suggestion = SuggestedReviewerInfo | AccountInfo;

export function isAccountSuggestions(s: Suggestion): s is AccountInfo {
  return (s as AccountInfo)._account_id !== undefined;
}

export function isReviewerAccountSuggestion(
  s: Suggestion
): s is SuggestedReviewerAccountInfo {
  return (s as SuggestedReviewerAccountInfo).account !== undefined;
}

export function isReviewerGroupSuggestion(
  s: Suggestion
): s is SuggestedReviewerGroupInfo {
  return (s as SuggestedReviewerGroupInfo).group !== undefined;
}

type ApiCallCallback = (input: string) => Promise<Suggestion[]>;

export interface SuggestionItem {
  name: string;
  value: SuggestedReviewerInfo;
}

export class GrReviewerSuggestionsProvider {
  static create(
    restApi: RestApiService,
    changeNumber: NumericChangeId,
    userType: SUGGESTIONS_PROVIDERS_USERS_TYPES
  ) {
    switch (userType) {
      case SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER:
        return new GrReviewerSuggestionsProvider(restApi, input =>
          restApi.getChangeSuggestedReviewers(changeNumber, input)
        );
      case SUGGESTIONS_PROVIDERS_USERS_TYPES.CC:
        return new GrReviewerSuggestionsProvider(restApi, input =>
          restApi.getChangeSuggestedCCs(changeNumber, input)
        );
      case SUGGESTIONS_PROVIDERS_USERS_TYPES.ANY:
        return new GrReviewerSuggestionsProvider(restApi, input =>
          restApi.getSuggestedAccounts(`cansee:${changeNumber} ${input}`)
        );
      default:
        throw new Error(`Unknown users type: ${userType}`);
    }
  }

  private _initPromise?: Promise<void>;

  private _config?: ServerInfo;

  private _loggedIn = false;

  private _initialized = false;

  private constructor(
    private readonly _restAPI: RestApiService,
    private readonly _apiCall: ApiCallCallback
  ) {}

  init() {
    if (this._initPromise) {
      return this._initPromise;
    }
    const getConfigPromise = this._restAPI.getConfig().then(cfg => {
      this._config = cfg;
    });
    const getLoggedInPromise = this._restAPI.getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
    this._initPromise = Promise.all([
      getConfigPromise,
      getLoggedInPromise,
    ]).then(() => {
      this._initialized = true;
    });
    return this._initPromise;
  }

  getSuggestions(input: string): Promise<Suggestion[]> {
    if (!this._initialized || !this._loggedIn) {
      return Promise.resolve([]);
    }

    return this._apiCall(input).then(reviewers => reviewers || []);
  }

  makeSuggestionItem(suggestion: Suggestion): SuggestionItem {
    if (isReviewerAccountSuggestion(suggestion)) {
      // Reviewer is an account suggestion from getChangeSuggestedReviewers.
      return {
        name: getAccountDisplayName(this._config, suggestion.account),
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
        name: getAccountDisplayName(this._config, suggestion),
        value: {account: suggestion, count: 1},
      };
    }
    assertNever(suggestion, 'Received an incorrect suggestion');
  }
}
