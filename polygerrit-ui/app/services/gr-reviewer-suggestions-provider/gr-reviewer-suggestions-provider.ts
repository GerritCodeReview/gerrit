/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  getAccountDisplayName,
  getGroupDisplayName,
} from '../../utils/display-name-util';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
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
import {isDefined, ParsedChangeInfo} from '../../types/types';
import {accountKey} from '../../utils/account-util';
import {
  AccountId,
  ChangeInfo,
  EmailAddress,
  GroupId,
  ReviewerState,
} from '../../api/rest-api';

export interface ReviewerSuggestionsProvider {
  getSuggestions(input: string): Promise<Suggestion[]>;
  makeSuggestionItem(
    suggestion: Suggestion
  ): AutocompleteSuggestion<SuggestedReviewerInfo>;
}

export class GrReviewerSuggestionsProvider
  implements ReviewerSuggestionsProvider
{
  private changes: (ChangeInfo | ParsedChangeInfo)[];

  constructor(
    private restApi: RestApiService,
    private type: ReviewerState.REVIEWER | ReviewerState.CC,
    private config: ServerInfo | undefined,
    private loggedIn: boolean,
    ...changes: (ChangeInfo | ParsedChangeInfo)[]
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
      .filter(isDefined);
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
      const reviewerAndSuggestionKeys = new Set<
        AccountId | EmailAddress | GroupId | undefined
      >([
        ...(change.reviewers[this.type]?.map(accountKey) ?? []),
        ...suggestionsByChangeIndex[changeIndex].map(suggestionKey),
      ]);
      return suggestions.filter(suggestion =>
        reviewerAndSuggestionKeys.has(suggestionKey(suggestion))
      );
    }, uniqueSuggestions(suggestionsByChangeIndex.flat()));
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

    if (isAccountSuggestion(suggestion)) {
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
}

function uniqueSuggestions(suggestions: Suggestion[]): Suggestion[] {
  return suggestions.filter(
    (suggestion, index) =>
      index ===
      suggestions.findIndex(
        other => suggestionKey(suggestion) === suggestionKey(other)
      )
  );
}

function suggestionKey(suggestion: Suggestion) {
  if (isReviewerAccountSuggestion(suggestion)) {
    return accountKey(suggestion.account);
  } else if (isReviewerGroupSuggestion(suggestion)) {
    return suggestion.group.id;
  } else if (isAccountSuggestion(suggestion)) {
    return accountKey(suggestion);
  }
  return undefined;
}

function isAccountSuggestion(s: Suggestion): s is AccountInfo {
  return (s as AccountInfo)._account_id !== undefined;
}
