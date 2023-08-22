/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
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
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-create-repo-dialog tests', () => {
  let element: GrCreateRepoDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-create-repo-dialog></gr-create-repo-dialog>`
    );
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles">
          <div id="form">
            <section>
              <span class="title"> Repository name </span>
              <iron-input>
                <input autocomplete="on" id="repoNameInput" />
              </iron-input>
            </section>
            <section>
              <span class="title"> Default Branch </span>
              <span class="value">
                <gr-autocomplete id="defaultBranchNameInput"> </gr-autocomplete>
              </span>
            </section>
            <section>
              <span class="title"> Rights inherit from </span>
              <span class="value">
                <gr-autocomplete id="rightsInheritFromInput"> </gr-autocomplete>
              </span>
            </section>
            <section>
              <span class="title"> Owner </span>
              <span class="value">
                <gr-autocomplete id="ownerInput"> </gr-autocomplete>
              </span>
            </section>
            <section>
              <span class="title"> Create initial empty commit </span>
              <span class="value">
                <gr-select id="initialCommit">
                  <select>
                    <option value="false">False</option>
                    <option value="true">True</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <span class="title">
                Only serve as parent for other repositories
              </span>
              <span class="value">
                <gr-select id="parentRepo">
                  <select>
                    <option value="false">False</option>
                    <option value="true">True</option>
                  </select>
                </gr-select>
              </span>
            </section>
          </div>
        </div>
      `
    );
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
