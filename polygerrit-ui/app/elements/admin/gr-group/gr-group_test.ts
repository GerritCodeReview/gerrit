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
import './gr-group';
import {GrGroup} from './gr-group';
import {
  addListenerForTest,
  mockPromise,
  stubRestApi,
} from '../../../test/test-utils';
import {createGroupInfo} from '../../../test/test-data-generators.js';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-group');

suite('gr-group tests', () => {
  let element: GrGroup;
  let groupStub: sinon.SinonStub;

  const group: GroupInfo = {
    ...createGroupInfo('6a1e70e1a88782771a91808c8af9bbb7a9871389'),
    url: '#/admin/groups/uuid-6a1e70e1a88782771a91808c8af9bbb7a9871389',
    options: {
      visible_to_all: false,
    },
    description: 'Gerrit Site Administrators',
    group_id: 1,
    owner: 'Administrators',
    owner_id: '6a1e70e1a88782771a91808c8af9bbb7a9871389',
    name: 'Administrators' as GroupName,
  };

  setup(() => {
    element = basicFixture.instantiate();
    groupStub = stubRestApi('getGroupConfig').returns(Promise.resolve(group));
  });

  test('loading displays before group config is loaded', () => {
    assert.isTrue(element.$.loading.classList.contains('loading'));
    assert.isFalse(getComputedStyle(element.$.loading).display === 'none');
    assert.isTrue(element.$.loadedContent.classList.contains('loading'));
    assert.isTrue(getComputedStyle(element.$.loadedContent).display === 'none');
  });

  test('default values are populated with internal group', async () => {
    stubRestApi('getIsGroupOwner').returns(Promise.resolve(true));
    element.groupId = '1' as GroupId;
    await element._loadGroup();
    assert.isTrue(element._groupIsInternal);
    assert.isFalse(element.$.visibleToAll.bindValue); //
  });

  test('default values with external group', async () => {
    const groupExternal = {...group};
    groupExternal.id = 'external-group-id' as GroupId;
    groupStub.restore();
    groupStub = stubRestApi('getGroupConfig').returns(
      Promise.resolve(groupExternal)
    );
    stubRestApi('getIsGroupOwner').returns(Promise.resolve(true));
    element.groupId = '1' as GroupId;
    await element._loadGroup();
    assert.isFalse(element._groupIsInternal);
    assert.isFalse(element.$.visibleToAll.bindValue);
  });

  test('rename group', async () => {
    const groupName = 'test-group';
    const groupName2 = 'test-group2';
    element.groupId = '1' as GroupId;
    element._groupConfig = {
      name: groupName as GroupName,
      id: '1' as GroupId,
    };
    element._groupName = groupName as GroupName;

    stubRestApi('getIsGroupOwner').returns(Promise.resolve(true));
    stubRestApi('saveGroupName').returns(
      Promise.resolve({...new Response(), status: 200})
    );

    const button = element.$.inputUpdateNameBtn;

    await element._loadGroup();
    assert.isTrue(button.hasAttribute('disabled'));
    assert.isFalse(element.$.Title.classList.contains('edited'));

    element.$.groupNameInput.text = groupName2;

    await flush();
    assert.isFalse(button.hasAttribute('disabled'));
    assert.isTrue(element.$.groupName.classList.contains('edited'));

    await element._handleSaveName();
    assert.isTrue(button.hasAttribute('disabled'));
    assert.isFalse(element.$.Title.classList.contains('edited'));
    assert.equal(element._groupName, groupName2);
  });

  test('rename group owner', async () => {
    const groupName = 'test-group';
    element.groupId = '1' as GroupId;
    element._groupConfig = {
      name: groupName as GroupName,
      id: '1' as GroupId,
    };
    element._groupConfigOwner = 'testId';
    element._groupOwner = true;

    stubRestApi('getIsGroupOwner').returns(Promise.resolve(true));

    const button = element.$.inputUpdateOwnerBtn;

    await element._loadGroup();
    assert.isTrue(button.hasAttribute('disabled'));
    assert.isFalse(element.$.Title.classList.contains('edited'));

    element.$.groupOwnerInput.text = 'testId2';

    await flush();
    assert.isFalse(button.hasAttribute('disabled'));
    assert.isTrue(element.$.groupOwner.classList.contains('edited'));

    await element._handleSaveOwner();
    assert.isTrue(button.hasAttribute('disabled'));
    assert.isFalse(element.$.Title.classList.contains('edited'));
  });

  test('test for undefined group name', async () => {
    groupStub.restore();

    stubRestApi('getGroupConfig').returns(Promise.resolve(undefined));

    assert.isUndefined(element.groupId);

    element.groupId = '1' as GroupId;

    assert.isDefined(element.groupId);

    // Test that loading shows instead of filling
    // in group details
    await element._loadGroup();
    assert.isTrue(element.$.loading.classList.contains('loading'));

    assert.isTrue(element._loading);
  });

  test('test fire event', async () => {
    element._groupConfig = {
      name: 'test-group' as GroupName,
      id: '1' as GroupId,
    };
    element.groupId = 'gg' as GroupId;
    stubRestApi('saveGroupName').returns(
      Promise.resolve({...new Response(), status: 200})
    );

    const showStub = sinon.stub(element, 'dispatchEvent');
    await element._handleSaveName();
    assert.isTrue(showStub.called);
  });

  test('_computeGroupDisabled', () => {
    let admin = true;
    let owner = false;
    let groupIsInternal = true;
    assert.equal(
      element._computeGroupDisabled(owner, admin, groupIsInternal),
      false
    );

    admin = false;
    assert.equal(
      element._computeGroupDisabled(owner, admin, groupIsInternal),
      true
    );

    owner = true;
    assert.equal(
      element._computeGroupDisabled(owner, admin, groupIsInternal),
      false
    );

    owner = false;
    assert.equal(
      element._computeGroupDisabled(owner, admin, groupIsInternal),
      true
    );

    groupIsInternal = false;
    assert.equal(
      element._computeGroupDisabled(owner, admin, groupIsInternal),
      true
    );

    admin = true;
    assert.equal(
      element._computeGroupDisabled(owner, admin, groupIsInternal),
      true
    );
  });

  test('_computeLoadingClass', () => {
    assert.equal(element._computeLoadingClass(true), 'loading');
    assert.equal(element._computeLoadingClass(false), '');
  });

  test('fires page-error', async () => {
    groupStub.restore();

    element.groupId = '1' as GroupId;

    const response = {...new Response(), status: 404};
    stubRestApi('getGroupConfig').callsFake((_, errFn) => {
      if (errFn !== undefined) {
        errFn(response);
      } else {
        assert.fail('errFn is undefined');
      }
      return Promise.resolve(undefined);
    });

    const promise = mockPromise();
    addListenerForTest(document, 'page-error', e => {
      assert.deepEqual((e as CustomEvent).detail.response, response);
      promise.resolve();
    });

    element._loadGroup();
    await promise;
  });

  test('uuid', () => {
    element._groupConfig = {
      id: '6a1e70e1a88782771a91808c8af9bbb7a9871389' as GroupId,
    };

    assert.equal(element._groupConfig.id, element.$.uuid.text);

    element._groupConfig = {
      id: 'user%2Fgroup' as GroupId,
    };

    assert.equal('user/group', element.$.uuid.text);
  });
});
