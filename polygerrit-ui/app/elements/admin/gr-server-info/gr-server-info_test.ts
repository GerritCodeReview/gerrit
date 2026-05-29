/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, html} from '@open-wc/testing';
import './gr-server-info';
import {GrServerInfo} from './gr-server-info';
import {assert} from '@open-wc/testing';
import {createServerInfo} from '../../../test/test-data-generators';

suite('gr-server-info tests', () => {
  let element: GrServerInfo;

  setup(async () => {
    element = await fixture<GrServerInfo>(html`
      <gr-server-info></gr-server-info>
    `);
    element.serverInfo = {
      ...createServerInfo(),
      metadata: [
        {
          name: 'test.name',
          value: 'test.value',
          description: 'test description',
        },
      ],
    };
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      `
        <main class="gr-form-styles read-only">
          <table id="list" class="genericList">
            <tbody>
              <tr class="headerRow">
                <th class="metadataName topHeader">Name</th>
                <th class="metadataValue topHeader">Value</th>
                <th class="metadataWebLinks topHeader">Links</th>
                <th class="metadataDescription topHeader">Description</th>
              </tr>
            </tbody>
            <tbody>
              <tr class="table">
                <td class="metadataName">accounts.visibility</td>
                <td class="metadataValue">ALL</td>
                <td class="metadataWebLinks"></td>
                <td class="metadataDescription">
                  Controls visibility of other users' dashboard pages and completion suggestions to web users.
                </td>
              </tr>
              <tr class="table">
                <td class="metadataName">test.name</td>
                <td class="metadataValue">test.value</td>
                <td class="metadataWebLinks"></td>
                <td class="metadataDescription">test description</td>
              </tr>
            </tbody>
          </table>
        </main>
      `
    );
  });
});
