/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html, assert} from '@open-wc/testing';
import {SinonStubbedMember} from 'sinon';
import {
  AccountInfo,
  GroupId,
  GroupInfo,
  GroupName,
  ReviewerState,
} from '../../../api/rest-api';
import {
  BulkActionsModel,
  bulkActionsModelToken,
} from '../../../models/bulk-actions/bulk-actions-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import '../../../test/common-test-setup';
import {
  createAccountWithIdNameAndEmail,
  createChange,
  createGroupInfo,
} from '../../../test/test-data-generators';
import {
  MockPromise,
  mockPromise,
  queryAndAssert,
  stubReporting,
  stubRestApi,
  waitUntil,
  waitUntilObserved,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId} from '../../../types/common';
import {ValueChangedEvent} from '../../../types/events';
import {query} from '../../../utils/common-util';
import {GrAccountList} from '../../shared/gr-account-list/gr-account-list';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import './gr-change-list-reviewer-flow';
import type {GrChangeListReviewerFlow} from './gr-change-list-reviewer-flow';

const accounts: AccountInfo[] = [
  createAccountWithIdNameAndEmail(0),
  createAccountWithIdNameAndEmail(1),
  createAccountWithIdNameAndEmail(2),
  createAccountWithIdNameAndEmail(3),
  createAccountWithIdNameAndEmail(4),
  createAccountWithIdNameAndEmail(5),
  createAccountWithIdNameAndEmail(6),
];
const groups: GroupInfo[] = [
  {...createGroupInfo('groupId'), name: 'Group 0' as GroupName},
];
const changes: ChangeInfo[] = [
  {
    ...createChange(),
    _number: 1 as NumericChangeId,
    subject: 'Subject 1',
    owner: accounts[6],
    reviewers: {
      REVIEWER: [accounts[0], accounts[1], accounts[6]],
      CC: [accounts[3], accounts[4]],
    },
  },
  {
    ...createChange(),
    _number: 2 as NumericChangeId,
    subject: 'Subject 2',
    owner: accounts[6],
    reviewers: {REVIEWER: [accounts[0], accounts[6]], CC: [accounts[3]]},
  },
];

