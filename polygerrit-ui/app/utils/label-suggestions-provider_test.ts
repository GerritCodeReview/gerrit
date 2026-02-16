/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {LabelSuggestionsProvider} from './label-suggestions-provider';
import {RepoName} from '../types/common';
import {SinonStub} from 'sinon';
import {
  LabelDefinitionInfo,
  LabelDefinitionInfoFunction,
} from '../api/rest-api';
import {stubReporting, stubRestApi} from '../test/test-utils';
import {getAppContext} from '../services/app-context';

suite('LabelSuggestionsProvider tests', () => {
  let provider: LabelSuggestionsProvider;
  let getRepoLabelsStub: SinonStub;

  const VOTE_LABELS: LabelDefinitionInfo[] = [
    {
      name: 'Code-Review',
      project_name: 'test-project',
      function: LabelDefinitionInfoFunction.MaxWithBlock,
      values: {'-1': 'Bad', ' 0': 'No score', '+1': 'Good'},
      default_value: 0,
    },
    {
      name: 'Verified',
      project_name: 'test-project',
      function: LabelDefinitionInfoFunction.MaxWithBlock,
      values: {'-1': 'Fails', ' 0': 'No score', '+1': 'Verified'},
      default_value: 0,
    },
  ];

  setup(() => {
    stubReporting('error');
    getRepoLabelsStub = stubRestApi('getRepoLabels').returns(
      Promise.resolve(VOTE_LABELS)
    );
    provider = new LabelSuggestionsProvider(getAppContext().restApiService);
  });

  test('getSuggestions returns empty array when repoName is not set', async () => {
    const suggestions = await provider.getSuggestions('label', 'Code');
    assert.isEmpty(suggestions);
  });

  test('getSuggestions calls getRepoLabels with the correct repoName', async () => {
    const repoName = 'test-repo' as RepoName;
    provider.setRepoName(repoName);
    await provider.getSuggestions('label', 'Code');
    assert.isTrue(getRepoLabelsStub.calledWith(repoName));
  });

  test('getSuggestions filters labels based on expression', async () => {
    const repoName = 'test-repo' as RepoName;
    provider.setRepoName(repoName);
    const suggestions = await provider.getSuggestions('label', 'Code');
    assert.deepEqual(suggestions, [{text: 'label:Code-Review'}]);
  });

  test('getSuggestions is case-insensitive', async () => {
    const repoName = 'test-repo' as RepoName;
    provider.setRepoName(repoName);
    const suggestions = await provider.getSuggestions('label', 'code');
    assert.deepEqual(suggestions, [{text: 'label:Code-Review'}]);
  });

  test('getSuggestions returns all labels when expression is empty', async () => {
    const repoName = 'test-repo' as RepoName;
    provider.setRepoName(repoName);
    const suggestions = await provider.getSuggestions('label', '');
    assert.deepEqual(suggestions, [
      {text: 'label:Code-Review'},
      {text: 'label:Verified'},
    ]);
  });

  test('getSuggestions handles API errors gracefully', async () => {
    getRepoLabelsStub.returns(Promise.reject(new Error('API error')));
    const repoName = 'test-repo' as RepoName;
    provider.setRepoName(repoName);
    const suggestions = await provider.getSuggestions('label', 'Code');
    assert.isEmpty(suggestions);
  });
});
