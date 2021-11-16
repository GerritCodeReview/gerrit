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
import './gr-create-group-dialog';
import {GrCreateGroupDialog} from './gr-create-group-dialog';
import {page} from '../../../utils/page-wrapper-utils';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {IronInputElement} from '@polymer/iron-input';
import {GroupId} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-create-group-dialog');

suite('gr-create-group-dialog tests', () => {
  let element: GrCreateGroupDialog;

  const GROUP_NAME = 'test-group';

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('name is updated correctly', async () => {
    const promise = mockPromise();
    element.addEventListener('has-new-group-name', () => {
      promise.resolve();
    });

    const inputEl = queryAndAssert<IronInputElement>(element, 'iron-input');
    inputEl.bindValue = GROUP_NAME;
    inputEl.dispatchEvent(new Event('input', {bubbles: true, composed: true}));

    await promise;

    assert.deepEqual(element.name, GROUP_NAME);
  });

  test('test for redirecting to group on successful creation', async () => {
    stubRestApi('createGroup').returns(
      Promise.resolve({status: 201} as Response)
    );
    stubRestApi('getGroupConfig').returns(
      Promise.resolve({id: 'testId551' as GroupId, group_id: 551})
    );

    const showStub = sinon.stub(page, 'show');
    await element.handleCreateGroup();
    assert.isTrue(showStub.calledWith('/admin/groups/551'));
  });

  test('test for unsuccessful group creation', async () => {
    stubRestApi('createGroup').returns(
      Promise.resolve({status: 409} as Response)
    );
    stubRestApi('getGroupConfig').returns(
      Promise.resolve({id: 'testId551' as GroupId, group_id: 551})
    );

    const showStub = sinon.stub(page, 'show');
    await element.handleCreateGroup();
    assert.isFalse(showStub.called);
  });
});
