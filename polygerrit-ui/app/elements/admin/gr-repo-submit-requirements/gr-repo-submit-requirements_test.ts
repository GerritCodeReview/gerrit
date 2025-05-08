/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-repo-submit-requirements';
import {GrRepoSubmitRequirements} from './gr-repo-submit-requirements';
import {stubRestApi, waitEventLoop} from '../../../test/test-utils';
import {RepoName, SubmitRequirementInfo} from '../../../types/common';
import {fixture, html, assert} from '@open-wc/testing';

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
      element.isAdmin = true;
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
          <dialog id="createDialog" tabindex="-1">
            <gr-dialog cancel-label="Cancel" confirm-label="Create" disabled="">
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
                      <iron-input>
                        <input
                          id="name"
                          required=""
                          type="text"
                        >
                      </iron-input>
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
                      <iron-input>
                      <input
                        id="applicability"
                        placeholder="Optional"
                        type="text"
                      >
                      </iron-input>
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
                      <iron-input>
                      <input
                        id="submittability"
                        required=""
                        type="text"
                        >
                      </iron-input>
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
                      <iron-input>
                        <input
                          id="override"
                          placeholder="Optional"
                          type="text"
                        >
                      </iron-input>
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
            </gr-dialog>
          </dialog>
        `
      );
    });
  });
});
