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

import '../../../test/common-test-setup-karma';
import './gr-watched-projects-editor';
import {GrWatchedProjectsEditor} from './gr-watched-projects-editor';
import {stubRestApi} from '../../../test/test-utils';
import {ProjectWatchInfo} from '../../../types/common';
import {queryAll, queryAndAssert} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-watched-projects-editor');

suite('gr-watched-projects-editor tests', () => {
  let element: GrWatchedProjectsEditor;

  setup(async () => {
    const projects = [
      {
        project: 'project a',
        notify_submitted_changes: true,
        notify_abandoned_changes: true,
      },
      {
        project: 'project b',
        filter: 'filter 1',
        notify_new_changes: true,
      },
      {
        project: 'project b',
        filter: 'filter 2',
      },
      {
        project: 'project c',
        notify_new_changes: true,
        notify_new_patch_sets: true,
        notify_all_comments: true,
      },
    ] as ProjectWatchInfo[];

    stubRestApi('getWatchedProjects').returns(Promise.resolve(projects));
    stubRestApi('getSuggestedProjects').callsFake(input => {
      if (input.startsWith('th')) {
        return Promise.resolve({
          'the project': {
            id: 'the project',
            state: 'ACTIVE',
            web_links: [],
          },
        });
      } else {
        return Promise.resolve({});
      }
    });

    element = basicFixture.instantiate();

    await element.loadData();
    await flush();
  });

  test('renders', () => {
    const rows = queryAndAssert(element, 'table').querySelectorAll('tbody tr');
    assert.equal(rows.length, 4);

    function getKeysOfRow(row: number) {
      const boxes = queryAll(rows[row], 'input[checked]');
      return Array.prototype.map.call(boxes, e => e.getAttribute('data-key'));
    }

    let checkedKeys = getKeysOfRow(0);
    assert.equal(checkedKeys.length, 2);
    assert.equal(checkedKeys[0], 'notify_submitted_changes');
    assert.equal(checkedKeys[1], 'notify_abandoned_changes');

    checkedKeys = getKeysOfRow(1);
    assert.equal(checkedKeys.length, 1);
    assert.equal(checkedKeys[0], 'notify_new_changes');

    checkedKeys = getKeysOfRow(2);
    assert.equal(checkedKeys.length, 0);

    checkedKeys = getKeysOfRow(3);
    assert.equal(checkedKeys.length, 3);
    assert.equal(checkedKeys[0], 'notify_new_changes');
    assert.equal(checkedKeys[1], 'notify_new_patch_sets');
    assert.equal(checkedKeys[2], 'notify_all_comments');
  });

  test('_getProjectSuggestions empty', async () => {
    const projects = await element._getProjectSuggestions('nonexistent');
    assert.equal(projects.length, 0);
  });

  test('_getProjectSuggestions non-empty', async () => {
    const projects = await element._getProjectSuggestions('the project');
    assert.equal(projects.length, 1);
    assert.equal(projects[0].name, 'the project');
  });

  test('_getProjectSuggestions non-empty with two letter project', async () => {
    const projects = await element._getProjectSuggestions('th');
    assert.equal(projects.length, 1);
    assert.equal(projects[0].name, 'the project');
  });

  test('_canAddProject', () => {
    assert.isFalse(element._canAddProject(null, null, null));

    // Can add a project that is not in the list.
    assert.isTrue(element._canAddProject('project d', null, null));
    assert.isTrue(element._canAddProject('project d', null, 'filter 3'));

    // Cannot add a project that is in the list with no filter.
    assert.isFalse(element._canAddProject('project a', null, null));

    // Can add a project that is in the list if the filter differs.
    assert.isTrue(element._canAddProject('project a', null, 'filter 4'));

    // Cannot add a project that is in the list with the same filter.
    assert.isFalse(element._canAddProject('project b', null, 'filter 1'));
    assert.isFalse(element._canAddProject('project b', null, 'filter 2'));

    // Can add a project that is in the list using a new filter.
    assert.isTrue(element._canAddProject('project b', null, 'filter 3'));

    // Can add a project that is not added by the auto complete
    assert.isTrue(element._canAddProject(null, 'test', null));
  });

  test('_getNewProjectIndex', () => {
    // Projects are sorted in ASCII order.
    assert.equal(element._getNewProjectIndex('project A', 'filter'), 0);
    assert.equal(element._getNewProjectIndex('project a', 'filter'), 1);

    // Projects are sorted by filter when the names are equal
    assert.equal(element._getNewProjectIndex('project b', 'filter 0'), 1);
    assert.equal(element._getNewProjectIndex('project b', 'filter 1.5'), 2);
    assert.equal(element._getNewProjectIndex('project b', 'filter 3'), 3);

    // Projects with filters follow those without
    assert.equal(element._getNewProjectIndex('project c', 'filter'), 4);
  });

  test('_handleAddProject', () => {
    element.$.newProject.value = 'project d';
    element.$.newProject.setText('project d');
    element.$.newFilterInput.bindValue = '';

    element._handleAddProject();

    const projects = element._projects!;
    assert.equal(projects.length, 5);
    assert.equal(projects[4].project, 'project d');
    assert.isNotOk(projects[4].filter);
    assert.isTrue(projects[4]._is_local);
  });

  test('_handleAddProject with invalid inputs', () => {
    element.$.newProject.value = 'project b';
    element.$.newProject.setText('project b');
    element.$.newFilterInput.bindValue = 'filter 1';
    element.$.newFilter.value = 'filter 1';

    element._handleAddProject();

    assert.equal(element._projects!.length, 4);
  });

  test('_handleRemoveProject', () => {
    assert.deepEqual(element._projectsToRemove, []);

    const button = queryAndAssert(
      element,
      'table tbody tr:nth-child(2) gr-button'
    );
    MockInteractions.tap(button);

    flush();

    const rows = queryAndAssert(element, 'table tbody').querySelectorAll('tr');

    assert.equal(rows.length, 3);

    assert.equal(element._projectsToRemove.length, 1);
    assert.equal(element._projectsToRemove[0].project, 'project b');
  });
});
