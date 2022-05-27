/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-repo-branch-picker';
import {GrRepoBranchPicker} from './gr-repo-branch-picker';
import {stubRestApi} from '../../../test/test-utils';
import {GitRef, ProjectInfoWithName, RepoName} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-repo-branch-picker');

suite('gr-repo-branch-picker tests', () => {
  let element: GrRepoBranchPicker;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  suite('getRepoSuggestions', () => {
    let getReposStub: sinon.SinonStub;
    setup(() => {
      getReposStub = stubRestApi('getRepos').returns(
        Promise.resolve([
          {
            id: 'plugins%2Favatars-external',
            name: 'plugins/avatars-external' as RepoName,
          },
          {
            id: 'plugins%2Favatars-gravatar',
            name: 'plugins/avatars-gravatar' as RepoName,
          },
          {
            id: 'plugins%2Favatars%2Fexternal',
            name: 'plugins/avatars/external',
          },
          {
            id: 'plugins%2Favatars%2Fgravatar',
            name: 'plugins/avatars/gravatar' as RepoName,
          },
        ] as ProjectInfoWithName[])
      );
    });

    test('converts to suggestion objects', async () => {
      const input = 'plugins/avatars';
      const suggestions = await element.getRepoSuggestions(input);
      assert.isTrue(getReposStub.calledWith(input));
      const unencodedNames = [
        'plugins/avatars-external',
        'plugins/avatars-gravatar',
        'plugins/avatars/external',
        'plugins/avatars/gravatar',
      ];
      assert.deepEqual(
        suggestions.map(s => s.name),
        unencodedNames
      );
      assert.deepEqual(
        suggestions.map(s => s.value),
        unencodedNames
      );
    });
  });

  suite('getRepoBranchesSuggestions', () => {
    let getRepoBranchesStub: sinon.SinonStub;
    setup(() => {
      getRepoBranchesStub = stubRestApi('getRepoBranches').returns(
        Promise.resolve([
          {ref: 'refs/heads/stable-2.10' as GitRef, revision: '123'},
          {ref: 'refs/heads/stable-2.11' as GitRef, revision: '1234'},
          {ref: 'refs/heads/stable-2.12' as GitRef, revision: '12345'},
          {ref: 'refs/heads/stable-2.13' as GitRef, revision: '123456'},
          {ref: 'refs/heads/stable-2.14' as GitRef, revision: '1234567'},
          {ref: 'refs/heads/stable-2.15' as GitRef, revision: '12345678'},
        ])
      );
    });

    test('converts to suggestion objects', async () => {
      const repo = 'gerrit';
      const branchInput = 'stable-2.1';
      element.repo = repo as RepoName;
      const suggestions = await element.getRepoBranchesSuggestions(branchInput);
      assert.isTrue(getRepoBranchesStub.calledWith(branchInput, repo, 15));
      const refNames = [
        'stable-2.10',
        'stable-2.11',
        'stable-2.12',
        'stable-2.13',
        'stable-2.14',
        'stable-2.15',
      ];
      assert.deepEqual(
        suggestions.map(s => s.name),
        refNames
      );
      assert.deepEqual(
        suggestions.map(s => s.value),
        refNames
      );
    });

    test('filters out ref prefix', async () => {
      const repo = 'gerrit' as RepoName;
      const branchInput = 'refs/heads/stable-2.1';
      element.repo = repo;
      return element.getRepoBranchesSuggestions(branchInput).then(() => {
        assert.isTrue(getRepoBranchesStub.calledWith('stable-2.1', repo, 15));
      });
    });

    test('does not query when repo is unset', async () => {
      await element.getRepoBranchesSuggestions('');
      assert.isFalse(getRepoBranchesStub.called);
      element.repo = 'gerrit' as RepoName;
      await element.getRepoBranchesSuggestions('');
      assert.isTrue(getRepoBranchesStub.called);
    });
  });
});
