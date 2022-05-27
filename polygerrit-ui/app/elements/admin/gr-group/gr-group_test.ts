/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-group';
import {GrGroup} from './gr-group';
import {
  addListenerForTest,
  mockPromise,
  queryAndAssert,
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {createGroupInfo} from '../../../test/test-data-generators.js';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrCopyClipboard} from '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {GrSelect} from '../../shared/gr-select/gr-select';

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

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
    groupStub = stubRestApi('getGroupConfig').returns(Promise.resolve(group));
  });

  test('loading displays before group config is loaded', () => {
    assert.isTrue(
      queryAndAssert<HTMLDivElement>(element, '#loading').classList.contains(
        'loading'
      )
    );
    assert.isFalse(
      getComputedStyle(queryAndAssert<HTMLDivElement>(element, '#loading'))
        .display === 'none'
    );
    assert.isTrue(
      queryAndAssert<HTMLDivElement>(
        element,
        '#loadedContent'
      ).classList.contains('loading')
    );
    assert.isTrue(
      getComputedStyle(
        queryAndAssert<HTMLDivElement>(element, '#loadedContent')
      ).display === 'none'
    );
  });

  test('default values are populated with internal group', async () => {
    stubRestApi('getIsGroupOwner').returns(Promise.resolve(true));
    element.groupId = '1' as GroupId;
    await element.loadGroup();
    assert.isTrue(element.groupIsInternal);
    // The value returned is a boolean in a string
    // thus we have to check with the string.
    assert.equal(
      queryAndAssert<GrSelect>(element, '#visibleToAll').bindValue,
      'false'
    );
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
    await element.loadGroup();
    assert.isFalse(element.groupIsInternal);
    // The value returned is a boolean in a string
    // thus we have to check with the string.
    assert.equal(
      queryAndAssert<GrSelect>(element, '#visibleToAll').bindValue,
      'false'
    );
  });

  test('rename group', async () => {
    const groupName = 'test-group';
    const groupName2 = 'test-group2';
    element.groupId = '1' as GroupId;
    element.groupConfig = {
      name: groupName as GroupName,
      id: '1' as GroupId,
    };
    element.originalName = groupName as GroupName;

    stubRestApi('getIsGroupOwner').returns(Promise.resolve(true));
    stubRestApi('saveGroupName').returns(
      Promise.resolve({...new Response(), status: 200})
    );

    const button = queryAndAssert<GrButton>(element, '#inputUpdateNameBtn');

    await element.loadGroup();
    assert.isTrue(button.hasAttribute('disabled'));
    assert.isFalse(
      queryAndAssert<HTMLHeadingElement>(element, '#Title').classList.contains(
        'edited'
      )
    );

    queryAndAssert<GrAutocomplete>(element, '#groupNameInput').text =
      groupName2;

    await waitUntil(() => button.hasAttribute('disabled') === false);

    assert.isTrue(
      queryAndAssert<HTMLHeadingElement>(
        element,
        '#groupName'
      ).classList.contains('edited')
    );

    await element.handleSaveName();
    assert.isTrue(button.disabled);
    assert.isFalse(
      queryAndAssert<HTMLHeadingElement>(element, '#Title').classList.contains(
        'edited'
      )
    );
    assert.equal(element.originalName, groupName2);
  });

  test('rename group owner', async () => {
    const groupName = 'test-group';
    element.groupId = '1' as GroupId;
    element.groupConfig = {
      name: groupName as GroupName,
      id: '1' as GroupId,
    };
    element.groupConfigOwner = 'testId';
    element.groupOwner = true;

    stubRestApi('getIsGroupOwner').returns(Promise.resolve(true));

    const button = queryAndAssert<GrButton>(element, '#inputUpdateOwnerBtn');

    await element.loadGroup();
    assert.isTrue(button.disabled);
    assert.isFalse(
      queryAndAssert<HTMLHeadingElement>(element, '#Title').classList.contains(
        'edited'
      )
    );

    queryAndAssert<GrAutocomplete>(element, '#groupOwnerInput').text =
      'testId2';

    await waitUntil(() => button.disabled === false);
    assert.isTrue(
      queryAndAssert<HTMLHeadingElement>(
        element,
        '#groupOwner'
      ).classList.contains('edited')
    );

    await element.handleSaveOwner();
    assert.isTrue(button.disabled);
    assert.isFalse(
      queryAndAssert<HTMLHeadingElement>(element, '#Title').classList.contains(
        'edited'
      )
    );
  });

  test('test for undefined group name', async () => {
    groupStub.restore();

    stubRestApi('getGroupConfig').returns(Promise.resolve(undefined));

    assert.isUndefined(element.groupId);

    element.groupId = '1' as GroupId;

    assert.isDefined(element.groupId);

    // Test that loading shows instead of filling
    // in group details
    await element.loadGroup();
    assert.isTrue(
      queryAndAssert<HTMLDivElement>(element, '#loading').classList.contains(
        'loading'
      )
    );

    assert.isTrue(element.loading);
  });

  test('test fire event', async () => {
    element.groupConfig = {
      name: 'test-group' as GroupName,
      id: '1' as GroupId,
    };
    element.groupId = 'gg' as GroupId;
    stubRestApi('saveGroupName').returns(
      Promise.resolve({...new Response(), status: 200})
    );

    const showStub = sinon.stub(element, 'dispatchEvent');
    await element.handleSaveName();
    assert.isTrue(showStub.called);
  });

  test('computeGroupDisabled', () => {
    element.isAdmin = true;
    element.groupOwner = false;
    element.groupIsInternal = true;
    assert.equal(element.computeGroupDisabled(), false);

    element.isAdmin = false;
    assert.equal(element.computeGroupDisabled(), true);

    element.groupOwner = true;
    assert.equal(element.computeGroupDisabled(), false);

    element.groupOwner = false;
    assert.equal(element.computeGroupDisabled(), true);

    element.groupIsInternal = false;
    assert.equal(element.computeGroupDisabled(), true);

    element.isAdmin = true;
    assert.equal(element.computeGroupDisabled(), true);
  });

  test('computeLoadingClass', () => {
    element.loading = true;
    assert.equal(element.computeLoadingClass(), 'loading');
    element.loading = false;
    assert.equal(element.computeLoadingClass(), '');
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

    await element.loadGroup();
    await promise;
  });

  test('uuid', async () => {
    element.groupConfig = {
      id: '6a1e70e1a88782771a91808c8af9bbb7a9871389' as GroupId,
    };

    await element.updateComplete;

    assert.equal(
      element.groupConfig.id,
      queryAndAssert<GrCopyClipboard>(element, '#uuid').text
    );

    element.groupConfig = {
      id: 'user%2Fgroup' as GroupId,
    };

    await element.updateComplete;

    assert.equal(
      'user/group',
      queryAndAssert<GrCopyClipboard>(element, '#uuid').text
    );
  });
});
