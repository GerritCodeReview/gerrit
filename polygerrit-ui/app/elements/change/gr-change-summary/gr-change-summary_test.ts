/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrChangeSummary} from './gr-change-summary';
import {queryAndAssert} from '../../../utils/common-util';
import {fakeRun0} from '../../../models/checks/checks-fakes';

suite('gr-change-summary test', () => {
  let element: GrChangeSummary;
  setup(async () => {
    element = await fixture(html`<gr-change-summary></gr-change-summary>`);
  });

  test('is defined', () => {
    const el = document.createElement('gr-change-summary');
    assert.instanceOf(el, GrChangeSummary);
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div>
      <table>
        <tbody>
          <tr>
            <td class="key">Comments</td>
            <td class="value">
              <span class="zeroState"> No comments </span>
              <gr-summary-chip
                category="drafts"
                hidden=""
                icon="edit"
                styletype="warning"
              >
              </gr-summary-chip>
              <gr-summary-chip
                category="unresolved"
                hidden=""
                styletype="warning"
              >
                0 unresolved
              </gr-summary-chip>
              <gr-summary-chip
                category="show all"
                hidden=""
                icon="markChatRead"
                styletype="check"
              >
                0 resolved
              </gr-summary-chip>
            </td>
          </tr>
          <tr hidden="">
            <td class="key">Checks</td>
            <td class="value">
              <div class="checksSummary">
                <span class="loading zeroState" role="status">
                  No results
                </span>
              </div>
            </td>
          </tr>
          <tr hidden="">
            <td class="key">Findings</td>
            <td class="value"></td>
          </tr>
        </tbody>
      </table>
    </div>`);
  });

  test('renders checks summary', async () => {
    element.runs = [fakeRun0];
    await element.updateComplete;
    const checksSummary = queryAndAssert(element, '.checksSummary');
    expect(checksSummary).dom.to.equal(/* HTML */ `
      <div class="checksSummary">
        <gr-checks-chip> </gr-checks-chip>
      </div>
    `);
  });

  test('renders checks summary message', async () => {
    element.runs = [fakeRun0];
    element.messages = ['a message'];
    element.showChecksSummary = true;
    await element.updateComplete;
    const checksSummary = queryAndAssert(element, '.checksSummary');
    expect(checksSummary).dom.to.equal(/* HTML */ `
      <div class="checksSummary">
        <gr-checks-chip> </gr-checks-chip>
        <div class="info">
          <div class="left">
            <span class="material-icon filled">info</span>
          </div>
          <div class="right">
            <div class="message" title="a message">a message</div>
          </div>
        </div>
      </div>
    `);
  });
});
