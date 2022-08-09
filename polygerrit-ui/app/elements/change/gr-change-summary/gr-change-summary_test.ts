/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrChangeSummary} from './gr-change-summary';
import {query, queryAndAssert} from '../../../utils/common-util';
import {fakeRun0} from '../../../models/checks/checks-fakes';
import {
  createComment,
  createCommentThread,
  createDraft,
} from '../../../test/test-data-generators';

suite('gr-change-summary test', () => {
  let element: GrChangeSummary;
  setup(async () => {
    element = await fixture(html`<gr-change-summary></gr-change-summary>`);
  });

  test('is defined', () => {
    const el = document.createElement('gr-change-summary');
    assert.instanceOf(el, GrChangeSummary);
  });

  test('renders', async () => {
    element.getCommentsModel().setState({
      drafts: {
        a: [createDraft(), createDraft(), createDraft()],
      },
      discardedDrafts: [],
    });
    element.commentThreads = [
      createCommentThread([createComment()]),
      createCommentThread([{...createComment(), unresolved: true}]),
    ];
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `<div>
      <table>
        <tbody>
          <tr>
            <td class="key">Comments</td>
            <td class="value">
              <gr-summary-chip
                category="drafts"
                icon="edit"
                styletype="warning"
              >
                3 drafts
              </gr-summary-chip>
              <gr-summary-chip category="unresolved" styletype="warning">
                1 unresolved
              </gr-summary-chip>
              <gr-summary-chip
                category="show all"
                icon="mark_chat_read"
                styletype="check"
              >
                1 resolved
              </gr-summary-chip>
            </td>
          </tr>
        </tbody>
      </table>
    </div> `);
  });

  test('renders checks summary', async () => {
    element.runs = [fakeRun0];
    await element.updateComplete;
    assert.isNotOk(query(element, '.checksSummary'));
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
            <gr-icon icon="info" filled></gr-icon>
          </div>
          <div class="right">
            <div class="message" title="a message">a message</div>
          </div>
        </div>
      </div>
    `);
  });

  test('renders mentions summary', async () => {
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
            <gr-icon icon="info" filled></gr-icon>
          </div>
          <div class="right">
            <div class="message" title="a message">a message</div>
          </div>
        </div>
      </div>
    `);
  });
});
