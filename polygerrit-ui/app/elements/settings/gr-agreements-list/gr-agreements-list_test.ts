/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-agreements-list';
import {stubRestApi} from '../../../test/test-utils';
import {GrAgreementsList} from './gr-agreements-list';
import {ContributorAgreementInfo} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-agreements-list');

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

    element = basicFixture.instantiate();

    await element.loadData();
    await flush();
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
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
    `);
  });
});
