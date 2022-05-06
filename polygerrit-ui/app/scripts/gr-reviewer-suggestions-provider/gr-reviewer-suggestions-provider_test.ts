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

import '../../test/common-test-setup-karma';
import {
  GrReviewerSuggestionsProvider,
  SUGGESTIONS_PROVIDERS_USERS_TYPES,
} from './gr-reviewer-suggestions-provider';
import {getAppContext} from '../../services/app-context';
import {stubRestApi} from '../../test/test-utils';
import {
  AccountInfo,
  ChangeInfo,
  GroupId,
  GroupName,
  NumericChangeId,
} from '../../api/rest-api';
import {SuggestedReviewerInfo} from '../../types/common';
import {
  createAccountDetailWithIdNameAndEmail,
  createChange,
  createServerInfo,
} from '../../test/test-data-generators';

suite('GrReviewerSuggestionsProvider tests', () => {
  let owner: AccountInfo;
  let existingReviewer1: AccountInfo;
  let existingReviewer2: AccountInfo;
  let suggestion1: SuggestedReviewerInfo;
  let suggestion2: SuggestedReviewerInfo;
  let suggestion3: SuggestedReviewerInfo;
  let provider: GrReviewerSuggestionsProvider;
  let redundantSuggestion1: SuggestedReviewerInfo;
  let redundantSuggestion2: SuggestedReviewerInfo;
  let redundantSuggestion3: SuggestedReviewerInfo;
  let change: ChangeInfo;

  setup(() => {
    owner = createAccountDetailWithIdNameAndEmail(1);
    existingReviewer1 = createAccountDetailWithIdNameAndEmail(2);
    existingReviewer2 = createAccountDetailWithIdNameAndEmail(3);
    suggestion1 = {account: createAccountDetailWithIdNameAndEmail(4), count: 1};
    suggestion2 = {account: createAccountDetailWithIdNameAndEmail(5), count: 1};
    suggestion3 = {
      group: {
        id: 'suggested group id' as GroupId,
        name: 'suggested group' as GroupName,
      },
      count: 1,
    };

    change = {
      ...createChange(),
      _number: 42 as NumericChangeId,
      owner,
      reviewers: {
        CC: [existingReviewer1],
        REVIEWER: [existingReviewer2],
      },
    };
  });

  suite('suggestions for single change', () => {
    let getChangeSuggestedReviewersStub: sinon.SinonStub;
    setup(() => {
      provider = new GrReviewerSuggestionsProvider(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER,
        createServerInfo(),
        true,
        change._number
      );
      redundantSuggestion1 = {account: existingReviewer1, count: 1};
      redundantSuggestion2 = {account: existingReviewer2, count: 1};
      redundantSuggestion3 = {account: owner, count: 1};
      getChangeSuggestedReviewersStub = stubRestApi(
        'getChangeSuggestedReviewers'
      ).resolves([
        redundantSuggestion1,
        redundantSuggestion2,
        redundantSuggestion3,
        suggestion1,
        suggestion2,
        suggestion3,
      ]);
    });

    test('getSuggestions', async () => {
      const reviewers = await provider.getSuggestions('');

      // Default is no filtering.
      assert.sameDeepMembers(reviewers, [
        redundantSuggestion1,
        redundantSuggestion2,
        redundantSuggestion3,
        suggestion1,
        suggestion2,
        suggestion3,
      ]);
    });

    test('getSuggestions short circuits when logged out', async () => {
      await provider.getSuggestions('');
      assert.isTrue(getChangeSuggestedReviewersStub.calledOnce);

      // not logged in
      provider = new GrReviewerSuggestionsProvider(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER,
        createServerInfo(),
        false,
        change._number
      );

      await provider.getSuggestions('');

      // no additional call is made
      assert.isTrue(getChangeSuggestedReviewersStub.calledOnce);
    });

    test('makeSuggestionItem formats account or group accordingly', () => {
      let account = createAccountDetailWithIdNameAndEmail(1);
      const account3 = createAccountDetailWithIdNameAndEmail(2);
      let suggestion = provider.makeSuggestionItem({account, count: 1});
      assert.deepEqual(suggestion, {
        name: `${account.name} <${account.email}>`,
        value: {account, count: 1},
      });

      const group = {name: 'test' as GroupName, id: '5' as GroupId};
      suggestion = provider.makeSuggestionItem({group, count: 1});
      assert.deepEqual(suggestion, {
        name: `${group.name} (group)`,
        value: {group, count: 1},
      });

      suggestion = provider.makeSuggestionItem(account);
      assert.deepEqual(suggestion, {
        name: `${account.name} <${account.email}>`,
        value: {account, count: 1},
      });

      suggestion = provider.makeSuggestionItem({account: {}, count: 1});
      assert.deepEqual(suggestion, {
        name: 'Name of user not set',
        value: {account: {}, count: 1},
      });

      provider = new GrReviewerSuggestionsProvider(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER,
        {
          ...createServerInfo(),
          user: {
            anonymous_coward_name: 'Anonymous Coward Name',
          },
        },
        true,
        change._number
      );

      suggestion = provider.makeSuggestionItem({account: {}, count: 1});
      assert.deepEqual(suggestion, {
        name: 'Anonymous Coward Name',
        value: {account: {}, count: 1},
      });

      account = {...createAccountDetailWithIdNameAndEmail(3), status: 'OOO'};

      suggestion = provider.makeSuggestionItem({account, count: 1});
      assert.deepEqual(suggestion, {
        name: `${account.name} <${account.email}> (OOO)`,
        value: {account, count: 1},
      });

      suggestion = provider.makeSuggestionItem(account);
      assert.deepEqual(suggestion, {
        name: `${account.name} <${account.email}> (OOO)`,
        value: {account, count: 1},
      });

      account3.email = undefined;

      suggestion = provider.makeSuggestionItem(account3);
      assert.deepEqual(suggestion, {
        name: account3.name,
        value: {account: account3, count: 1},
      });
    });
  });

  suite('suggestions for multiple changes', () => {
    test('only returns REVIEWER suggestions for all changes', async () => {
      stubRestApi('getChangeSuggestedReviewers')
        .resolves([suggestion1, suggestion2, suggestion3])
        .resolves([suggestion2, suggestion3]);
      provider = new GrReviewerSuggestionsProvider(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER,
        createServerInfo(),
        true,
        ...[change._number, 43 as NumericChangeId]
      );

      // suggestion1 is excluded because it is not returned for the second
      // change.
      assert.sameDeepMembers(await provider.getSuggestions('s'), [
        suggestion2,
        suggestion3,
      ]);
    });

    test('only returns CC suggestions for all changes', async () => {
      stubRestApi('getChangeSuggestedCCs')
        .resolves([suggestion1, suggestion2, suggestion3])
        .resolves([suggestion2, suggestion3]);
      provider = new GrReviewerSuggestionsProvider(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.CC,
        createServerInfo(),
        true,
        ...[change._number, 43 as NumericChangeId]
      );

      // suggestion1 is excluded because it is not returned for the second
      // change.
      assert.sameDeepMembers(await provider.getSuggestions('s'), [
        suggestion2,
        suggestion3,
      ]);
    });
  });
});
