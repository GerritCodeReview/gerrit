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
import './gr-create-pointer-dialog';
import {GrCreatePointerDialog} from './gr-create-pointer-dialog';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {BranchName} from '../../../types/common';
import {RepoDetailView} from '../../core/gr-navigation/gr-navigation';
import {IronInputElement} from '@polymer/iron-input';

const basicFixture = fixtureFromElement('gr-create-pointer-dialog');

suite('gr-create-pointer-dialog tests', () => {
  let element: GrCreatePointerDialog;

  const ironInput = (element: Element) =>
    queryAndAssert<IronInputElement>(element, 'iron-input');

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('branch created', async () => {
    stubRestApi('createRepoBranch').returns(Promise.resolve(new Response()));

    assert.isFalse(element.hasNewItemName);

    element._itemName = 'test-branch' as BranchName;
    element.itemDetail = 'branches' as RepoDetailView.BRANCHES;

    ironInput(element.$.itemNameSection).bindValue = 'test-branch2';
    ironInput(element.$.itemRevisionSection).bindValue = 'HEAD';

    await flush();

    assert.isTrue(element.hasNewItemName);
    assert.equal(element._itemName, 'test-branch2' as BranchName);
    assert.equal(element._itemRevision, 'HEAD');
  });

  test('tag created', async () => {
    stubRestApi('createRepoTag').returns(Promise.resolve(new Response()));

    assert.isFalse(element.hasNewItemName);

    element._itemName = 'test-tag' as BranchName;
    element.itemDetail = 'tags' as RepoDetailView.TAGS;

    ironInput(element.$.itemNameSection).bindValue = 'test-tag2';
    ironInput(element.$.itemRevisionSection).bindValue = 'HEAD';

    await flush();
    assert.isTrue(element.hasNewItemName);
    assert.equal(element._itemName, 'test-tag2' as BranchName);
    assert.equal(element._itemRevision, 'HEAD');
  });

  test('tag created with annotations', async () => {
    stubRestApi('createRepoTag').returns(Promise.resolve(new Response()));

    assert.isFalse(element.hasNewItemName);

    element._itemName = 'test-tag' as BranchName;
    element._itemAnnotation = 'test-message';
    element.itemDetail = 'tags' as RepoDetailView.TAGS;

    ironInput(element.$.itemNameSection).bindValue = 'test-tag2';
    ironInput(element.$.itemAnnotationSection).bindValue = 'test-message2';
    ironInput(element.$.itemRevisionSection).bindValue = 'HEAD';

    await flush();
    assert.isTrue(element.hasNewItemName);
    assert.equal(element._itemName, 'test-tag2' as BranchName);
    assert.equal(element._itemAnnotation, 'test-message2');
    assert.equal(element._itemRevision, 'HEAD');
  });

  test('_computeHideItemClass returns hideItem if type is branches', () => {
    assert.equal(
      element._computeHideItemClass(RepoDetailView.BRANCHES),
      'hideItem'
    );
  });

  test('_computeHideItemClass returns strings if not branches', () => {
    assert.equal(element._computeHideItemClass(RepoDetailView.TAGS), '');
  });
});
