/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-repo-submit-requirements';
import {GrRepoSubmitRequirements} from './gr-repo-submit-requirements';
import {
  queryAndAssert,
  stubRestApi,
  waitEventLoop,
} from '../../../test/test-utils';
import {RepoName, SubmitRequirementInfo} from '../../../types/common';
import {assert, fixture, html} from '@open-wc/testing';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-repo-submit-requirements tests', () => {
  let element: GrRepoSubmitRequirements;

  setup(async () => {
    element = await fixture(
      html`<gr-repo-submit-requirements></gr-repo-submit-requirements>`
    );
  });

  suite('submit requirements table', () => {
    setup(() => {
      stubRestApi('getRepoSubmitRequirements').returns(
        Promise.resolve([
          {
            name: 'Verified',
            description: 'CI result status for build and tests is passing',
            submittability_expression:
              'label:Verified=MAX AND -label:Verified=MIN',
          },
        ] as SubmitRequirementInfo[])
      );
      element.repo = 'test' as RepoName;
    });

    test('render loading', () => {
      element.repo = 'test2' as RepoName;
      assert.shadowDom.equal(
        element,
        /* HTML */ `<gr-list-view>
          <table class="genericList" id="list">
            <tbody>
              <tr class="headerRow">
                <th class="topHeader">Name</th>
                <th class="topHeader">Description</th>
                <th class="topHeader">Applicability Expression</th>
                <th class="topHeader">Submittability Expression</th>
                <th class="topHeader">Override Expression</th>
                <th
                  class="topHeader"
                  title="Whether override is allowed in child projects"
                >
                  Allow Override
                </th>
              </tr>
            </tbody>
            <tbody id="submit-requirements">
              <tr id="loadingContainer">
                <td>Loading...</td>
              </tr>
            </tbody>
          </table>
        </gr-list-view>`
      );
    });

    test('render', async () => {
      await waitEventLoop();
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-list-view>
            <table class="genericList" id="list">
              <tbody>
                <tr class="headerRow">
                  <th class="topHeader">Name</th>
                  <th class="topHeader">Description</th>
                  <th class="topHeader">Applicability Expression</th>
                  <th class="topHeader">Submittability Expression</th>
                  <th class="topHeader">Override Expression</th>
                  <th
                    class="topHeader"
                    title="Whether override is allowed in child projects"
                  >
                    Allow Override
                  </th>
                </tr>
              </tbody>
              <tbody id="submit-requirements">
                <tr class="table">
                  <td class="name">Verified</td>
                  <td class="desc">
                    CI result status for build and tests is passing
                  </td>
                  <td class="applicability"></td>
                  <td class="submittability">
                    label:Verified=MAX AND -label:Verified=MIN
                  </td>
                  <td class="override"></td>
                  <td class="allowOverride"></td>
                </tr>
              </tbody>
            </table>
          </gr-list-view>
        `
      );
    });

    test('render as admin', async () => {
      await waitEventLoop();
      element.isProjectOwner = true;
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-list-view>
            <table class="genericList" id="list">
              <tbody>
                <tr class="headerRow">
                  <th class="topHeader">Name</th>
                  <th class="topHeader">Description</th>
                  <th class="topHeader">Applicability Expression</th>
                  <th class="topHeader">Submittability Expression</th>
                  <th class="topHeader">Override Expression</th>
                  <th
                    class="topHeader"
                    title="Whether override is allowed in child projects"
                  >
                    Allow Override
                  </th>
                  <th class="topHeader"></th>
                </tr>
              </tbody>
              <tbody id="submit-requirements">
                <tr class="table">
                  <td class="name">Verified</td>
                  <td class="desc">
                    CI result status for build and tests is passing
                  </td>
                  <td class="applicability"></td>
                  <td class="submittability">
                    label:Verified=MAX AND -label:Verified=MIN
                  </td>
                  <td class="override"></td>
                  <td class="allowOverride"></td>
                  <td class="actions">
                  <gr-button
                      aria-disabled="false"
                      class="editBtn"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Edit
                    </gr-button>
                    <gr-button
                      aria-disabled="false"
                      class="deleteBtn"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Delete
                    </gr-button>
                  </td>
                </tr>
              </tbody>
            </table>
          </gr-list-view>
          <dialog id="createDialog" tabindex="-1">
            <gr-dialog>
              <div class="header" slot="header">Create Submit Requirement</div>
              <div class="main" slot="main">
                <div class="gr-form-styles">
              <div id="form">
                <section>
                  <div class="title-flex">
                    <span class="title">
                      Name
                    </span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <md-outlined-text-field
                        autocomplete=""
                        class="showBlueFocusBorder"
                        id="name"
                        inputmode=""
                        required=""
                        type="text"
                      >
                      </md-outlined-text-field>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">
                      Description
                    </span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <textarea
                        id="description"
                        placeholder="Optional"
                      >
                      </textarea>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">
                      Applicability Expression
                    </span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <md-outlined-text-field
                        autocomplete=""
                        class="showBlueFocusBorder"
                        id="applicability"
                        inputmode=""
                        placeholder="Optional"
                        type="text"
                      >
                      </md-outlined-text-field>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">
                      Submittability Expression
                    </span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <md-outlined-text-field
                        autocomplete=""
                        class="showBlueFocusBorder"
                        id="submittability"
                        inputmode=""
                        required=""
                        type="text"
                      >
                      </md-outlined-text-field>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">
                      Override Expression
                    </span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <md-outlined-text-field
                        autocomplete=""
                        class="showBlueFocusBorder"
                        id="override"
                        inputmode=""
                        placeholder="Optional"
                        type="text"
                      >
                      </md-outlined-text-field>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">
                      Allow Override in Child Projects
                    </span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <gr-select id="allowOverride">
                        <select>
                          <option value="true">
                            True
                          </option>
                          <option value="false">
                            False
                          </option>
                        </select>
                      </gr-select>
                    </span>
                  </div>
                </section>
              </div>
            </div>
          </div>
          <div class="footer" slot="footer">
            <gr-button
              aria-disabled="false"
              role="button"
              tabindex="0"
              link=""
            >
              Cancel
            </gr-button>
            <gr-button
              aria-disabled="true"
              class="action save-button"
              disabled=""
              link=""
              primary=""
              role="button"
              tabindex="-1"
            >
              Create
            </gr-button>
            <gr-button
              aria-disabled="true"
              class="action save-for-review"
              disabled=""
              link=""
              primary=""
              role="button"
              tabindex="-1"
            >
              Save for review
            </gr-button>
          </div>
        </gr-dialog>
      </dialog>
      <dialog
        id="deleteDialog"
        tabindex="-1"
      >
        <gr-dialog>
          <div
            class="header"
            slot="header"
          >
            Delete Submit Requirement
          </div>
          <div
            class="main"
            slot="main"
          >
          Are you sure you want to delete the submit requirement
            ""?
          </div>
          <div class="footer" slot="footer">
            <gr-button
              aria-disabled="false"
              role="button"
              tabindex="0"
              link=""
            >
              Cancel
            </gr-button>
            <gr-button
              aria-disabled="false"
              class="action"
              link=""
              role="button"
              tabindex="0"
            >
              Delete
            </gr-button>
            <gr-button
              aria-disabled="false"
              class="action"
              link=""
              primary=""
              role="button"
              tabindex="0"
            >
              Delete for review
            </-button>
          </div>
        </gr-dialog>
      </dialog>
    `
      );
    });

    test('open edit dialog', async () => {
      await waitEventLoop();
      element.isProjectOwner = true;
      await element.updateComplete;

      const editButton = queryAndAssert<GrButton>(element, '.editBtn');
      editButton.click();
      await element.updateComplete;

      // Verify dialog is open and has correct title
      const dialog = queryAndAssert<HTMLDialogElement>(
        element,
        '#createDialog'
      );
      assert.isTrue(dialog.open);

      const dialogTitle = queryAndAssert<HTMLElement>(element, '.header');
      assert.equal(dialogTitle.textContent?.trim(), 'Edit Submit Requirement');

      // Verify form is populated with correct data
      const nameInput = queryAndAssert<HTMLInputElement>(element, '#name');
      assert.equal(nameInput.value, 'Verified');
      assert.isTrue(nameInput.disabled);

      const descriptionTextarea = queryAndAssert<HTMLTextAreaElement>(
        element,
        '#description'
      );
      assert.equal(
        descriptionTextarea.value,
        'CI result status for build and tests is passing'
      );
      assert.isFalse(descriptionTextarea.disabled);

      const submittabilityInput = queryAndAssert<HTMLInputElement>(
        element,
        '#submittability'
      );
      assert.equal(
        submittabilityInput.value,
        'label:Verified=MAX AND -label:Verified=MIN'
      );

      // Verify save button is enabled
      const saveButton = queryAndAssert<HTMLElement>(
        element,
        'gr-button.save-button'
      );
      assert.isFalse(saveButton.hasAttribute('disabled'));
    });
  });

  suite('admin', () => {
    setup(async () => {
      element.isProjectOwner = true;
      await element.updateComplete;
    });

    test('save button is disabled when require_change_for_config_update is set', async () => {
      element.disableSaveWithoutReview = true;
      element.newRequirement.name = 'Test';
      element.newRequirement.submittability_expression = 'test';
      await element.updateComplete;

      const dialog = queryAndAssert<HTMLDialogElement>(
        element,
        '#createDialog'
      );
      dialog.showModal();
      await element.updateComplete;

      const saveButton = queryAndAssert<GrButton>(
        element,
        'gr-dialog gr-button.action.save-button'
      );
      assert.isTrue(saveButton.disabled);
    });
  });
});
