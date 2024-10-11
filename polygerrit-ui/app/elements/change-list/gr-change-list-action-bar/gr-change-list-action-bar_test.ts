/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html, assert} from '@open-wc/testing';
import {
  BulkActionsModel,
  bulkActionsModelToken,
  LoadingState,
} from '../../../models/bulk-actions/bulk-actions-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import '../../../test/common-test-setup';
import {createChange} from '../../../test/test-data-generators';
import {
  query,
  queryAndAssert,
  waitUntilObserved,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId} from '../../../types/common';
import './gr-change-list-action-bar';
import {GrChangeListActionBar} from './gr-change-list-action-bar';

const change1 = {...createChange(), _number: 1 as NumericChangeId, actions: {}};
const change2 = {...createChange(), _number: 2 as NumericChangeId, actions: {}};

suite('gr-change-list-action-bar tests', () => {
  let element: GrChangeListActionBar;
  let model: BulkActionsModel;

  async function toggleChange(change: ChangeInfo) {
    model.toggleSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChangeNums$, selectedChangeNums =>
      selectedChangeNums.includes(change._number)
    );
    await waitUntilObserved(
      model.loadingState$,
      loadingState => loadingState === LoadingState.LOADED
    );
    await element.updateComplete;
  }

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChangeNums$, selectedChangeNums =>
      selectedChangeNums.includes(change._number)
    );
    await element.updateComplete;
  }

  async function unselectChange(change: ChangeInfo) {
    model.removeSelectedChangeNum(change._number);
    await waitUntilObserved(
      model.selectedChangeNums$,
      selectedChangeNums => !selectedChangeNums.includes(change._number)
    );
    await element.updateComplete;
  }

  setup(async () => {
    model = new BulkActionsModel(getAppContext().restApiService);
    model.sync([change1, change2]);

    element = (
      await fixture(
        wrapInProvider(
          html`<gr-change-list-action-bar></gr-change-list-action-bar>`,
          bulkActionsModelToken,
          model
        )
      )
    ).querySelector('gr-change-list-action-bar')!;
    await element.updateComplete;
  });

  test('renders action bar', async () => {
    await selectChange(change1);

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <td>
          <div class="container">
            <div class="selectionInfo selectionInfoLoading">
              <span class="selectedChanges">1 change selected</span>
              <span class="loadingSpin"></span>
            </div>
          </div>
        </td>
      `
    );

    await unselectChange(change1);
    await toggleChange(change1);

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <td>
          <div class="container">
            <div class="selectionInfo">
              <span class="selectedChanges">1 change selected</span>
            </div>
            <div class="actionButtons">
              <gr-change-list-bulk-vote-flow></gr-change-list-bulk-vote-flow>
              <gr-change-list-topic-flow></gr-change-list-topic-flow>
              <gr-change-list-hashtag-flow></gr-change-list-hashtag-flow>
              <gr-change-list-reviewer-flow></gr-change-list-reviewer-flow>
              <gr-change-list-bulk-abandon-flow></gr-change-list-bulk-abandon-flow>
            </div>
          </div>
        </td>
      `
    );
  });

  test('label reflects number of selected changes', async () => {
    // zero case
    let numSelectedLabel = query<HTMLSpanElement>(element, '.selectedChanges');
    assert.isUndefined(numSelectedLabel);

    // single case
    await toggleChange(change1);
    numSelectedLabel = queryAndAssert<HTMLSpanElement>(
      element,
      '.selectedChanges'
    );
    assert.equal(numSelectedLabel.innerText, '1 change selected');

    // plural case
    await toggleChange(change2);

    numSelectedLabel = queryAndAssert<HTMLSpanElement>(
      element,
      '.selectedChanges'
    );
    assert.equal(numSelectedLabel.innerText, '2 changes selected');
  });
});
