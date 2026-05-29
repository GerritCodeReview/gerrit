/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-group-list';
import {GrGroupList} from './gr-group-list';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';
import {stubRestApi, waitEventLoop} from '../../../test/test-utils';
import {assert, fixture, html} from '@open-wc/testing';

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

    element = await fixture(html`<gr-group-list></gr-group-list>`);

    await element.loadData();
    await waitEventLoop();
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
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
                  <a href="/admin/groups/abc"> Group 1 </a>
                </td>
                <td>Group 1 description</td>
                <td class="visibleCell">No</td>
              </tr>
              <tr>
                <td class="nameColumn">
                  <a href="/admin/groups/456"> Group 2 </a>
                </td>
                <td></td>
                <td class="visibleCell">Yes</td>
              </tr>
              <tr>
                <td class="nameColumn">
                  <a href="/admin/groups/789"> Group 3 </a>
                </td>
                <td></td>
                <td class="visibleCell">No</td>
              </tr>
            </tbody>
          </table>
        </div>
      `
    );
  });
});
