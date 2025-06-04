/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {GrChangeSummary} from './gr-change-summary';
import {queryAll, queryAndAssert} from '../../../utils/common-util';
import {fakeRun0} from '../../../models/checks/checks-fakes';
import {
  createAccountWithEmail,
  createCheckResult,
  createComment,
  createCommentThread,
  createDraft,
  createRun,
} from '../../../test/test-data-generators';
import {Timestamp} from '../../../api/rest-api';
import {testResolver} from '../../../test/common-test-setup';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';
import {GrChecksChip} from './gr-checks-chip';
import {CheckRun} from '../../../models/checks/checks-model';
import {Category, RunStatus} from '../../../api/checks';

suite('gr-change-summary test', () => {
  let element: GrChangeSummary;
  let commentsModel: CommentsModel;
  let userModel: UserModel;

  setup(async () => {
    element = await fixture(html`<gr-change-summary></gr-change-summary>`);
    commentsModel = testResolver(commentsModelToken);
    userModel = testResolver(userModelToken);
  });

  test('is defined', () => {
    const el = document.createElement('gr-change-summary');
    assert.instanceOf(el, GrChangeSummary);
  });

  test('renders', async () => {
    commentsModel.setState({
      drafts: {
        a: [createDraft(), createDraft(), createDraft()],
      },
      discardedDrafts: [],
    });
    element.commentsLoading = false;
    element.commentThreads = [
      createCommentThread([createComment()]),
      createCommentThread([{...createComment(), unresolved: true}]),
    ];
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div>
        <table class="info">
          <tbody>
            <tr>
              <td class="key">Comments</td>
              <td class="value">
                <div class="value-content">
                  <gr-comments-summary
                    clickablechips=""
                    showcommentcategoryname=""
                  ></gr-comments-summary>
                </div>
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
              <gr-formatted-text class="message"></gr-formatted-text>
            </div>
          </div>
        </div>
      `
    );
  });

  suite('checks summary', () => {
    const checkSummary = async (runs: CheckRun[], texts: string[]) => {
      element.runs = runs;
      element.showChecksSummary = true;
      await element.updateComplete;
      const chips = queryAll<GrChecksChip>(element, 'gr-checks-chip') ?? [];
      assert.deepEqual(
        [...chips].map(c => `${c.statusOrCategory} ${c.text}`),
        texts
      );
    };

    test('single success', async () => {
      checkSummary([createRun()], ['SUCCESS test-name']);
    });

    test('single running', async () => {
      checkSummary(
        [createRun({status: RunStatus.RUNNING})],
        ['RUNNING test-name']
      );
    });

    test('single info', async () => {
      checkSummary(
        [
          createRun({
            status: RunStatus.COMPLETED,
            results: [createCheckResult({category: Category.INFO})],
          }),
        ],
        ['INFO test-name']
      );
    });

    test('single of each collapses INFO and SUCCESS', async () => {
      checkSummary(
        [
          createRun({status: RunStatus.RUNNING}),
          createRun({
            status: RunStatus.COMPLETED,
            results: [createCheckResult({category: Category.SUCCESS})],
          }),
          createRun({
            status: RunStatus.COMPLETED,
            results: [createCheckResult({category: Category.INFO})],
          }),
          createRun({
            status: RunStatus.COMPLETED,
            results: [createCheckResult({category: Category.WARNING})],
          }),
          createRun({
            status: RunStatus.COMPLETED,
            results: [createCheckResult({category: Category.ERROR})],
          }),
        ],
        [
          'ERROR test-name',
          'WARNING test-name',
          'INFO 1',
          'SUCCESS 1',
          'RUNNING test-name',
        ]
      );
    });
  });

  test('renders mentions summary', async () => {
    commentsModel.setState({
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
    userModel.setAccount({
      ...createAccountWithEmail('abc@def.com'),
      registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
    });
    await element.updateComplete;
    const commentsSummary = queryAndAssert(element, 'gr-comments-summary');
    const mentionSummary = queryAndAssert(commentsSummary, '.mentionSummary');
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
