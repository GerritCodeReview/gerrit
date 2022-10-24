/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, html, assert} from '@open-wc/testing';
import {GrChangeSummary} from './gr-change-summary';
import {queryAndAssert} from '../../../utils/common-util';
import {fakeRun0} from '../../../models/checks/checks-fakes';
import {
  createAccountWithEmail,
  createComment,
  createCommentThread,
  createDraft,
} from '../../../test/test-data-generators';
import {stubFlags} from '../../../test/test-utils';
import {Timestamp} from '../../../api/rest-api';
import {testResolver} from '../../../test/common-test-setup';
import {userModelToken} from '../../../models/user/user-model';
import {commentsModelToken} from '../../../models/comments/comments-model';

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
    testResolver(commentsModelToken).setState({
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
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div>
        <table>
          <tbody>
            <tr>
              <td class="key">Comments</td>
              <td class="value">
                <gr-summary-chip
                  category="drafts"
                  icon="rate_review"
                  iconFilled
                  styletype="info"
                >
                  3 drafts
                </gr-summary-chip>
                <gr-summary-chip category="unresolved" styletype="warning">
                  <gr-avatar-stack imageSize="32">
                    <gr-icon
                      class="unresolvedIcon"
                      filled
                      icon="chat_bubble"
                      slot="fallback"
                    ></gr-icon>
                  </gr-avatar-stack>
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
      </div> `
    );
  });

  test('renders checks summary message', async () => {
    element.runs = [fakeRun0];
    element.messages = ['a message'];
    element.showChecksSummary = true;
    await element.updateComplete;
    const checksSummary = queryAndAssert(element, '.checksSummary');
    assert.dom.equal(
      checksSummary,
      /* HTML */ `
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
      `
    );
  });

  test('renders mentions summary', async () => {
    stubFlags('isEnabled').returns(true);
    // recreate element so that flag protected subscriptions are added
    element = await fixture(html`<gr-change-summary></gr-change-summary>`);
    await element.updateComplete;

    testResolver(commentsModelToken).setState({
      drafts: {
        a: [
          {
            ...createDraft(),
            message: 'Hey @abc@def.com pleae take a look at this.',
            unresolved: true,
          },
          // Resolved draft thread hence ignored
          {...createDraft(), message: 'Hey @abc@def.com this is important.'},
          createDraft(),
        ],
      },
      comments: {
        a: [
          {
            ...createComment(),
            message: 'Hey @abc@def.com pleae take a look at this.',
            unresolved: true,
          },
        ],
        b: [
          {...createComment(), message: 'Hey @abc@def.com this is important.'},
        ],
      },
      discardedDrafts: [],
    });
    testResolver(userModelToken).setAccount({
      ...createAccountWithEmail('abc@def.com'),
      registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
    });
    await element.updateComplete;
    const mentionSummary = queryAndAssert(element, '.mentionSummary');
    // Only count occurrences in unresolved threads
    // Resolved threads are ignored hence mention chip count is 2
    assert.dom.equal(
      mentionSummary,
      /* HTML */ `
        <gr-summary-chip
          category="mentions"
          class="mentionSummary"
          icon="alternate_email"
          styletype="warning"
        >
          2 mentions
        </gr-summary-chip>
      `
    );
  });
});
