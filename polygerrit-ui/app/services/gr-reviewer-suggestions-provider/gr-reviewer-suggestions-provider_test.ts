/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import {GrReviewerSuggestionsProvider} from './gr-reviewer-suggestions-provider';
import {getAppContext} from '../app-context';
import {stubRestApi} from '../../test/test-utils';
import {
  AccountDetailInfo,
  ChangeInfo,
  GroupId,
  GroupName,
  NumericChangeId,
  ReviewerState,
} from '../../api/rest-api';
import {Suggestion} from '../../types/common';
import {
  createAccountDetailWithIdNameAndEmail,
  createChange,
  createServerInfo,
} from '../../test/test-data-generators';
import {assert} from '@open-wc/testing';

const accounts: AccountDetailInfo[] = [
  createAccountDetailWithIdNameAndEmail(1),
  createAccountDetailWithIdNameAndEmail(2),
  createAccountDetailWithIdNameAndEmail(3),
  createAccountDetailWithIdNameAndEmail(4),
  createAccountDetailWithIdNameAndEmail(5),
];
const suggestions: Suggestion[] = [
  {account: accounts[0], count: 1},
  {account: accounts[1], count: 1},
  {
    group: {
      id: 'suggested group id' as GroupId,
      name: 'suggested group' as GroupName,
    },
    count: 4,
  },
  {account: accounts[2], count: 1},
];
const changes: ChangeInfo[] = [
  {...createChange(), reviewers: {REVIEWER: [accounts[2]], CC: [accounts[2]]}},
  {...createChange(), _number: 43 as NumericChangeId},
];

suite('GrReviewerSuggestionsProvider tests', () => {
  let getChangeSuggestedReviewersStub: sinon.SinonStub;
  let getChangeSuggestedCCsStub: sinon.SinonStub;
  let provider: GrReviewerSuggestionsProvider;

  setup(() => {
    getChangeSuggestedReviewersStub = stubRestApi(
      'getChangeSuggestedReviewers'
    );
    getChangeSuggestedCCsStub = stubRestApi('getChangeSuggestedCCs');
    provider = new GrReviewerSuggestionsProvider(
      getAppContext().restApiService,
      ReviewerState.REVIEWER,
      createServerInfo(),
      true,
      ...changes
    );
  });

  test('getSuggestions', async () => {
    getChangeSuggestedReviewersStub.resolves([
      suggestions[0],
      suggestions[1],
      suggestions[2],
    ]);
    const reviewers = await provider.getSuggestions('');

    assert.sameDeepMembers(reviewers, [
      suggestions[0],
      suggestions[1],
      suggestions[2],
    ]);
  });

  test('getSuggestions short circuits when logged out', async () => {
    // logged in
    getChangeSuggestedReviewersStub.resolves([]);
    await provider.getSuggestions('');
    assert.isTrue(getChangeSuggestedReviewersStub.calledTwice);

    // not logged in
    provider = new GrReviewerSuggestionsProvider(
      getAppContext().restApiService,
      ReviewerState.REVIEWER,
      createServerInfo(),
      false,
      ...changes
    );

    await provider.getSuggestions('');

    // no additional calls are made
    assert.isTrue(getChangeSuggestedReviewersStub.calledTwice);
  });

  test('only returns REVIEWER suggestions shared by all changes', async () => {
    getChangeSuggestedReviewersStub
      .onFirstCall()
      .resolves([suggestions[0], suggestions[1], suggestions[2]])
      .onSecondCall()
      .resolves([suggestions[1], suggestions[2], suggestions[3]]);
    provider = new GrReviewerSuggestionsProvider(
      getAppContext().restApiService,
      ReviewerState.REVIEWER,
      createServerInfo(),
      true,
      ...changes
    );

    // suggestions[0] is excluded because it is not returned for the second
    // change.
    // suggestions[3] is included because the first change has the suggestion
    // as a reviewer already.
    assert.sameDeepMembers(await provider.getSuggestions('s'), [
      suggestions[1],
      suggestions[2],
      suggestions[3],
    ]);
  });

  test('only returns CC suggestions shared by all changes', async () => {
    getChangeSuggestedCCsStub
      .onFirstCall()
      .resolves([suggestions[0], suggestions[1], suggestions[2]])
      .onSecondCall()
      .resolves([suggestions[1], suggestions[2], suggestions[3]]);
    provider = new GrReviewerSuggestionsProvider(
      getAppContext().restApiService,
      ReviewerState.CC,
      createServerInfo(),
      true,
      ...changes
    );

    // suggestions[0] is excluded because it is not returned for the second
    // change.
    // suggestions[3] is included because the first change has the suggestion
    // as a CC already.
    assert.sameDeepMembers(await provider.getSuggestions('s'), [
      suggestions[1],
      suggestions[2],
      suggestions[3],
    ]);
  });

  test('makeSuggestionItem formats account or group accordingly', () => {
    let suggestion = provider.makeSuggestionItem({
      account: accounts[0],
      count: 1,
    });
    assert.deepEqual(suggestion, {
      name: `${accounts[0].name} <${accounts[0].email}>`,
      value: {account: accounts[0], count: 1},
    });

    const group = {name: 'test' as GroupName, id: '5' as GroupId};
    suggestion = provider.makeSuggestionItem({group, count: 1});
    assert.deepEqual(suggestion, {
      name: `${group.name} (group)`,
      value: {group, count: 1},
    });

    suggestion = provider.makeSuggestionItem(accounts[0]);
    assert.deepEqual(suggestion, {
      name: `${accounts[0].name} <${accounts[0].email}>`,
      value: {account: accounts[0], count: 1},
    });

    suggestion = provider.makeSuggestionItem({account: {}, count: 1});
    assert.deepEqual(suggestion, {
      name: 'Name of user not set',
      value: {account: {}, count: 1},
    });

    provider = new GrReviewerSuggestionsProvider(
      getAppContext().restApiService,
      ReviewerState.REVIEWER,
      {
        ...createServerInfo(),
        user: {
          anonymous_coward_name: 'Anonymous Coward Name',
        },
      },
      true,
      ...changes
    );

    suggestion = provider.makeSuggestionItem({account: {}, count: 1});
    assert.deepEqual(suggestion, {
      name: 'Anonymous Coward Name',
      value: {account: {}, count: 1},
    });

    const oooAccount = {
      ...createAccountDetailWithIdNameAndEmail(3),
      status: 'OOO',
    };

    suggestion = provider.makeSuggestionItem({account: oooAccount, count: 1});
    assert.deepEqual(suggestion, {
      name: `${oooAccount.name} <${oooAccount.email}> (OOO)`,
      value: {account: oooAccount, count: 1},
    });

    suggestion = provider.makeSuggestionItem(oooAccount);
    assert.deepEqual(suggestion, {
      name: `${oooAccount.name} <${oooAccount.email}> (OOO)`,
      value: {account: oooAccount, count: 1},
    });

    const accountWithoutEmail = {
      ...createAccountDetailWithIdNameAndEmail(3),
      email: undefined,
    };

    suggestion = provider.makeSuggestionItem(accountWithoutEmail);
    assert.deepEqual(suggestion, {
      name: accountWithoutEmail.name,
      value: {account: accountWithoutEmail, count: 1},
    });
  });
});
