/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-watched-projects-editor';
import {GrWatchedProjectsEditor} from './gr-watched-projects-editor';
import {stubRestApi} from '../../../test/test-utils';
import {ProjectWatchInfo} from '../../../types/common';
import {queryAll, queryAndAssert} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {IronInputElement} from '@polymer/iron-input';
import {assertIsDefined} from '../../../utils/common-util';

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
    await element.updateComplete;
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

  test('getProjectSuggestions empty', async () => {
    const projects = await element.getProjectSuggestions('nonexistent');
    assert.equal(projects.length, 0);
  });

  test('getProjectSuggestions non-empty', async () => {
    const projects = await element.getProjectSuggestions('the project');
    assert.equal(projects.length, 1);
    assert.equal(projects[0].name, 'the project');
  });

  test('getProjectSuggestions non-empty with two letter project', async () => {
    const projects = await element.getProjectSuggestions('th');
    assert.equal(projects.length, 1);
    assert.equal(projects[0].name, 'the project');
  });

  test('_canAddProject', () => {
    assert.isFalse(element.canAddProject(null, null, null));

    // Can add a project that is not in the list.
    assert.isTrue(element.canAddProject('project d', null, null));
    assert.isTrue(element.canAddProject('project d', null, 'filter 3'));

    // Cannot add a project that is in the list with no filter.
    assert.isFalse(element.canAddProject('project a', null, null));

    // Can add a project that is in the list if the filter differs.
    assert.isTrue(element.canAddProject('project a', null, 'filter 4'));

    // Cannot add a project that is in the list with the same filter.
    assert.isFalse(element.canAddProject('project b', null, 'filter 1'));
    assert.isFalse(element.canAddProject('project b', null, 'filter 2'));

    // Can add a project that is in the list using a new filter.
    assert.isTrue(element.canAddProject('project b', null, 'filter 3'));

    // Can add a project that is not added by the auto complete
    assert.isTrue(element.canAddProject(null, 'test', null));
  });

  test('getNewProjectIndex', () => {
    // Projects are sorted in ASCII order.
    assert.equal(element.getNewProjectIndex('project A', 'filter'), 0);
    assert.equal(element.getNewProjectIndex('project a', 'filter'), 1);

    // Projects are sorted by filter when the names are equal
    assert.equal(element.getNewProjectIndex('project b', 'filter 0'), 1);
    assert.equal(element.getNewProjectIndex('project b', 'filter 1.5'), 2);
    assert.equal(element.getNewProjectIndex('project b', 'filter 3'), 3);

    // Projects with filters follow those without
    assert.equal(element.getNewProjectIndex('project c', 'filter'), 4);
  });

  test('handleAddProject', () => {
    assertIsDefined(element.newProject, 'newProject');
    element.newProject.value = 'project d';
    element.newProject.setText('project d');
    queryAndAssert<IronInputElement>(element, '#newFilterInput').bindValue = '';

    element.handleAddProject();

    const projects = element.projects!;
    assert.equal(projects.length, 5);
    assert.equal(projects[4].project, 'project d');
    assert.isNotOk(projects[4].filter);
    assert.isTrue(projects[4]._is_local);
  });

  test('_handleAddProject with invalid inputs', () => {
    assertIsDefined(element.newProject, 'newProject');
    element.newProject.value = 'project b';
    element.newProject.setText('project b');
    queryAndAssert<IronInputElement>(element, '#newFilterInput').bindValue =
      'filter 1';
    assertIsDefined(element.newFilter, 'newFilter');
    element.newFilter.value = 'filter 1';

    element.handleAddProject();

    assert.equal(element.projects!.length, 4);
  });

  test('_handleRemoveProject', async () => {
    assert.deepEqual(element.projectsToRemove, []);

    const button = queryAndAssert(
      element,
      'table tbody tr:nth-child(2) gr-button'
    );
    MockInteractions.tap(button);

    await element.updateComplete;

    const rows = queryAndAssert(element, 'table tbody').querySelectorAll('tr');

    assert.equal(rows.length, 3);

    assert.equal(element.projectsToRemove.length, 1);
    assert.equal(element.projectsToRemove[0].project, 'project b');
  });
});
