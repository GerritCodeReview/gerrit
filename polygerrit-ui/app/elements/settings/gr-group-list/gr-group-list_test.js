/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import './gr-group-list.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';

const basicFixture = fixtureFromElement('gr-group-list');

suite('gr-group-list tests', () => {
  let element;
  let groups;

  setup(done => {
    groups = [{
      url: 'some url',
      options: {},
      description: 'Group 1 description',
      group_id: 1,
      owner: 'Administrators',
      owner_id: '123',
      id: 'abc',
      name: 'Group 1',
    }, {
      options: {visible_to_all: true},
      id: '456',
      name: 'Group 2',
    }, {
      options: {},
      id: '789',
      name: 'Group 3',
    }];

    stub('gr-rest-api-interface', {
      getAccountGroups() { return Promise.resolve(groups); },
    });

    element = basicFixture.instantiate();

    element.loadData().then(() => { flush(done); });
  });

  test('renders', () => {
    const rows = Array.from(
        element.root.querySelectorAll('tbody tr'));

    assert.equal(rows.length, 3);

    const nameCells = rows.map(row => { return row.querySelectorAll('td a')[0].textContent.trim(); }
    );

    assert.equal(nameCells[0], 'Group 1');
    assert.equal(nameCells[1], 'Group 2');
    assert.equal(nameCells[2], 'Group 3');
  });

  test('_computeVisibleToAll', () => {
    assert.equal(element._computeVisibleToAll(groups[0]), 'No');
    assert.equal(element._computeVisibleToAll(groups[1]), 'Yes');
  });

  test('_computeGroupPath', () => {
    let urlStub = sinon.stub(GerritNav, 'getUrlForGroup').callsFake(
        () => { return '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5'; });

    let group = {
      id: 'e2cd66f88a2db4d391ac068a92d987effbe872f5',
    };
    assert.equal(element._computeGroupPath(group),
        '/admin/groups/e2cd66f88a2db4d391ac068a92d987effbe872f5');

    group = {
      name: 'admin',
    };
    assert.isUndefined(element._computeGroupPath(group));

    urlStub.restore();

    urlStub = sinon.stub(GerritNav, 'getUrlForGroup').callsFake(
        () => { return '/admin/groups/user/test'; });

    group = {
      id: 'user%2Ftest',
    };
    assert.equal(element._computeGroupPath(group),
        '/admin/groups/user/test');
  });
});

