/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-agreements-list';
import {queryAll, stubRestApi} from '../../../test/test-utils';
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
    const rows = queryAll<HTMLTableRowElement>(element, 'tbody tr') ?? [];
    assert.equal(rows.length, 1);

    const nameCells = Array.from(rows).map(row =>
      queryAll<HTMLTableElement>(row, 'td')[0].textContent?.trim()
    );

    assert.equal(nameCells[0], 'Agreements 1');
  });
});
