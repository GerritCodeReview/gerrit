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
        /* HTML */ `<table class="genericList" id="list">
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
        </table>`
      );
    });

    test('render', async () => {
      await waitEventLoop();
      assert.shadowDom.equal(
        element,
        /* HTML */ `
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
        `
      );
    });
  });
});
