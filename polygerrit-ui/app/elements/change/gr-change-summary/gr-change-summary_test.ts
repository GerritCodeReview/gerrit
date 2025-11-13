/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {GrChangeSummary} from './gr-change-summary';
import {queryAll, queryAndAssert} from '../../../utils/common-util';
import {checkRun0} from '../../../test/test-data-generators';
import {
  createAccountWithEmail,
  createCheckResult,
  createComment,
  createCommentThread,
  createDraft,
  createRun,
} from '../../../test/test-data-generators';
import {FlowInfo, FlowStageState, Timestamp} from '../../../api/rest-api';
import {testResolver} from '../../../test/common-test-setup';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';
import {GrChecksChip} from './gr-checks-chip';
import {CheckRun} from '../../../models/checks/checks-model';
import {Category, RunStatus} from '../../../api/checks';
import {FlowsModel, flowsModelToken} from '../../../models/flows/flows-model';

function createFlow(partial: Partial<FlowInfo> = {}): FlowInfo {
  return {
    uuid: 'test-uuid',
    owner: createAccountWithEmail(),
    created: '2020-01-01 00:00:00.000000000' as Timestamp,
    stages: [],
    ...partial,
  };
}

suite('gr-change-summary test', () => {
  let element: GrChangeSummary;
  let commentsModel: CommentsModel;
  let userModel: UserModel;
  let flowsModel: FlowsModel;

  setup(async () => {
    element = await fixture(html`<gr-change-summary></gr-change-summary>`);
    commentsModel = testResolver(commentsModelToken);
    userModel = testResolver(userModelToken);
    flowsModel = testResolver(flowsModelToken);
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
      /* HTML */ `
        <div>
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
                    <gr-button
                      aria-disabled="false"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Create AI Review Prompt
                    </gr-button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <dialog id="aiPromptModal" tabindex="-1">
          <gr-ai-prompt-dialog id="aiPromptDialog" role="dialog">
          </gr-ai-prompt-dialog>
        </dialog>
      `
    );
  });

  test('renders checks summary message', async () => {
    element.runs = [checkRun0];
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

  suite('flows summary', () => {
    test('renders', async () => {
      flowsModel.setState({
        flows: [
          createFlow({
            stages: [
              {expression: {condition: ''}, state: FlowStageState.PENDING},
            ],
          }),
          createFlow({
            stages: [{expression: {condition: ''}, state: FlowStageState.DONE}],
          }),
          createFlow({
            stages: [{expression: {condition: ''}, state: FlowStageState.DONE}],
          }),
          createFlow({
            stages: [
              {expression: {condition: ''}, state: FlowStageState.FAILED},
            ],
          }),
          createFlow({
            stages: [
              {expression: {condition: ''}, state: FlowStageState.FAILED},
            ],
          }),
          createFlow({
            stages: [
              {expression: {condition: ''}, state: FlowStageState.FAILED},
            ],
          }),
        ],
        loading: false,
        isEnabled: true,
      });
      await element.updateComplete;
      const flowsSummary = queryAndAssert(element, '.flowsSummary');
      assert.dom.equal(
        flowsSummary,
        /* HTML */ `
          <div class="flowsSummary">
            <gr-checks-chip> </gr-checks-chip>
            <gr-checks-chip> </gr-checks-chip>
            <gr-checks-chip> </gr-checks-chip>
          </div>
        `
      );
      const chips = queryAll<GrChecksChip>(element, 'gr-checks-chip');
      assert.equal(chips.length, 3);
      assert.equal(chips[0].statusOrCategory, Category.ERROR);
      assert.equal(chips[0].text, '3');
      assert.equal(chips[1].statusOrCategory, RunStatus.RUNNING);
      assert.equal(chips[1].text, '1');
      assert.equal(chips[2].statusOrCategory, Category.SUCCESS);
      assert.equal(chips[2].text, '2');
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
