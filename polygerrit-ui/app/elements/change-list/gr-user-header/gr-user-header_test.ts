/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './gr-user-header';
import {GrUserHeader} from './gr-user-header';
import {stubRestApi, waitEventLoop} from '../../../test/test-utils';
import {AccountId, EmailAddress, Timestamp} from '../../../types/common';

suite('gr-user-header tests', () => {
  let element: GrUserHeader;

  setup(async () => {
    element = await fixture(html`<gr-user-header></gr-user-header>`);
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-avatar aria-label="Account avatar" hidden=""> </gr-avatar>
        <div class="info">
          <h1 class="heading-1"></h1>
          <hr />
          <div class="hide status">
            <span> Status: </span>
          </div>
          <div>
            <span> Email: </span>
            <a href="mailto:"> </a>
          </div>
          <div>
            <span> Joined: </span>
            <gr-date-formatter datestr=""> </gr-date-formatter>
          </div>
          <gr-endpoint-decorator name="user-header">
            <gr-endpoint-param name="accountDetails"> </gr-endpoint-param>
            <gr-endpoint-param name="loggedIn"> </gr-endpoint-param>
          </gr-endpoint-decorator>
        </div>
        <div class="info">
          <div class="dashboardLink hide">
            <a href=""> View dashboard </a>
          </div>
        </div>
      `
    );
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
    await waitEventLoop();

    assert.isOk(element._accountDetails);
    assert.isOk(element._status);

    element.userId = undefined;
    await waitEventLoop();

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
