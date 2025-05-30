/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-watched-projects-editor';
import {GrWatchedProjectsEditor} from './gr-watched-projects-editor';
import {stubRestApi, waitUntil} from '../../../test/test-utils';
import {ProjectWatchInfo} from '../../../types/common';
import {queryAndAssert} from '../../../test/test-utils';
import {IronInputElement} from '@polymer/iron-input';
import {assertIsDefined} from '../../../utils/common-util';
import {assert, fixture, html} from '@open-wc/testing';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';

suite('gr-watched-projects-editor tests', () => {
  let element: GrWatchedProjectsEditor;
  let suggestionStub: sinon.SinonStub;

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
    suggestionStub = stubRestApi('getSuggestedRepos').callsFake(
      (input: string) => {
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
      }
    );

    element = await fixture(
      html`<gr-watched-projects-editor></gr-watched-projects-editor>`
    );

    await element.loadData();
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles">
          <table id="watchedProjects">
            <thead>
              <tr>
                <th>Repo</th>
                <th class="notifType">Changes</th>
                <th class="notifType">Patches</th>
                <th class="notifType">Comments</th>
                <th class="notifType">Submits</th>
                <th class="notifType">Abandons</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>project a</td>
                <td class="notifControl">
                  <input data-key="notify_new_changes" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_new_patch_sets" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_all_comments" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input
                    checked=""
                    data-key="notify_submitted_changes"
                    type="checkbox"
                  />
                </td>
                <td class="notifControl">
                  <input
                    checked=""
                    data-key="notify_abandoned_changes"
                    type="checkbox"
                  />
                </td>
                <td>
                  <gr-button
                    aria-disabled="false"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Delete
                  </gr-button>
                </td>
              </tr>
              <tr>
                <td>
                  project b
                  <div class="projectFilter">filter 1</div>
                </td>
                <td class="notifControl">
                  <input
                    checked=""
                    data-key="notify_new_changes"
                    type="checkbox"
                  />
                </td>
                <td class="notifControl">
                  <input data-key="notify_new_patch_sets" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_all_comments" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_submitted_changes" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_abandoned_changes" type="checkbox" />
                </td>
                <td>
                  <gr-button
                    aria-disabled="false"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Delete
                  </gr-button>
                </td>
              </tr>
              <tr>
                <td>
                  project b
                  <div class="projectFilter">filter 2</div>
                </td>
                <td class="notifControl">
                  <input data-key="notify_new_changes" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_new_patch_sets" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_all_comments" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_submitted_changes" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_abandoned_changes" type="checkbox" />
                </td>
                <td>
                  <gr-button
                    aria-disabled="false"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Delete
                  </gr-button>
                </td>
              </tr>
              <tr>
                <td>project c</td>
                <td class="notifControl">
                  <input
                    checked=""
                    data-key="notify_new_changes"
                    type="checkbox"
                  />
                </td>
                <td class="notifControl">
                  <input
                    checked=""
                    data-key="notify_new_patch_sets"
                    type="checkbox"
                  />
                </td>
                <td class="notifControl">
                  <input
                    checked=""
                    data-key="notify_all_comments"
                    type="checkbox"
                  />
                </td>
                <td class="notifControl">
                  <input data-key="notify_submitted_changes" type="checkbox" />
                </td>
                <td class="notifControl">
                  <input data-key="notify_abandoned_changes" type="checkbox" />
                </td>
                <td>
                  <gr-button
                    aria-disabled="false"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Delete
                  </gr-button>
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <th>
                  <gr-autocomplete
                    allow-non-suggested-values=""
                    id="newProject"
                    placeholder="Repo"
                    tab-complete=""
                    threshold="1"
                  >
                  </gr-autocomplete>
                </th>
                <th colspan="5">
                  <iron-input class="newFilterInput" id="newFilterInput">
                    <input
                      class="newFilterInput"
                      id="newFilter"
                      placeholder="branch:name, or other search expression"
                    />
                  </iron-input>
                </th>
                <th>
                  <gr-button
                    aria-disabled="false"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Add
                  </gr-button>
                </th>
              </tr>
            </tfoot>
          </table>
        </div>
      `
    );
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

  test('autocompletes repo input', async () => {
    const repoAutocomplete = queryAndAssert<GrAutocomplete>(
      element,
      'gr-autocomplete'
    );
    const repoInput = queryAndAssert<HTMLInputElement>(
      repoAutocomplete,
      '#input'
    );

    repoInput.focus();
    repoAutocomplete.debounceWait = 10;
    repoAutocomplete.text = 'the';
    await waitUntil(() => suggestionStub.called);
    await repoAutocomplete.updateComplete;

    assert.isTrue(suggestionStub.calledWith('the'));
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

    const button = queryAndAssert<GrButton>(
      element,
      'table tbody tr:nth-child(2) gr-button'
    );
    button.click();

    await element.updateComplete;

    const rows = queryAndAssert(element, 'table tbody').querySelectorAll('tr');

    assert.equal(rows.length, 3);

    assert.equal(element.projectsToRemove.length, 1);
    assert.equal(element.projectsToRemove[0].project, 'project b');
  });
});
