/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-list-reviewer-flow';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrChangeListReviewerFlow} from './gr-change-list-reviewer-flow';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {
  createAccountWithIdNameAndEmail,
  createChange,
  createServerInfo,
} from '../../../test/test-data-generators';
import {
  BulkActionsModel,
  bulkActionsModelToken,
} from '../../../models/bulk-actions/bulk-actions-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import {stubRestApi, waitUntilObserved} from '../../../test/test-utils';
import {
  AccountInfo,
  ChangeInfo,
  EmailAddress,
  NumericChangeId,
} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {queryAndAssert} from '../../../utils/common-util';
import {ReviewerState} from '../../../constants/constants';
import {GrHovercardAccount} from '../../shared/gr-hovercard-account/gr-hovercard-account';
import {GrAccountList} from '../../shared/gr-account-list/gr-account-list';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {GrAccountLabel} from '../../shared/gr-account-label/gr-account-label';

const accounts: AccountInfo[] = [
  createAccountWithIdNameAndEmail(0),
  {
    ...createAccountWithIdNameAndEmail(1),
    name: 'CrystalBall Performance Presubmit',
    display_name: 'Performance Presubmit',
    email: 'android-crystalball-presubmit-eng@google.com' as EmailAddress,
  },
  createAccountWithIdNameAndEmail(2),
  createAccountWithIdNameAndEmail(3),
];

const changes: ChangeInfo[] = [
  {
    ...createChange(),
    _number: 1 as NumericChangeId,
    subject: 'Subject 1',
    owner: accounts[0],
    reviewers: {},
  },
  {
    ...createChange(),
    _number: 2 as NumericChangeId,
    subject: 'Subject 2',
    owner: accounts[0],
    reviewers: {},
  },
];

suite('gr-change-list-reviewer-flow screenshot tests', () => {
  let element: GrChangeListReviewerFlow;
  let model: BulkActionsModel;

  setup(async () => {
    stubRestApi('getDetailedChangesWithActions').resolves(changes);
    stubRestApi('getConfig').resolves({
      ...createServerInfo(),
      plugin: {has_avatars: true, js_resource_paths: []},
    });
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

    model.addSelectedChangeNum(changes[0]._number);
    model.addSelectedChangeNum(changes[1]._number);
    await waitUntilObserved(model.selectedChanges$, s => s.length === 2);
    await element.updateComplete;

    const startButton = queryAndAssert<GrButton>(
      element,
      'gr-button#start-flow'
    );
    startButton.click();
    await element.updateComplete;
  });

  test('empty reviewer flow dialog', async () => {
    const dialog = queryAndAssert(element, '#flow');
    await visualDiff(dialog, 'gr-change-list-reviewer-flow-empty');
    await visualDiffDarkTheme(dialog, 'gr-change-list-reviewer-flow-empty');
  });

  test('reviewer flow dialog with reviewers', async () => {
    element.updatedAccountsByReviewerState.set(ReviewerState.REVIEWER, [
      accounts[1],
      accounts[2],
    ]);
    element.requestUpdate();
    await element.updateComplete;

    const dialog = queryAndAssert(element, '#flow');
    await visualDiff(dialog, 'gr-change-list-reviewer-flow-with-reviewers');
    await visualDiffDarkTheme(
      dialog,
      'gr-change-list-reviewer-flow-with-reviewers'
    );
  });

  test('reviewer flow dialog with reviewer hovercard', async () => {
    element.updatedAccountsByReviewerState.set(ReviewerState.REVIEWER, [
      accounts[1],
      accounts[2],
    ]);
    element.requestUpdate();
    await element.updateComplete;

    const accountList = queryAndAssert<GrAccountList>(
      element,
      '#reviewer-list'
    );
    await accountList.updateComplete;
    const chip = queryAndAssert<GrAccountChip>(accountList, 'gr-account-chip');
    await chip.updateComplete;
    const label = queryAndAssert<GrAccountLabel>(chip, 'gr-account-label');
    await label.updateComplete;
    const hovercard = queryAndAssert<GrHovercardAccount>(
      label,
      'gr-hovercard-account'
    );
    await hovercard.show({});
    await hovercard.updateComplete;

    const dialog = queryAndAssert(element, '#flow');
    await visualDiff(
      dialog,
      'gr-change-list-reviewer-flow-with-reviewer-hovercard'
    );
    await visualDiffDarkTheme(
      dialog,
      'gr-change-list-reviewer-flow-with-reviewer-hovercard'
    );
  });
});
