/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
import {ReviewerState} from '../../api/rest-api';

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
    private type: ReviewerState.REVIEWER | ReviewerState.CC,
    private config: ServerInfo | undefined,
    private loggedIn: boolean,
    ...changeNumbers: NumericChangeId[]
  ) {
    this.changeNumbers = changeNumbers;
  }

  async getSuggestions(input: string): Promise<Suggestion[]> {
    if (!this.loggedIn) return [];

    const allResults = await allSettled(
      this.changeNumbers.map(changeNumber =>
        this.getSuggestionsForChange(changeNumber, input)
      )
    );
    const allSuggestions = allResults
      .filter(isFulfilled)
      .map(result => result.value)
      .filter(notUndefined);
    return intersection(allSuggestions, (s1, s2) =>
      this.areSameSuggestions(s1, s2)
    );
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

    if (this.isAccountSuggestion(suggestion)) {
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
  ): Promise<SuggestedReviewerInfo[] | undefined> {
    return this.type === ReviewerState.REVIEWER
      ? this.restApi.getChangeSuggestedReviewers(changeNumber, input)
      : this.restApi.getChangeSuggestedCCs(changeNumber, input);
  }

  private areSameSuggestions(a: Suggestion, b: Suggestion): boolean {
    if (isReviewerAccountSuggestion(a) && isReviewerAccountSuggestion(b)) {
      return accountKey(a.account) === accountKey(b.account);
    } else if (isReviewerGroupSuggestion(a) && isReviewerGroupSuggestion(b)) {
      return a.group.id === b.group.id;
    } else if (this.isAccountSuggestion(a) && this.isAccountSuggestion(b)) {
      return accountKey(a) === accountKey(b);
    }
    return false;
  }

  private isAccountSuggestion(s: Suggestion): s is AccountInfo {
    return (s as AccountInfo)._account_id !== undefined;
  }
}
