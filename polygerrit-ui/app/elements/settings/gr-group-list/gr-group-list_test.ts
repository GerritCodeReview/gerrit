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
import {stubRestApi} from '../../../test/test-utils';

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

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="gr-form-styles">
        <table id="groups">
          <thead>
            <tr>
              <th class="nameHeader">Name</th>
              <th class="descriptionHeader">Description</th>
              <th class="visibleCell">Visible to all</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td class="nameColumn">
                <a href=""> Group 1 </a>
              </td>
              <td>Group 1 description</td>
              <td class="visibleCell">No</td>
            </tr>
            <tr>
              <td class="nameColumn">
                <a href=""> Group 2 </a>
              </td>
              <td></td>
              <td class="visibleCell">Yes</td>
            </tr>
            <tr>
              <td class="nameColumn">
                <a href=""> Group 3 </a>
              </td>
              <td></td>
              <td class="visibleCell">No</td>
            </tr>
          </tbody>
        </table>
      </div>
    `);
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
