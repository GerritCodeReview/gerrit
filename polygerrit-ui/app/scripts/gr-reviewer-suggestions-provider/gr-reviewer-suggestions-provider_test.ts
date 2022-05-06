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
  AccountId,
  AccountInfo,
  ChangeInfo,
  EmailAddress,
  GroupId,
  GroupName,
  NumericChangeId,
} from '../../api/rest-api';
import {SuggestedReviewerInfo} from '../../types/common';
import {createChange, createServerInfo} from '../../test/test-data-generators';

suite('GrReviewerSuggestionsProvider tests', () => {
  let _nextAccountId = 0;
  function makeAccount(opt_status?: string): AccountInfo {
    const accountId = ++_nextAccountId;
    return {
      _account_id: accountId as AccountId,
      name: `name ${accountId}`,
      email: `email ${accountId}` as EmailAddress,
      status: opt_status,
    };
  }
  let _nextAccountId2 = 0;
  function makeAccount2(opt_status?: string): AccountInfo {
    const accountId2 = ++_nextAccountId2;
    return {
      _account_id: accountId2 as AccountId,
      name: `name ${accountId2}`,
      status: opt_status,
    };
  }

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

  setup(async () => {
    owner = makeAccount();
    existingReviewer1 = makeAccount();
    existingReviewer2 = makeAccount();
    suggestion1 = {account: makeAccount(), count: 1};
    suggestion2 = {account: makeAccount(), count: 1};
    suggestion3 = {
      group: {
        id: 'suggested group id' as GroupId,
        name: 'suggested group' as GroupName,
      },
      count: 1,
    };

    stubRestApi('getConfig').resolves(createServerInfo());

    change = {
      ...createChange(),
      _number: 42 as NumericChangeId,
      owner,
      reviewers: {
        CC: [existingReviewer1],
        REVIEWER: [existingReviewer2],
      },
    };

    await flush();
  });

  suite('allowAnyUser set to false', () => {
    setup(async () => {
      provider = GrReviewerSuggestionsProvider.create(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER,
        change._number
      );
      await provider.init();
    });

    suite('stubbed values for _getReviewerSuggestions', () => {
      let getChangeSuggestedReviewersStub: sinon.SinonStub;
      setup(() => {
        getChangeSuggestedReviewersStub = stubRestApi(
          'getChangeSuggestedReviewers'
        ).callsFake(() => {
          redundantSuggestion1 = {account: existingReviewer1, count: 1};
          redundantSuggestion2 = {account: existingReviewer2, count: 1};
          redundantSuggestion3 = {account: owner, count: 1};
          return Promise.resolve([
            redundantSuggestion1,
            redundantSuggestion2,
            redundantSuggestion3,
            suggestion1,
            suggestion2,
            suggestion3,
          ]);
        });
      });

      test('makeSuggestionItem formats account or group accordingly', () => {
        let account = makeAccount();
        const account3 = makeAccount2();
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

        provider.config = {
          ...createServerInfo(),
          user: {
            anonymous_coward_name: 'Anonymous Coward Name',
          },
        };

        suggestion = provider.makeSuggestionItem({account: {}, count: 1});
        assert.deepEqual(suggestion, {
          name: 'Anonymous Coward Name',
          value: {account: {}, count: 1},
        });

        account = makeAccount('OOO');

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

      test('getSuggestions', async () => {
        const reviewers = await provider.getSuggestions('');

        // Default is no filtering.
        assert.equal(reviewers.length, 6);
        assert.deepEqual(reviewers, [
          redundantSuggestion1,
          redundantSuggestion2,
          redundantSuggestion3,
          suggestion1,
          suggestion2,
          suggestion3,
        ]);
      });

      test('getSuggestions short circuits when logged out', () => {
        provider.loggedIn = false;
        return provider.getSuggestions('').then(() => {
          assert.isFalse(getChangeSuggestedReviewersStub.called);
          provider.loggedIn = true;
          return provider.getSuggestions('').then(() => {
            assert.isTrue(getChangeSuggestedReviewersStub.called);
          });
        });
      });
    });

    test('getChangeSuggestedReviewers is used', async () => {
      const suggestReviewerStub = stubRestApi(
        'getChangeSuggestedReviewers'
      ).returns(Promise.resolve([]));
      const suggestAccountStub = stubRestApi('getSuggestedAccounts').returns(
        Promise.resolve([])
      );

      await provider.getSuggestions('');
      assert.isTrue(suggestReviewerStub.calledOnce);
      assert.isTrue(suggestReviewerStub.calledWith(42 as NumericChangeId, ''));
      assert.isFalse(suggestAccountStub.called);
    });
  });

  suite('allowAnyUser set to true', () => {
    setup(async () => {
      provider = GrReviewerSuggestionsProvider.create(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.ANY,
        change._number
      );
      await provider.init();
    });

    test('getSuggestedAccounts is used', async () => {
      const suggestReviewerStub = stubRestApi(
        'getChangeSuggestedReviewers'
      ).returns(Promise.resolve([]));
      const suggestAccountStub = stubRestApi('getSuggestedAccounts').returns(
        Promise.resolve([])
      );

      await provider.getSuggestions('');
      assert.isFalse(suggestReviewerStub.called);
      assert.isTrue(suggestAccountStub.calledOnce);
      assert.isTrue(suggestAccountStub.calledWith('cansee:42 '));
    });
  });

  suite('suggestions for multiple changes', () => {
    setup(async () => {
      stubRestApi('getLoggedIn').resolves(true);
    });

    test('only returns REVIEWER suggestions for all changes', async () => {
      stubRestApi('getChangeSuggestedReviewers')
        .resolves([suggestion1, suggestion2, suggestion3])
        .resolves([suggestion2, suggestion3]);
      provider = GrReviewerSuggestionsProvider.create(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER,
        ...[change._number, 43 as NumericChangeId]
      );
      await provider.init();

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
      provider = GrReviewerSuggestionsProvider.create(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.CC,
        ...[change._number, 43 as NumericChangeId]
      );
      await provider.init();

      // suggestion1 is excluded because it is not returned for the second
      // change.
      assert.sameDeepMembers(await provider.getSuggestions('s'), [
        suggestion2,
        suggestion3,
      ]);
    });

    test('only returns ANY suggestions for all changes', async () => {
      stubRestApi('getSuggestedAccounts')
        .resolves([existingReviewer1, existingReviewer2])
        .resolves([existingReviewer1]);
      provider = GrReviewerSuggestionsProvider.create(
        getAppContext().restApiService,
        SUGGESTIONS_PROVIDERS_USERS_TYPES.ANY,
        ...[change._number, 43 as NumericChangeId]
      );
      await provider.init();

      // existingReviewer2 is excluded because it is not returned for the second
      // change.
      assert.sameDeepMembers(await provider.getSuggestions('s'), [
        existingReviewer1,
      ]);
    });
  });
});
