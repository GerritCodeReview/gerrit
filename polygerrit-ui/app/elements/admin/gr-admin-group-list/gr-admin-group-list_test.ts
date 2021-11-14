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
import './gr-admin-group-list';
import {GrAdminGroupList} from './gr-admin-group-list';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {
  GroupId,
  GroupName,
  GroupNameToGroupInfoMap,
} from '../../../types/common';
import {AppElementAdminParams} from '../../gr-app-types';
import {GerritView} from '../../../services/router/router-model';
import {GrListView} from '../../shared/gr-list-view/gr-list-view';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';

const basicFixture = fixtureFromElement('gr-admin-group-list');

function createGroup(name: string, counter: number) {
  return {
    name: `${name}${counter}` as GroupName,
    id: '59b92f35489e62c80d1ab1bf0c2d17843038df8b' as GroupId,
    url: '#/admin/groups/uuid-59b92f35489e62c80d1ab1bf0c2d17843038df8b',
    options: {
      visible_to_all: false,
    },
    description: 'Gerrit Site Administrators',
    group_id: 1,
    owner: 'Administrators',
    owner_id: '7ca042f4d5847936fcb90ca91057673157fd06fc',
  };
}

function createGroupList(name: string, n: number) {
  const groups = [];
  for (let i = 0; i < n; ++i) {
    groups.push(createGroup(name, i));
  }
  return groups;
}

function createGroupObjectList(name: string, n: number) {
  const groups: GroupNameToGroupInfoMap = {};
  for (let i = 0; i < n; ++i) {
    groups[`${name}${i}`] = createGroup(name, i);
  }
  return groups;
}

suite('gr-admin-group-list tests', () => {
  let element: GrAdminGroupList;
  let groups: GroupNameToGroupInfoMap;

  const value: AppElementAdminParams = {view: GerritView.ADMIN, adminView: ''};

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('_computeGroupUrl', () => {
    let urlStub = sinon
      .stub(GerritNav, 'getUrlForGroup')
      .callsFake(
        () => '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5'
      );

    let group = 'e2cd66f88a2db4d391ac068a92d987effbe872f5';
    assert.equal(
      element._computeGroupUrl(group),
      '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5'
    );

    urlStub.restore();

    urlStub = sinon
      .stub(GerritNav, 'getUrlForGroup')
      .callsFake(() => '/admin/groups/user/test');

    group = 'user%2Ftest';
    assert.equal(element._computeGroupUrl(group), '/admin/groups/user/test');

    urlStub.restore();
  });

  suite('list with groups', () => {
    setup(async () => {
      groups = createGroupObjectList('test', 26);
      stubRestApi('getGroups').returns(Promise.resolve(groups));
      element._paramsChanged(value);
      await flush();
    });

    test('test for test group in the list', () => {
      assert.equal(element._groups[1].name, 'test1' as GroupName);
      assert.equal(element._groups[1].options!.visible_to_all, false);
    });

    test('_shownGroups', () => {
      assert.equal(element._shownGroups.length, 25);
    });

    test('_maybeOpenCreateOverlay', () => {
      const overlayOpen = sinon.stub(
        queryAndAssert<GrOverlay>(element, '#createOverlay'),
        'open'
      );
      element._maybeOpenCreateOverlay();
      assert.isFalse(overlayOpen.called);
      element._maybeOpenCreateOverlay(undefined);
      assert.isFalse(overlayOpen.called);
      value.openCreateModal = true;
      element._maybeOpenCreateOverlay(value);
      assert.isTrue(overlayOpen.called);
    });
  });

  suite('test with less then 25 groups', () => {
    setup(async () => {
      groups = createGroupObjectList('test', 25);
      stubRestApi('getGroups').returns(Promise.resolve(groups));
      await element._paramsChanged(value);
      await flush();
    });

    test('_shownGroups', () => {
      assert.equal(element._shownGroups.length, 25);
    });
  });

  suite('filter', () => {
    test('_paramsChanged', async () => {
      const getGroupsStub = stubRestApi('getGroups');
      getGroupsStub.returns(Promise.resolve(groups));
      value.filter = 'test';
      value.offset = 25;
      await element._paramsChanged(value);
      assert.isTrue(getGroupsStub.lastCall.calledWithExactly('test', 25, 25));
    });
  });

  suite('loading', async () => {
    test('correct contents are displayed', async () => {
      assert.isTrue(element._loading);
      assert.equal(element.computeLoadingClass(element._loading), 'loading');
      assert.equal(
        getComputedStyle(queryAndAssert<HTMLTableElement>(element, '#loading'))
          .display,
        'block'
      );

      element._loading = false;
      element._groups = createGroupList('test', 25);

      await flush();
      assert.equal(element.computeLoadingClass(element._loading), '');
      assert.equal(
        getComputedStyle(queryAndAssert<HTMLTableElement>(element, '#loading'))
          .display,
        'none'
      );
    });
  });

  suite('create new', () => {
    test('_handleCreateClicked called when create-click fired', () => {
      const handleCreateClickedStub = sinon.stub(
        element,
        '_handleCreateClicked'
      );
      queryAndAssert<GrListView>(element, 'gr-list-view').dispatchEvent(
        new CustomEvent('create-clicked', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCreateClickedStub.called);
    });

    test('_handleCreateClicked opens modal', () => {
      const openStub = sinon
        .stub(queryAndAssert<GrOverlay>(element, '#createOverlay'), 'open')
        .returns(Promise.resolve());
      element._handleCreateClicked();
      assert.isTrue(openStub.called);
    });

    test('_handleCreateGroup called when confirm fired', () => {
      const handleCreateGroupStub = sinon.stub(element, '_handleCreateGroup');
      queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
        new CustomEvent('confirm', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCreateGroupStub.called);
    });

    test('_handleCloseCreate called when cancel fired', () => {
      const handleCloseCreateStub = sinon.stub(element, '_handleCloseCreate');
      queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
        new CustomEvent('cancel', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCloseCreateStub.called);
    });
  });
});
