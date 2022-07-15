/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-create-pointer-dialog';
import {GrCreatePointerDialog} from './gr-create-pointer-dialog';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {BranchName} from '../../../types/common';
import {RepoDetailView} from '../../core/gr-navigation/gr-navigation';
import {IronInputElement} from '@polymer/iron-input';

const basicFixture = fixtureFromElement('gr-create-pointer-dialog');

suite('gr-create-pointer-dialog tests', () => {
  let element: GrCreatePointerDialog;

  const ironInput = (element: Element) =>
    queryAndAssert<IronInputElement>(element, 'iron-input');

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('render', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="gr-form-styles">
        <div id="form">
          <section id="itemNameSection">
            <span class="title"> name </span>
            <iron-input>
              <input placeholder=" Name" />
            </iron-input>
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
    `);
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
