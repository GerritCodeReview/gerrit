/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-user-header';
import {GrUserHeader} from './gr-user-header';
import {stubRestApi} from '../../../test/test-utils';
import {AccountId, EmailAddress, Timestamp} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-user-header');

suite('gr-user-header tests', () => {
  let element: GrUserHeader;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('loads and clears account info', async () => {
    stubRestApi('getAccountDetails').returns(
      Promise.resolve({
        name: 'foo',
        email: 'bar' as EmailAddress,
        status: 'OOO',
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );

    element.userId = 10 as AccountId;
    await flush();

    assert.isOk(element._accountDetails);
    assert.isOk(element._status);

    element.userId = undefined;
    await flush();

    assert.isUndefined(element._accountDetails);
    assert.equal(element._status, '');
  });

  test('_computeDashboardLinkClass', () => {
    assert.include(element._computeDashboardLinkClass(false, false), 'hide');
    assert.include(element._computeDashboardLinkClass(true, false), 'hide');
    assert.include(element._computeDashboardLinkClass(false, true), 'hide');
    assert.notInclude(element._computeDashboardLinkClass(true, true), 'hide');
  });
});
