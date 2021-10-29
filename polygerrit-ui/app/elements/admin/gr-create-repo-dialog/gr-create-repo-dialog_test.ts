/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import './gr-create-repo-dialog';
import {GrCreateRepoDialog} from './gr-create-repo-dialog';
import {stubRestApi} from '../../../test/test-utils';
import {BranchName, GroupId, RepoName} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-create-repo-dialog');

suite('gr-create-repo-dialog tests', () => {
  let element: GrCreateRepoDialog;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('default values are populated', () => {
    assert.isTrue(element.$.initialCommit.bindValue);
    assert.isFalse(element.$.parentRepo.bindValue);
  });

  test('repo created', async () => {
    const configInputObj = {
      name: 'test-repo' as RepoName,
      create_empty_commit: true,
      parent: 'All-Project' as RepoName,
      permissions_only: false,
    };

    const saveStub = stubRestApi('createRepo').returns(
      Promise.resolve(new Response())
    );

    assert.isFalse(element.hasNewRepoName);

    element._repoConfig = {
      name: 'test-repo' as RepoName,
      create_empty_commit: true,
      parent: 'All-Project' as RepoName,
      permissions_only: false,
    };

    element._repoOwner = 'test';
    element._repoOwnerId = 'testId' as GroupId;
    element._defaultBranch = 'main' as BranchName;

    element.$.repoNameInput.value = configInputObj.name;
    element.$.rightsInheritFromInput.value = configInputObj.parent;
    element.$.initialCommit.bindValue = configInputObj.create_empty_commit;
    element.$.parentRepo.bindValue = configInputObj.permissions_only;

    assert.isTrue(element.hasNewRepoName);

    assert.deepEqual(element._repoConfig, configInputObj);

    await element.handleCreateRepo();
    assert.isTrue(
      saveStub.lastCall.calledWithExactly({
        ...configInputObj,
        owners: ['testId' as GroupId],
        branches: ['main' as BranchName],
      })
    );
  });
});
