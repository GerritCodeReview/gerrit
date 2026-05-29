/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './gr-user-header';
import {GrUserHeader} from './gr-user-header';
import {
  queryAndAssert,
  stubRestApi,
  waitEventLoop,
} from '../../../test/test-utils';
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

    assert.ok(element.shadowRoot!.querySelector('h1.heading-1'));
    // We can check if status is visible
    const statusDiv = queryAndAssert(element, '.status');
    assert.notInclude(statusDiv.className, 'hide');
    assert.include(statusDiv.textContent, 'OOO');

    element.userId = undefined;
    await waitEventLoop();

    // Check if status is hidden or empty
    const statusDivAfter = queryAndAssert(element, '.status');
    // It should be hidden or empty
    assert.include(statusDivAfter.className, 'hide');
  });

  test('dashboard link class', async () => {
    element.showDashboardLink = false;
    element.loggedIn = false;
    await element.updateComplete;
    assert.include(queryAndAssert(element, '.dashboardLink').className, 'hide');

    element.showDashboardLink = true;
    element.loggedIn = false;
    await element.updateComplete;
    assert.include(queryAndAssert(element, '.dashboardLink').className, 'hide');

    element.showDashboardLink = false;
    element.loggedIn = true;
    await element.updateComplete;
    assert.include(queryAndAssert(element, '.dashboardLink').className, 'hide');

    element.showDashboardLink = true;
    element.loggedIn = true;
    await element.updateComplete;
    assert.notInclude(
      queryAndAssert(element, '.dashboardLink').className,
      'hide'
    );
  });
});
