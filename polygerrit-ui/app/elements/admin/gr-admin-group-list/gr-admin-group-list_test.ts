/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
import {SHOWN_ITEMS_COUNT} from '../../../constants/constants';

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

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('computeGroupUrl', () => {
    let urlStub = sinon
      .stub(GerritNav, 'getUrlForGroup')
      .callsFake(
        () => '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5'
      );

    let group = 'e2cd66f88a2db4d391ac068a92d987effbe872f5';
    assert.equal(
      element.computeGroupUrl(group),
      '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5'
    );

    urlStub.restore();

    urlStub = sinon
      .stub(GerritNav, 'getUrlForGroup')
      .callsFake(() => '/admin/groups/user/test');

    group = 'user%2Ftest';
    assert.equal(element.computeGroupUrl(group), '/admin/groups/user/test');

    urlStub.restore();
  });

  suite('list with groups', () => {
    setup(async () => {
      groups = createGroupObjectList('test', 26);
      stubRestApi('getGroups').returns(Promise.resolve(groups));
      element.params = value;
      element.paramsChanged();
      await element.updateComplete;
    });

    test('test for test group in the list', () => {
      assert.equal(element.groups[1].name, 'test1' as GroupName);
      assert.equal(element.groups[1].options!.visible_to_all, false);
    });

    test('groups', () => {
      assert.equal(element.groups.slice(0, SHOWN_ITEMS_COUNT).length, 25);
    });

    test('maybeOpenCreateOverlay', () => {
      const overlayOpen = sinon.stub(
        queryAndAssert<GrOverlay>(element, '#createOverlay'),
        'open'
      );
      element.maybeOpenCreateOverlay();
      assert.isFalse(overlayOpen.called);
      element.maybeOpenCreateOverlay(undefined);
      assert.isFalse(overlayOpen.called);
      value.openCreateModal = true;
      element.maybeOpenCreateOverlay(value);
      assert.isTrue(overlayOpen.called);
    });
  });

  suite('test with 25 groups', () => {
    setup(async () => {
      groups = createGroupObjectList('test', 25);
      stubRestApi('getGroups').returns(Promise.resolve(groups));
      element.params = value;
      await element.paramsChanged();
      await element.updateComplete;
    });

    test('groups', () => {
      assert.equal(element.groups.slice(0, SHOWN_ITEMS_COUNT).length, 25);
    });
  });

  suite('filter', () => {
    test('paramsChanged', async () => {
      const getGroupsStub = stubRestApi('getGroups');
      getGroupsStub.returns(Promise.resolve(groups));
      value.filter = 'test';
      value.offset = 25;
      element.params = value;
      await element.paramsChanged();
      assert.isTrue(getGroupsStub.lastCall.calledWithExactly('test', 25, 25));
    });
  });

  suite('loading', async () => {
    test('correct contents are displayed', async () => {
      assert.isTrue(element.loading);
      assert.equal(
        getComputedStyle(queryAndAssert<HTMLTableElement>(element, '#loading'))
          .display,
        'block'
      );

      element.loading = false;
      element.groups = createGroupList('test', 25);

      await element.updateComplete;
      assert.equal(
        getComputedStyle(queryAndAssert<HTMLTableElement>(element, '#loading'))
          .display,
        'none'
      );
    });
  });

  suite('create new', () => {
    test('handleCreateClicked called when create-click fired', () => {
      const handleCreateClickedStub = sinon.stub(
        element,
        'handleCreateClicked'
      );
      queryAndAssert<GrListView>(element, 'gr-list-view').dispatchEvent(
        new CustomEvent('create-clicked', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCreateClickedStub.called);
    });

    test('handleCreateClicked opens modal', () => {
      const openStub = sinon
        .stub(queryAndAssert<GrOverlay>(element, '#createOverlay'), 'open')
        .returns(Promise.resolve());
      element.handleCreateClicked();
      assert.isTrue(openStub.called);
    });

    test('handleCreateGroup called when confirm fired', () => {
      const handleCreateGroupStub = sinon.stub(element, 'handleCreateGroup');
      queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
        new CustomEvent('confirm', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCreateGroupStub.called);
    });

    test('handleCloseCreate called when cancel fired', () => {
      const handleCloseCreateStub = sinon.stub(element, 'handleCloseCreate');
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
