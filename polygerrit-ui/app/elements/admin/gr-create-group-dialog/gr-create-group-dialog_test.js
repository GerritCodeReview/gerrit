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

import '../../../test/common-test-setup-karma.js';
import './gr-create-group-dialog.js';
import {page} from '../../../utils/page-wrapper-utils.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-create-group-dialog');

suite('gr-create-group-dialog tests', () => {
  let element;

  const GROUP_NAME = 'test-group';

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('name is updated correctly', async () => {
    assert.isFalse(element.hasNewGroupName);

    const inputEl = element.root.querySelector('iron-input');
    inputEl.bindValue = GROUP_NAME;

    await new Promise(resolve => setTimeout(resolve));
    assert.isTrue(element.hasNewGroupName);
    assert.deepEqual(element._name, GROUP_NAME);
  });

  test('test for redirecting to group on successful creation', async () => {
    stubRestApi('createGroup').returns(Promise.resolve({status: 201}));
    stubRestApi('getGroupConfig').returns(Promise.resolve({group_id: 551}));

    const showStub = sinon.stub(page, 'show');
    await element.handleCreateGroup();
    assert.isTrue(showStub.calledWith('/admin/groups/551'));
  });

  test('test for unsuccessful group creation', async () => {
    stubRestApi('createGroup').returns(Promise.resolve({status: 409}));
    stubRestApi('getGroupConfig').returns(Promise.resolve({group_id: 551}));

    const showStub = sinon.stub(page, 'show');
    await element.handleCreateGroup();
    assert.isFalse(showStub.called);
  });
});

