/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-create-repo-dialog';
import {GrCreateRepoDialog} from './gr-create-repo-dialog';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {BranchName, GroupId, RepoName} from '../../../types/common';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrSelect} from '../../shared/gr-select/gr-select';

const basicFixture = fixtureFromElement('gr-create-repo-dialog');

suite('gr-create-repo-dialog tests', () => {
  let element: GrCreateRepoDialog;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('default values are populated', () => {
    assert.isTrue(
      queryAndAssert<GrSelect>(element, '#initialCommit').bindValue
    );
    assert.isFalse(queryAndAssert<GrSelect>(element, '#parentRepo').bindValue);
  });

  test('repo created', async () => {
    const configInputObj = {
      name: 'test-repo-new' as RepoName,
      create_empty_commit: true,
      parent: 'All-Project' as RepoName,
      permissions_only: false,
    };

    const saveStub = stubRestApi('createRepo').returns(
      Promise.resolve(new Response())
    );

    const promise = mockPromise();
    element.addEventListener('new-repo-name', () => {
      promise.resolve();
    });

    element.repoConfig = {
      name: 'test-repo' as RepoName,
      create_empty_commit: true,
      parent: 'All-Project' as RepoName,
      permissions_only: false,
    };

    element.repoOwner = 'test';
    element.repoOwnerId = 'testId' as GroupId;
    element.defaultBranch = 'main' as BranchName;

    const repoNameInput = queryAndAssert<HTMLInputElement>(
      element,
      '#repoNameInput'
    );
    repoNameInput.value = configInputObj.name;
    repoNameInput.dispatchEvent(
      new Event('input', {bubbles: true, composed: true})
    );
    queryAndAssert<GrAutocomplete>(element, '#rightsInheritFromInput').value =
      configInputObj.parent;
    queryAndAssert<GrSelect>(element, '#initialCommit').bindValue =
      configInputObj.create_empty_commit;
    queryAndAssert<GrSelect>(element, '#parentRepo').bindValue =
      configInputObj.permissions_only;

    assert.deepEqual(element.repoConfig, configInputObj);

    await element.handleCreateRepo();
    assert.isTrue(
      saveStub.lastCall.calledWithExactly({
        ...configInputObj,
        owners: ['testId' as GroupId],
        branches: ['main' as BranchName],
      })
    );

    await promise;

    assert.equal(element.repoConfig.name, configInputObj.name);
    assert.equal(element.nameChanged, true);
  });
});
