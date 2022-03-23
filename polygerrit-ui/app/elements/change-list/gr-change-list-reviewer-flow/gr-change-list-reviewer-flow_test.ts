/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html} from '@open-wc/testing-helpers';
import {AccountInfo, ReviewerState} from '../../../api/rest-api';
import {
  BulkActionsModel,
  bulkActionsModelToken,
} from '../../../models/bulk-actions/bulk-actions-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import '../../../test/common-test-setup-karma';
import {
  createAccountWithIdNameAndEmail,
  createChange,
} from '../../../test/test-data-generators';
import {
  MockPromise,
  mockPromise,
  queryAndAssert,
  stubRestApi,
  waitUntilObserved,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId} from '../../../types/common';
import {GrAccountList} from '../../shared/gr-account-list/gr-account-list';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import './gr-change-list-reviewer-flow';
import type {GrChangeListReviewerFlow} from './gr-change-list-reviewer-flow';

const accounts: AccountInfo[] = [
  createAccountWithIdNameAndEmail(0),
  createAccountWithIdNameAndEmail(1),
  createAccountWithIdNameAndEmail(2),
  createAccountWithIdNameAndEmail(3),
  createAccountWithIdNameAndEmail(4),
  createAccountWithIdNameAndEmail(5),
];
const changes: ChangeInfo[] = [
  {
    ...createChange(),
    _number: 1 as NumericChangeId,
    subject: 'Subject 1',
    reviewers: {
      REVIEWER: [accounts[0], accounts[1]],
      CC: [accounts[3], accounts[4]],
    },
  },
  {
    ...createChange(),
    _number: 2 as NumericChangeId,
    subject: 'Subject 2',
    reviewers: {REVIEWER: [accounts[0]], CC: [accounts[3]]},
  },
];

suite('gr-change-list-reviewer-flow tests', () => {
  let element: GrChangeListReviewerFlow;
  let model: BulkActionsModel;

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChanges$, selected =>
      selected.some(other => other._number === change._number)
    );
    await element.updateComplete;
  }

  setup(async () => {
    stubRestApi('getDetailedChangesWithActions').resolves(changes);
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

  test('renders flow', async () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-button
        id="start-flow"
        flatten=""
        aria-disabled="false"
        role="button"
        tabindex="0"
        >add reviewer/cc</gr-button
      >
      <gr-overlay
        aria-hidden="true"
        with-backdrop=""
        tabindex="-1"
        style="outline: none; display: none;"
      >
        <gr-dialog role="dialog">
          <div slot="header">Add Reviewer / CC</div>
          <div slot="main">
            <div>
              <span>Reviewers</span>
              <gr-account-list id="reviewer-list"></gr-account-list>
            </div>
            <div>
              <span>CC</span>
              <gr-account-list id="cc-list"></gr-account-list>
            </div>
          </div>
        </gr-dialog>
      </gr-overlay>
    `);
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
    const overlay = queryAndAssert<GrOverlay>(element, 'gr-overlay');
    assert.isFalse(overlay.opened);
  });

  test('flow button click shows overlay', async () => {
    const button = queryAndAssert<GrButton>(element, 'gr-button#start-flow');

    button.click();
    await element.updateComplete;

    const overlay = queryAndAssert<GrOverlay>(element, 'gr-overlay');
    assert.isTrue(overlay.opened);
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

    test('only lists reviewers/CCs shared by all changes', async () => {
      const reviewerList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#reviewer-list'
      );
      const ccList = queryAndAssert<GrAccountList>(
        dialog,
        'gr-account-list#cc-list'
      );
      // does not include account 1
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
      reviewerList.accounts.push(accounts[2]);
      ccList.accounts.push(accounts[5]);
      await flush();
      dialog.confirmButton!.click();
      await element.updateComplete;

      assert.isTrue(saveChangeReviewStub.calledTwice);
      assert.sameDeepOrderedMembers(saveChangeReviewStub.firstCall.args, [
        changes[0]._number,
        'current',
        {
          reviewers: [
            {reviewer: accounts[2]._account_id, state: ReviewerState.REVIEWER},
            {reviewer: accounts[5]._account_id, state: ReviewerState.CC},
          ],
        },
      ]);
      assert.sameDeepOrderedMembers(saveChangeReviewStub.secondCall.args, [
        changes[1]._number,
        'current',
        {
          reviewers: [
            {reviewer: accounts[2]._account_id, state: ReviewerState.REVIEWER},
            {reviewer: accounts[5]._account_id, state: ReviewerState.CC},
          ],
        },
      ]);
    });

    test('confirm button text updates', async () => {
      assert.equal(dialog.confirmLabel, 'Apply');

      dialog.confirmButton!.click();
      await element.updateComplete;

      assert.equal(dialog.confirmLabel, 'Running');

      await resolvePromises();
      await element.updateComplete;

      assert.equal(dialog.confirmLabel, 'Close');
    });
  });
});
