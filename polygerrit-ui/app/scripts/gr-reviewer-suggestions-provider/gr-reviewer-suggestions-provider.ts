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
import {accountKey} from '../../utils/account-util';

// TODO(TS): enum name doesn't follow typescript style guide rules
// Rename it
export enum SUGGESTIONS_PROVIDERS_USERS_TYPES {
  REVIEWER = 'reviewers',
  CC = 'ccs',
}

function isAccountSuggestions(s: Suggestion): s is AccountInfo {
  return (s as AccountInfo)._account_id !== undefined;
}

function areSameSuggestions(a: Suggestion, b: Suggestion): boolean {
  if (isReviewerAccountSuggestion(a) && isReviewerAccountSuggestion(b)) {
    return accountKey(a.account) === accountKey(b.account);
  } else if (isReviewerGroupSuggestion(a) && isReviewerGroupSuggestion(b)) {
    return a.group.id === b.group.id;
  } else if (isAccountSuggestions(a) && isAccountSuggestions(b)) {
    return accountKey(a) === accountKey(b);
  }
  return false;
}
export interface ReviewerSuggestionsProvider {
  getSuggestions(input: string): Promise<Suggestion[]>;
  makeSuggestionItem(
    suggestion: Suggestion
  ): AutocompleteSuggestion<SuggestedReviewerInfo>;
}

export class GrReviewerSuggestionsProvider
  implements ReviewerSuggestionsProvider
{
  private changeNumbers: NumericChangeId[];

  constructor(
    private restApi: RestApiService,
    private type: SUGGESTIONS_PROVIDERS_USERS_TYPES,
    private config: ServerInfo | undefined,
    private loggedIn: boolean,
    ...changeNumbers: NumericChangeId[]
  ) {
    this.changeNumbers = changeNumbers;
  }

  async getSuggestions(input: string): Promise<Suggestion[]> {
    if (!this.loggedIn) {
      return [];
    }

    const allResults = await allSettled(
      this.changeNumbers.map(changeNumber =>
        this.getSuggestionsForChange(changeNumber, input)
      )
    );
    const allSuggestions = allResults
      .filter(isFulfilled)
      .map(result => result.value)
      .filter(notUndefined);
    return intersection(allSuggestions, areSameSuggestions);
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

  private getSuggestionsForChange(
    changeNumber: NumericChangeId,
    input: string
  ): Promise<Suggestion[] | undefined> {
    return this.type === SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER
      ? this.restApi.getChangeSuggestedReviewers(changeNumber, input)
      : this.restApi.getChangeSuggestedCCs(changeNumber, input);
  }
}
