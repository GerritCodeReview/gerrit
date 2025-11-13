/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {
  isReviewerAccountSuggestion,
  isReviewerGroupSuggestion,
  NumericChangeId,
  ServerInfo,
  SuggestedReviewerAccountInfo,
  SuggestedReviewerInfo,
  Suggestion,
} from '../../types/common';
import {AutocompleteSuggestion} from '../../utils/autocomplete-util';
import {allSettled, isFulfilled} from '../../utils/async-util';
import {isDefined, ParsedChangeInfo} from '../../types/types';
import {
  accountKey,
  getSuggestedReviewerName,
  isAccountSuggestion,
} from '../../utils/account-util';
import {
  AccountId,
  ChangeInfo,
  EmailAddress,
  GroupId,
  ReviewerState,
} from '../../api/rest-api';
import {throwingErrorCallback} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

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

  /**
   * Requests related suggestions.
   *
   * If the request fails the returned promise is rejected.
   */
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
    const name = getSuggestedReviewerName(suggestion, this.config);
    const value = isAccountSuggestion(suggestion)
      ? ({account: suggestion, count: 1} as SuggestedReviewerAccountInfo)
      : suggestion;
    return {name, value};
  }

  private getSuggestionsForChange(
    changeNumber: NumericChangeId,
    input: string
  ): Promise<SuggestedReviewerInfo[] | undefined> {
    return this.type === ReviewerState.REVIEWER
      ? this.restApi.getChangeSuggestedReviewers(
          changeNumber,
          input,
          throwingErrorCallback
        )
      : this.restApi.getChangeSuggestedCCs(
          changeNumber,
          input,
          throwingErrorCallback
        );
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
