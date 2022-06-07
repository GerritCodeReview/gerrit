/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-group-list';
import {GrGroupList} from './gr-group-list';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';
import {queryAll, stubRestApi} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-group-list');

suite('gr-group-list tests', () => {
  let element: GrGroupList;
  let groups: GroupInfo[];

  setup(async () => {
    groups = [
      {
        url: 'some url',
        options: {
          visible_to_all: false,
        },
        description: 'Group 1 description',
        group_id: 1,
        owner: 'Administrators',
        owner_id: '123',
        id: 'abc' as GroupId,
        name: 'Group 1' as GroupName,
      },
      {
        options: {visible_to_all: true},
        id: '456' as GroupId,
        name: 'Group 2' as GroupName,
      },
      {
        options: {visible_to_all: false},
        id: '789' as GroupId,
        name: 'Group 3' as GroupName,
      },
    ];

    stubRestApi('getAccountGroups').returns(Promise.resolve(groups));

    element = basicFixture.instantiate();

    await element.loadData();
    await flush();
  });

  test('renders', async () => {
    await flush();

    const rows = Array.from(queryAll(element, 'tbody tr'));

    assert.equal(rows.length, 3);

    const nameCells = rows.map(row =>
      queryAll(row, 'td a')[0].textContent!.trim()
    );

    assert.equal(nameCells[0], 'Group 1');
    assert.equal(nameCells[1], 'Group 2');
    assert.equal(nameCells[2], 'Group 3');
  });

  test('_computeGroupPath', () => {
    let urlStub = sinon
      .stub(GerritNav, 'getUrlForGroup')
      .callsFake(
        () => '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5'
      );

    let group = {
      id: 'e2cd66f88a2db4d391ac068a92d987effbe872f5' as GroupId,
    };
    assert.equal(
      element._computeGroupPath(group),
      '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5'
    );

    urlStub.restore();

    urlStub = sinon
      .stub(GerritNav, 'getUrlForGroup')
      .callsFake(() => '/admin/groups/user/test');

    group = {
      id: 'user%2Ftest' as GroupId,
    };
    assert.equal(element._computeGroupPath(group), '/admin/groups/user/test');
  });
});