suite('gr-change-list-reviewer-flow tests', () => {
  let element: GrChangeListReviewerFlow;
  let model: BulkActionsModel;
  let reportingStub: SinonStubbedMember<ReportingService['reportInteraction']>;

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChanges$, selected =>
      selected.some(other => other._number === change._number)
    );
    await element.updateComplete;
  }

  setup(async () => {
    stubRestApi('getDetailedChangesWithActions').resolves(changes);
    reportingStub = stubReporting('reportInteraction');
    model = new BulkActionsModel(getAppContext().restApiService);
    model.sync(changes);

    element = (
      await fixture(
        wrapInProvider(
          html`<gr-change-list-reviewer-flow></gr-change-list-reviewer-flow>`,
          bulkActionsModelToken,
          model
        )
      )
    ).querySelector('gr-change-list-reviewer-flow')!;
    await selectChange(changes[0]);
    await selectChange(changes[1]);
    await waitUntilObserved(model.selectedChanges$, s => s.length === 2);
    await element.updateComplete;
  });

  test('skips dialog render when closed', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-button
          id="start-flow"
          flatten=""
          aria-disabled="false"
          role="button"
          tabindex="0"
          >add reviewer/cc</gr-button
        >
        <dialog id="flow" tabindex="-1"></dialog>
      `
    );
  });

  test('flow button enabled when changes selected', async () => {
    const button = queryAndAssert<GrButton>(element, 'gr-button#start-flow');
    assert.isFalse(button.disabled);
  });

  test('flow button disabled when no changes selected', async () => {
    model.clearSelectedChangeNums();
    await waitUntilObserved(model.selectedChanges$, s => s.length === 0);
    await element.updateComplete;

    const button = queryAndAssert<GrButton>(element, 'gr-button#start-flow');
    assert.isTrue(button.disabled);
  });

  test('overlay hidden before flow button clicked', async () => {
    const dialog = queryAndAssert<HTMLDialogElement>(element, 'dialog');
    const openStub = sinon.stub(dialog, 'showModal');
    assert.isFalse(openStub.called);
  });

  test('flow button click shows overlay', async () => {
    const button = queryAndAssert<GrButton>(element, 'gr-button#start-flow');
    const dialog = queryAndAssert<HTMLDialogElement>(element, 'dialog');
    const openStub = sinon.stub(dialog, 'showModal');

    button.click();

    await element.updateComplete;

    assert.isTrue(openStub.called);
  });

  suite('dialog flow', () => {
    let saveChangesPromises: MockPromise<Response>[];
    let saveChangeReviewStub: sinon.SinonStub;
    let dialog: GrDialog;

    async function resolvePromises() {
      saveChangesPromises[0].resolve(new Response());
      saveChangesPromises[1].resolve(new Response());
      await element.updateComplete;
    }

    setup(async () => {
      saveChangesPromises = [];
      saveChangeReviewStub = stubRestApi('saveChangeReview');
      for (let i = 0; i < changes.length; i++) {
        const promise = mockPromise<Response>();
        saveChangesPromises.push(promise);
        saveChangeReviewStub
          .withArgs(changes[i]._number, sinon.match.any, sinon.match.any)
          .returns(promise);
      }

      queryAndAssert<GrButton>(element, 'gr-button#start-flow').click();
      await element.updateComplete;
      dialog = queryAndAssert<GrDialog>(element, 'gr-dialog');
      await dialog.updateComplete;
    });

    test('renders dialog when opened', async () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-button
            id="start-flow"
            flatten=""
            aria-disabled="false"
            role="button"
            tabindex="0"
            >add reviewer/cc</gr-button
          >
          <dialog id="flow" open="" tabindex="-1">
            <gr-dialog role="dialog">
              <div slot="header">Add reviewer / CC</div>
              <div slot="main">
                <div class="grid">
                  <span>Reviewers</span>
                  <gr-account-list id="reviewer-list"></gr-account-list>
                  <dialog id="confirm-reviewer" tabindex="-1">
                    <div class="confirmation-text">
                      Group
                      <span class="groupName"></span>
                      has
                      <span class="groupSize"></span>
                      members.
                      <br />
                      Are you sure you want to add them all?
                    </div>
                    <div class="confirmation-buttons">
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0"
                      >
                        Yes
                      </gr-button>
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0"
                      >
                        No
                      </gr-button>
                    </div>
                  </dialog>
                  <span>CC</span>
                  <gr-account-list id="cc-list"></gr-account-list>
                  <dialog id="confirm-cc" tabindex="-1">
                    <div class="confirmation-text">
                      Group
                      <span class="groupName"></span>
                      has
                      <span class="groupSize"></span>
                      members.
                      <br />
                      Are you sure you want to add them all?
                    </div>
                    <div class="confirmation-buttons">
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0"
                      >
                        Yes
                      </gr-button>
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0"
                      >
                        No
                      </gr-button>
                    </div>
                  </dialog>
                </div>
              </div>
            </gr-dialog>
          </dialog>
        `
      );
    });

    test('only lists reviewers/CCs shared by all changes', async () => {
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );
      const ccList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#cc-list'
      );
      // does not include account 1 because it is not shared, does not include
      // account 6 because it is the owner
      assert.sameMembers(reviewerList.accounts, [accounts[0]]);
      // does not include account 4
      assert.sameMembers(ccList.accounts, [accounts[3]]);
    });

    test('adds reviewer & CC', async () => {
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );
      const ccList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#cc-list'
      );
      reviewerList.accounts.push(accounts[2], groups[0]);
      ccList.accounts.push(accounts[5]);

      assert.isFalse(dialog.loading);

      await element.updateComplete;
      dialog.confirmButton!.click();
      await element.updateComplete;

      assert.isTrue(dialog.loading);
      assert.equal(
        dialog.loadingLabel,
        'Adding Reviewer and CC in progress...'
      );

      assert.deepEqual(reportingStub.lastCall.args[1], {
        type: 'add-reviewer',
        selectedChangeCount: 2,
      });

      assert.isTrue(saveChangeReviewStub.calledTwice);
      assert.sameDeepOrderedMembers(saveChangeReviewStub.firstCall.args, [
        changes[0]._number,
        'current',
        {
          reviewers: [
            {reviewer: accounts[2]._account_id, state: ReviewerState.REVIEWER},
            {reviewer: groups[0].id, state: ReviewerState.REVIEWER},
            {reviewer: accounts[5]._account_id, state: ReviewerState.CC},
          ],
          ignore_automatic_attention_set_rules: true,
          // only the reviewer is added to the attention set, not the cc
          add_to_attention_set: [
            {
              reason: '<GERRIT_ACCOUNT_1> replied on the change',
              user: accounts[2]._account_id,
            },
            {
              reason: '<GERRIT_ACCOUNT_1> replied on the change',
              user: groups[0].id,
            },
          ],
        },
      ]);
      assert.sameDeepOrderedMembers(saveChangeReviewStub.secondCall.args, [
        changes[1]._number,
        'current',
        {
          reviewers: [
            {reviewer: accounts[2]._account_id, state: ReviewerState.REVIEWER},
            {reviewer: groups[0].id, state: ReviewerState.REVIEWER},
            {reviewer: accounts[5]._account_id, state: ReviewerState.CC},
          ],
          ignore_automatic_attention_set_rules: true,
          // only the reviewer is added to the attention set, not the cc
          add_to_attention_set: [
            {
              reason: '<GERRIT_ACCOUNT_1> replied on the change',
              user: accounts[2]._account_id,
            },
            {
              reason: '<GERRIT_ACCOUNT_1> replied on the change',
              user: groups[0].id,
            },
          ],
        },
      ]);
    });

    test('removes from reviewer list when added to cc', async () => {
      const ccList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#cc-list'
      );
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );
      assert.sameOrderedMembers(reviewerList.accounts, [accounts[0]]);

      ccList.handleAdd(
        new CustomEvent('add', {
          detail: {
            value: {
              account: accounts[0],
              count: 1,
            },
          },
        }) as unknown as ValueChangedEvent<string>
      );
      await element.updateComplete;

      assert.isEmpty(reviewerList.accounts);
    });

    test('removes from cc list when added to reviewer', async () => {
      const ccList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#cc-list'
      );
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );
      assert.sameOrderedMembers(ccList.accounts, [accounts[3]]);

      reviewerList.handleAdd(
        new CustomEvent('add', {
          detail: {
            value: {
              account: accounts[3],
              count: 1,
            },
          },
        }) as unknown as ValueChangedEvent<string>
      );
      await element.updateComplete;

      assert.isEmpty(ccList.accounts);
    });

    suite('success toasts', () => {
      test('reviewer only', async () => {
        const dispatchEventStub = sinon.stub(element, 'dispatchEvent');
        const existingReviewers = element.getCurrentAccounts(
          ReviewerState.REVIEWER
        );
        const getCurrentAccountsStub = sinon.stub(
          element,
          'getCurrentAccounts'
        );
        getCurrentAccountsStub.withArgs(ReviewerState.CC).returns([]);
        getCurrentAccountsStub
          .withArgs(ReviewerState.REVIEWER)
          .returns(existingReviewers);
        const reviewerList = queryAndAssert<GrAccountList>(
          dialog,
          'gr-account-list#reviewer-list'
        );
        reviewerList.accounts.push(accounts[2], groups[0]);
        element.updatedAccountsByReviewerState.set(ReviewerState.CC, []);
        await element.updateComplete;
        dialog.confirmButton!.click();

        await resolvePromises();
        await element.updateComplete;

        await waitUntil(
          () => dispatchEventStub.callCount > 0,
          'dispatchEventStub never called'
        );

        assert.equal(
          (dispatchEventStub.firstCall.args[0] as CustomEvent).detail.message,
          '2 reviewers added'
        );
      });

      test('ccs only', async () => {
        const dispatchEventStub = sinon.stub(element, 'dispatchEvent');
        const existingCCs = element.getCurrentAccounts(ReviewerState.CC);
        const getCurrentAccountsStub = sinon.stub(
          element,
          'getCurrentAccounts'
        );
        getCurrentAccountsStub.withArgs(ReviewerState.CC).returns(existingCCs);
        getCurrentAccountsStub.withArgs(ReviewerState.REVIEWER).returns([]);
        const ccsList = queryAndAssert<GrAccountList>(
          dialog,
          'gr-account-list#cc-list'
        );
        ccsList.accounts.push(accounts[2], groups[0]);
        ccsList.accounts = [];
        element.updatedAccountsByReviewerState.set(ReviewerState.REVIEWER, []);
        await element.updateComplete;
        dialog.confirmButton!.click();

        await resolvePromises();
        await element.updateComplete;

        await waitUntil(
          () => dispatchEventStub.callCount > 0,
          'dispatchEventStub never called'
        );

        assert.equal(
          (dispatchEventStub.firstCall.args[0] as CustomEvent).detail.message,
          '2 CCs added'
        );
      });

      test('reviewers and CC', async () => {
        const dispatchEventStub = sinon.stub(element, 'dispatchEvent');
        const existingReviewers = element.getCurrentAccounts(
          ReviewerState.REVIEWER
        );
        const existingCCs = element.getCurrentAccounts(ReviewerState.CC);
        const getCurrentAccountsStub = sinon.stub(
          element,
          'getCurrentAccounts'
        );
        getCurrentAccountsStub.withArgs(ReviewerState.CC).returns(existingCCs);
        getCurrentAccountsStub
          .withArgs(ReviewerState.REVIEWER)
          .returns(existingReviewers);

        const reviewerList = queryAndAssert<GrAccountList>(
          dialog,
          'gr-account-list#reviewer-list'
        );
        const ccsList = queryAndAssert<GrAccountList>(
          dialog,
          'gr-account-list#cc-list'
        );

        reviewerList.accounts.push(accounts[2], groups[0]);
        ccsList.accounts.push(accounts[2], groups[0]);
        await element.updateComplete;
        dialog.confirmButton!.click();

        await resolvePromises();
        await element.updateComplete;

        await waitUntil(
          () => dispatchEventStub.callCount > 0,
          'dispatchEventStub never called'
        );

        assert.equal(
          (dispatchEventStub.firstCall.args[0] as CustomEvent).detail.message,
          '2 reviewers and 2 CCs added'
        );
      });
    });

    test('reloads page on success', async () => {
      const dispatchEventStub = sinon.stub(element, 'dispatchEvent');
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );

      reviewerList.accounts.push(accounts[2], groups[0]);
      await element.updateComplete;
      dialog.confirmButton!.click();
      await element.updateComplete;

      await resolvePromises();
      await element.updateComplete;

      await waitUntil(
        () => dispatchEventStub.callCount > 0,
        'dispatchEventStub never called'
      );

      assert.isTrue(dispatchEventStub.calledTwice);
      assert.equal(dispatchEventStub.secondCall.args[0].type, 'reload');
    });

    test('does not reload page on failure', async () => {
      const dispatchEventStub = sinon.stub(element, 'dispatchEvent');
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );

      reviewerList.accounts.push(accounts[2], groups[0]);
      await element.updateComplete;
      dialog.confirmButton!.click();
      await element.updateComplete;
      saveChangesPromises[0].reject(new Error('failed!'));
      saveChangesPromises[1].reject(new Error('failed!'));
      await element.updateComplete;

      await waitUntil(
        () => reportingStub.calledWith('bulk-action-failure'),
        'reporting stub never called'
      );

      assert.deepEqual(reportingStub.lastCall.args, [
        'bulk-action-failure',
        {
          type: 'add-reviewer',
          count: 2,
        },
      ]);
      assert.isTrue(dispatchEventStub.notCalled);
    });

    test('renders warnings when reviewer/cc are overwritten', async () => {
      const ccList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#cc-list'
      );
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );

      reviewerList.handleAdd(
        new CustomEvent('add', {
          detail: {
            value: {
              account: accounts[4],
              count: 1,
            },
          },
        }) as unknown as ValueChangedEvent<string>
      );
      ccList.handleAdd(
        new CustomEvent('add', {
          detail: {
            value: {
              account: accounts[1],
              count: 1,
            },
          },
        }) as unknown as ValueChangedEvent<string>
      );
      await element.updateComplete;

      // prettier and shadowDom string don't agree on the long text in divs
      assert.shadowDom.equal(
        element,
        /* prettier-ignore */
        /* HTML */ `
          <gr-button
            id="start-flow"
            flatten=""
            aria-disabled="false"
            role="button"
            tabindex="0"
            >add reviewer/cc</gr-button
          >
          <dialog id="flow" open="" tabindex="-1">
            <gr-dialog role="dialog">
              <div slot="header">Add reviewer / CC</div>
              <div slot="main">
                <div class="grid">
                  <span>Reviewers</span>
                  <gr-account-list id="reviewer-list"></gr-account-list>
                  <dialog tabindex="-1" id="confirm-reviewer">
                    <div class="confirmation-text">
                      Group
                      <span class="groupName"></span>
                      has
                      <span class="groupSize"></span>
                      members.
                      <br>
                      Are you sure you want to add them all?
                    </div>
                    <div class="confirmation-buttons">
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0">
                        Yes
                      </gr-button>
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0">
                        No
                      </gr-button>
                    </div>
                  </dialog>
                  <span>CC</span>
                  <gr-account-list id="cc-list"></gr-account-list>
                  <dialog tabindex="-1" id="confirm-cc">
                    <div class="confirmation-text">
                      Group
                      <span class="groupName"></span>
                      has
                      <span class="groupSize"></span>
                      members.
                      <br>
                      Are you sure you want to add them all?
                    </div>
                    <div class="confirmation-buttons">
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0">
                        Yes
                      </gr-button>
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0">
                        No
                      </gr-button>
                    </div>
                  </dialog>
                </div>
                <div class="warning">
                  <gr-icon icon="warning" filled role="img" aria-label="Warning"
                  ></gr-icon>
                  User-1 is a reviewer
        on some selected changes and will be moved to CC on all
        changes.
                </div>
                <div class="warning">
                  <gr-icon icon="warning" filled role="img" aria-label="Warning"
                  ></gr-icon>
                  User-4 is a CC
        on some selected changes and will be moved to reviewer on all
        changes.
                </div>
              </div>
            </gr-dialog>
          </dialog>
        `,
        {
          // dialog sizing seems to vary between local & CI
          ignoreAttributes: [{tags: ['dialog'], attributes: ['style']}],
        }
      );
    });

    test('renders errors when requests fail', async () => {
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );

      reviewerList.accounts.push(accounts[2], groups[0]);
      await element.updateComplete;
      dialog.confirmButton!.click();
      await element.updateComplete;
      saveChangesPromises[0].reject(new Error('failed!'));
      saveChangesPromises[1].reject(new Error('failed!'));

      await waitUntil(() => !!query(dialog, '.error'));

      // prettier and shadowDom string don't agree on the long text in divs
      assert.shadowDom.equal(
        element,
        /* prettier-ignore */
        /* HTML */ `
          <gr-button
            aria-disabled="false"
            flatten=""
            id="start-flow"
            role="button"
            tabindex="0"
          >
            add reviewer/cc
          </gr-button>
          <dialog
            id="flow"
            tabindex="-1"
            open=""
          >
            <gr-dialog role="dialog">
              <div slot="header">Add reviewer / CC</div>
              <div slot="main">
                <div class="grid">
                  <span> Reviewers </span>
                  <gr-account-list id="reviewer-list"> </gr-account-list>
                  <dialog tabindex="-1" id="confirm-reviewer">
                    <div class="confirmation-text">
                      Group
                      <span class="groupName"> </span>
                      has
                      <span class="groupSize"> </span>
                      members.
                      <br />
                      Are you sure you want to add them all?
                    </div>
                    <div class="confirmation-buttons">
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0"
                      >
                        Yes
                      </gr-button>
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0"
                      >
                        No
                      </gr-button>
                    </div>
                  </dialog>
                  <span> CC </span>
                  <gr-account-list id="cc-list"> </gr-account-list>
                  <dialog tabindex="-1" id="confirm-cc">
                    <div class="confirmation-text">
                      Group
                      <span class="groupName"> </span>
                      has
                      <span class="groupSize"> </span>
                      members.
                      <br />
                      Are you sure you want to add them all?
                    </div>
                    <div class="confirmation-buttons">
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0"
                      >
                        Yes
                      </gr-button>
                      <gr-button
                        aria-disabled="false"
                        role="button"
                        tabindex="0"
                      >
                        No
                      </gr-button>
                    </div>
                  </dialog>
                </div>
                <div class="error">
                  <gr-icon icon="error" filled role="img" aria-label="Error"></gr-icon>
                  Failed to add User-0, User-2, Group 0, and User-3 to changes.
                </div>
              </div>
            </gr-dialog>
          </dialog>
        `,
        {
          // dialog sizing seems to vary between local & CI
          ignoreAttributes: [{tags: ['dialog'], attributes: ['style']}],
        }
      );
    });

    test('shows confirmation dialog when large group is added', async () => {
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );
      reviewerList.handleAdd(
        new CustomEvent('add', {
          detail: {
            value: {
              group: {
                id: '5',
                name: 'large-group',
              },
              count: 12,
              confirm: true,
            },
          },
        }) as unknown as ValueChangedEvent<string>
      );
      // reviewerList needs to set the pendingConfirmation property which
      // triggers an update of ReviewerFlow
      await reviewerList.updateComplete;
      await element.updateComplete;

      const confirmDialog = queryAndAssert(element, 'dialog#confirm-reviewer');
      await waitUntil(
        () =>
          getComputedStyle(confirmDialog).getPropertyValue('display') !== 'none'
      );
    });

    test('"yes" button confirms large group', async () => {
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );
      reviewerList.handleAdd(
        new CustomEvent('add', {
          detail: {
            value: {
              group: {
                id: '5',
                name: 'large-group',
              },
              count: 12,
              confirm: true,
            },
          },
        }) as unknown as ValueChangedEvent<string>
      );
      // reviewerList needs to set the pendingConfirmation property which
      // triggers an update of ReviewerFlow
      await reviewerList.updateComplete;
      await element.updateComplete;
      // "Yes" button is first
      queryAndAssert<GrButton>(
        element,
        '.confirmation-buttons > gr-button:first-of-type'
      ).click();
      await element.updateComplete;

      const confirmDialog = queryAndAssert(element, 'dialog#confirm-reviewer');
      await waitUntil(
        () =>
          getComputedStyle(confirmDialog).getPropertyValue('display') === 'none'
      );

      assert.deepEqual(reviewerList.accounts[1], {
        confirmed: true,
        id: '5' as GroupId,
        name: 'large-group',
      });
    });

    test('confirmation dialog skipped for small group', async () => {
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );
      // "confirm" field is used to decide whether to use the confirmation flow,
      // not the count. "confirm" value comes from server based on count
      // threshold
      reviewerList.handleAdd(
        new CustomEvent('add', {
          detail: {
            value: {
              group: {
                id: '5',
                name: 'small-group',
              },
              count: 2,
              confirm: false,
            },
          },
        }) as unknown as ValueChangedEvent<string>
      );
      // reviewerList needs to set the pendingConfirmation property which
      // triggers an update of ReviewerFlow
      await reviewerList.updateComplete;
      await element.updateComplete;
      const confirmDialog = queryAndAssert(element, 'dialog#confirm-reviewer');
      assert.isTrue(
        getComputedStyle(confirmDialog).getPropertyValue('display') === 'none'
      );
      assert.deepEqual(reviewerList.accounts[1], {
        id: '5' as GroupId,
        name: 'small-group',
      });
    });

    test('"no" button cancels large group', async () => {
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );
      reviewerList.handleAdd(
        new CustomEvent('add', {
          detail: {
            value: {
              group: {
                id: '5',
                name: 'large-group',
              },
              count: 12,
              confirm: true,
            },
          },
        }) as unknown as ValueChangedEvent<string>
      );
      // reviewerList needs to set the pendingConfirmation property which
      // triggers an update of ReviewerFlow
      await reviewerList.updateComplete;
      await element.updateComplete;
      // "No" button is last
      queryAndAssert<GrButton>(
        element,
        '.confirmation-buttons > gr-button:last-of-type'
      ).click();
      await element.updateComplete;

      const confirmDialog = queryAndAssert(element, 'dialog#confirm-reviewer');
      assert.isTrue(
        getComputedStyle(confirmDialog).getPropertyValue('display') === 'none'
      );
      // Group not present
      assert.sameDeepMembers(reviewerList.accounts, [accounts[0]]);
    });
  });
});
