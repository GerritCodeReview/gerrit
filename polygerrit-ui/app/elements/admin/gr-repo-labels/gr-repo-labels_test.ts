/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-repo-labels';
import {GrRepoLabels} from './gr-repo-labels';
import {
  queryAndAssert,
  stubRestApi,
  waitEventLoop,
} from '../../../test/test-utils';
import {RepoName} from '../../../types/common';
import {assert, fixture, html} from '@open-wc/testing';
import {GrButton} from '../../shared/gr-button/gr-button';
import {
  LabelDefinitionInfo,
  LabelDefinitionInfoFunction,
} from '../../../api/rest-api';
import {GrDropdownList} from '../../shared/gr-dropdown-list/gr-dropdown-list';

suite('gr-repo-labels tests', () => {
  let element: GrRepoLabels;

  setup(async () => {
    element = await fixture(html`<gr-repo-labels></gr-repo-labels>`);
  });

  suite('labels table', () => {
    setup(() => {
      stubRestApi('getRepoLabels').returns(
        Promise.resolve([
          {
            name: 'Code-Review',
            description: 'This is a cool label',
            function: 'MaxWithBlock',
            values: undefined,
            default_value: 0,
            copy_condition: 'is:MIN',
            unset_copy_condition: true,
            allow_post_submit: true,
            can_override: true,
            ignore_self_approval: false,
            branches: ['refs/heads/main'],
          },
        ] as unknown as LabelDefinitionInfo[])
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
                <th class="topHeader">Function</th>
                <th class="topHeader">Default Value</th>
                <th class="topHeader">Copy Condition</th>
                <th class="topHeader">Allow Post Submit</th>
                <th class="topHeader">Can Override</th>
                <th class="topHeader">Ignore Self Approval</th>
                <th class="topHeader">Branches</th>
                <th class="topHeader">Values</th>
              </tr>
            </tbody>
            <tbody id="labels">
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
                  <th class="topHeader">Function</th>
                  <th class="topHeader">Default Value</th>
                  <th class="topHeader">Copy Condition</th>
                  <th class="topHeader">Allow Post Submit</th>
                  <th class="topHeader">Can Override</th>
                  <th class="topHeader">Ignore Self Approval</th>
                  <th class="topHeader">Branches</th>
                  <th class="topHeader">Values</th>
                </tr>
              </tbody>
              <tbody id="labels">
                <tr class="table">
                  <td class="name">Code-Review</td>
                  <td class="description">This is a cool label</td>
                  <td class="function">
                    MaxWithBlock
                    <gr-icon
                      icon="warning"
                      title="This function is deprecated."
                    ></gr-icon>
                  </td>
                  <td class="defaultValue">0</td>
                  <td class="copyCondition">is:MIN</td>
                  <td class="allowPostSubmit">✓</td>
                  <td class="canOverride">✓</td>
                  <td class="ignoreSelfApproval"></td>
                  <td class="branches">refs/heads/main</td>
                  <td class="values"></td>
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
        `
          <gr-list-view>
            <table class="genericList" id="list">
              <tbody>
                <tr class="headerRow">
                  <th class="topHeader">Name</th>
                  <th class="topHeader">Description</th>
                  <th class="topHeader">Function</th>
                  <th class="topHeader">Default Value</th>
                  <th class="topHeader">Copy Condition</th>
                  <th class="topHeader">Allow Post Submit</th>
                  <th class="topHeader">Can Override</th>
                  <th class="topHeader">Ignore Self Approval</th>
                  <th class="topHeader">Branches</th>
                  <th class="topHeader">Values</th>
                  <th class="topHeader"></th>
                </tr>
              </tbody>
              <tbody id="labels">
                <tr class="table">
                  <td class="name">Code-Review</td>
                  <td class="description">This is a cool label</td>
                  <td class="function">
                    MaxWithBlock
                    <gr-icon
                      icon="warning"
                      title="This function is deprecated."
                    ></gr-icon>
                  </td>
                  <td class="defaultValue">0</td>
                  <td class="copyCondition">is:MIN</td>
                  <td class="allowPostSubmit">✓</td>
                  <td class="canOverride">✓</td>
                  <td class="ignoreSelfApproval"></td>
                  <td class="branches">refs/heads/main</td>
                  <td class="values"></td>
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
              <div class="header" slot="header">Create Label</div>
              <div class="main" slot="main">
                <div class="gr-form-styles">
                  <div id="form">
                    <section>
                      <div class="title-flex">
                        <span class="title"> Name </span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <iron-input>
                            <input id="name" required="" type="text" />
                          </iron-input>
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title">Description</span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <iron-input>
                            <input id="description" type="text" />
                          </iron-input>
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title"> Function </span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <gr-dropdown-list> </gr-dropdown-list>
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title"> Default Value </span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <iron-input>
                            <input id="defaultValue" type="number" />
                          </iron-input>
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title"> Copy Condition </span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <iron-input>
                            <input id="copyCondition" type="text" />
                          </iron-input>
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title">Unset Copy Condition</span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <input id="unsetCopyCondition" type="checkbox" />
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title">Can Override</span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <input id="canOverride" checked="" type="checkbox" />
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title">Allow Post Submit</span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <input id="allowPostSubmit" type="checkbox" />
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title">Ignore Self Approval</span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <input id="ignoreSelfApproval" type="checkbox" />
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title">Branches</span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <textarea id="branches" rows="2"></textarea>
                        </span>
                      </div>
                    </section>
                    <section>
                      <div class="title-flex">
                        <span class="title"> Values </span>
                      </div>
                      <div class="value-flex">
                        <span class="value">
                          <textarea id="values"></textarea>
                        </span>
                      </div>
                    </section>
                  </div>
                </div>
              </div>
              <div class="footer" slot="footer">
                <gr-button aria-disabled="false" role="button" link="" 
                  tabindex="0"
                  >Cancel</gr-button
                >
                <gr-button
                  aria-disabled="true"
                  class="action save-button" 
                  disabled=""
                  link=""
                  primary=""
                  role="button"
                  tabindex="-1"
                  >Create</gr-button
                >
                <gr-button
                  aria-disabled="true"
                  class="action save-for-review"
                  disabled=""
                  link=""
                  primary=""
                  role="button"
                  tabindex="-1"
                  >Save for review</gr-button
                >
              </div>
            </gr-dialog>
          </dialog>
          <dialog id="deleteDialog" tabindex="-1">
            <gr-dialog>
              <div class="header" slot="header">Delete Label</div>
              <div class="main" slot="main">
                Are you sure you want to delete the label
            ""?
              </div>
              <div
                class="footer"
                slot="footer"
              >
                <gr-button
                  aria-disabled="false"
                  role="button"
                  link=""
                  tabindex="0"
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
                  primary=""
                  link=""
                  role="button"
                  tabindex="0"
                >
                  Delete for review
                </gr-button>
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
      assert.equal(dialogTitle.textContent?.trim(), 'Edit Label');

      // Verify form is populated with correct data
      const nameInput = queryAndAssert<HTMLInputElement>(element, '#name');
      assert.equal(nameInput.value, 'Code-Review');
      assert.isTrue(nameInput.disabled);

      const descriptionInput = queryAndAssert<HTMLInputElement>(
        element,
        '#description'
      );
      assert.equal(descriptionInput.value, 'This is a cool label');

      const functionDropdown = queryAndAssert<GrDropdownList>(
        element,
        'gr-dropdown-list'
      );
      assert.equal(functionDropdown.value, 'MaxWithBlock');
      assert.deepEqual(
        functionDropdown.items,
        Object.values(LabelDefinitionInfoFunction).map(fun => {
          return {
            text: fun,
            value: fun,
          };
        })
      );

      const copyConditionInput = queryAndAssert<HTMLInputElement>(
        element,
        '#copyCondition'
      );
      assert.equal(copyConditionInput.value, 'is:MIN');

      const canOverrideInput = queryAndAssert<HTMLInputElement>(
        element,
        '#canOverride'
      );
      assert.isTrue(canOverrideInput.checked);

      const allowPostSubmitInput = queryAndAssert<HTMLInputElement>(
        element,
        '#allowPostSubmit'
      );
      assert.isTrue(allowPostSubmitInput.checked);

      const ignoreSelfApprovalInput = queryAndAssert<HTMLInputElement>(
        element,
        '#ignoreSelfApproval'
      );
      assert.isFalse(ignoreSelfApprovalInput.checked);

      const branchesTextarea = queryAndAssert<HTMLTextAreaElement>(
        element,
        '#branches'
      );
      assert.equal(branchesTextarea.value, 'refs/heads/main');

      // Verify save button is enabled
      const saveButton = queryAndAssert<HTMLElement>(element, 'gr-dialog');
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
      element.newLabel.name = 'Test';
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
