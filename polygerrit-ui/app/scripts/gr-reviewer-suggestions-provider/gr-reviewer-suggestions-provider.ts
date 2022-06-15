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
import {assertNever} from '../../utils/common-util';
import {AutocompleteSuggestion} from '../../elements/shared/gr-autocomplete/gr-autocomplete';
import {allSettled, isFulfilled} from '../../utils/async-util';
import {notUndefined} from '../../types/types';
import {accountKey} from '../../utils/account-util';
import {ChangeInfo, ReviewerState} from '../../api/rest-api';

export interface ReviewerSuggestionsProvider {
  getSuggestions(input: string): Promise<Suggestion[]>;
  makeSuggestionItem(
    suggestion: Suggestion
  ): AutocompleteSuggestion<SuggestedReviewerInfo>;
}

export class GrReviewerSuggestionsProvider
  implements ReviewerSuggestionsProvider
{
  private changes: ChangeInfo[];

  constructor(
    private restApi: RestApiService,
    private type: ReviewerState.REVIEWER | ReviewerState.CC,
    private config: ServerInfo | undefined,
    private loggedIn: boolean,
    ...changes: ChangeInfo[]
  ) {
    this.changes = changes;
  }

  async getSuggestions(input: string): Promise<Suggestion[]> {
    if (!this.loggedIn) return [];

    const resultsByChangeIndex = await allSettled(
      this.changes.map(change =>
        this.getSuggestionsForChange(change._number, input)
      )
    );
    const suggestionsByChangeIndex = resultsByChangeIndex
      .filter(isFulfilled)
      .map(result => result.value)
      .filter(notUndefined);
    if (suggestionsByChangeIndex.length !== resultsByChangeIndex.length) {
      // one of the requests failed, so don't allow any suggestions.
      return [];
    }

    // Pass the union of all the suggestions through each change, keeping only
    // suggestions where either:
    //   A) the change had the suggestion too, or
    //   B) the suggestion is already a reviewer/CC on the change (depending on
    //      this.type).
    return this.changes.reduce((suggestions, change, changeIndex) => {
      const reviewerAndSuggestionKeys = [
        ...(change.reviewers[this.type]?.map(accountKey) ?? []),
        ...suggestionsByChangeIndex[changeIndex].map(this.suggestionKey),
      ];
      return suggestions.filter(suggestion =>
        reviewerAndSuggestionKeys.includes(this.suggestionKey(suggestion))
      );
    }, this.uniqueSuggestions(suggestionsByChangeIndex.flat()));
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

  private uniqueSuggestions(suggestions: Suggestion[]): Suggestion[] {
    return suggestions.filter(
      (suggestion, index) =>
        index ===
        suggestions.findIndex(
          other => this.suggestionKey(suggestion) === this.suggestionKey(other)
        )
    );
  }

  private suggestionKey(suggestion: Suggestion) {
    if (isReviewerAccountSuggestion(suggestion)) {
      return accountKey(suggestion.account);
    } else if (isReviewerGroupSuggestion(suggestion)) {
      return suggestion.group.id;
    } else if (this.isAccountSuggestion(suggestion)) {
      return accountKey(suggestion);
    }
    return undefined;
  }

  private isAccountSuggestion(s: Suggestion): s is AccountInfo {
    return (s as AccountInfo)._account_id !== undefined;
  }
}
