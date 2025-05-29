/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-pointer-dialog';
import {GrCreatePointerDialog} from './gr-create-pointer-dialog';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {BranchName} from '../../../types/common';
import {IronInputElement} from '@polymer/iron-input';
import {assert, fixture, html} from '@open-wc/testing';
import {RepoDetailView} from '../../../models/views/repo';

suite('gr-create-pointer-dialog tests', () => {
  let element: GrCreatePointerDialog;

  const ironInput = (element: Element) =>
    queryAndAssert<IronInputElement>(element, 'iron-input');

  setup(async () => {
    element = await fixture(
      html`<gr-create-pointer-dialog></gr-create-pointer-dialog>`
    );
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles">
          <div id="form">
            <section id="itemNameSection">
              <span class="title"> name </span>
              <iron-input>
                <input placeholder=" Name" />
              </iron-input>
            </section>
            <section id="createEmptyCommitSection">
              <div>
                <span class="title">Point to</span>
              </div>
              <div>
                <span class="value">
                  <gr-select id="initialCommit">
                    <select>
                      <option value="false">Existing Revision</option>
                      <option value="true">Initial empty commit</option>
                    </select>
                  </gr-select>
                </span>
              </div>
            </section>
            <section id="itemRevisionSection">
              <span class="title"> Initial Revision </span>
              <iron-input>
                <input placeholder="Revision (Branch or SHA-1)" />
              </iron-input>
            </section>
            <section id="itemAnnotationSection">
              <span class="title"> Annotation </span>
              <iron-input>
                <input placeholder="Annotation (Optional)" />
              </iron-input>
            </section>
          </div>
        </div>
      `
    );
  });

  test('branch created', async () => {
    stubRestApi('createRepoBranch').returns(Promise.resolve(new Response()));

    const promise = mockPromise();
    element.addEventListener('update-item-name', () => {
      promise.resolve();
    });

    element.itemName = 'test-branch' as BranchName;
    element.itemDetail = 'branches' as RepoDetailView.BRANCHES;

    ironInput(queryAndAssert(element, '#itemNameSection')).bindValue =
      'test-branch2';
    ironInput(queryAndAssert(element, '#itemRevisionSection')).bindValue =
      'HEAD';

    await promise;

    assert.equal(element.itemName, 'test-branch2' as BranchName);
    assert.equal(element.itemRevision, 'HEAD');
  });

  test('tag created', async () => {
    stubRestApi('createRepoTag').returns(Promise.resolve(new Response()));

    const promise = mockPromise();
    element.addEventListener('update-item-name', () => {
      promise.resolve();
    });

    element.itemName = 'test-tag' as BranchName;
    element.itemDetail = 'tags' as RepoDetailView.TAGS;

    ironInput(queryAndAssert(element, '#itemNameSection')).bindValue =
      'test-tag2';
    ironInput(queryAndAssert(element, '#itemRevisionSection')).bindValue =
      'HEAD';

    await promise;

    assert.equal(element.itemName, 'test-tag2' as BranchName);
    assert.equal(element.itemRevision, 'HEAD');
  });

  test('tag created with annotations', async () => {
    stubRestApi('createRepoTag').returns(Promise.resolve(new Response()));

    const promise = mockPromise();
    element.addEventListener('update-item-name', () => {
      promise.resolve();
    });

    element.itemName = 'test-tag' as BranchName;
    element.itemAnnotation = 'test-message';
    element.itemDetail = 'tags' as RepoDetailView.TAGS;

    ironInput(queryAndAssert(element, '#itemNameSection')).bindValue =
      'test-tag2';
    ironInput(queryAndAssert(element, '#itemAnnotationSection')).bindValue =
      'test-message2';
    ironInput(queryAndAssert(element, '#itemRevisionSection')).bindValue =
      'HEAD';

    await promise;

    assert.equal(element.itemName, 'test-tag2' as BranchName);
    assert.equal(element.itemAnnotation, 'test-message2');
    assert.equal(element.itemRevision, 'HEAD');
  });
});
