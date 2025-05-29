/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-agreements-list';
import {stubRestApi, waitEventLoop} from '../../../test/test-utils';
import {GrAgreementsList} from './gr-agreements-list';
import {ContributorAgreementInfo} from '../../../types/common';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-agreements-list tests', () => {
  let element: GrAgreementsList;

  setup(async () => {
    const agreements: ContributorAgreementInfo[] = [
      {
        url: 'some url',
        description: 'Agreements 1 description',
        name: 'Agreements 1',
      },
    ];

    stubRestApi('getAccountAgreements').returns(Promise.resolve(agreements));

    element = await fixture(html`<gr-agreements-list></gr-agreements-list>`);

    await element.loadData();
    await waitEventLoop();
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles">
          <table id="agreements">
            <thead>
              <tr>
                <th class="nameColumn">Name</th>
                <th class="descriptionColumn">Description</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="nameColumn">
                  <a href="/some url" rel="external"> Agreements 1 </a>
                </td>
                <td class="descriptionColumn">Agreements 1 description</td>
              </tr>
            </tbody>
          </table>
          <a href="/settings/new-agreement"> New Contributor Agreement </a>
        </div>
      `
    );
  });
});
