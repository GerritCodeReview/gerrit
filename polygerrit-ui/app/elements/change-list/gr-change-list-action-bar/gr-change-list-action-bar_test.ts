/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html} from '@open-wc/testing-helpers';
import {
  BulkActionsModel,
  bulkActionsModelToken,
} from '../../../models/bulk-actions/bulk-actions-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import '../../../test/common-test-setup-karma';
import {createChange} from '../../../test/test-data-generators';
import {
  query,
  queryAndAssert,
  waitUntilObserved,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId} from '../../../types/common';
import './gr-change-list-action-bar';
import type {GrChangeListActionBar} from './gr-change-list-action-bar';

const change1 = {...createChange(), _number: 1 as NumericChangeId, actions: {}};
const change2 = {...createChange(), _number: 2 as NumericChangeId, actions: {}};

suite('gr-change-list-action-bar tests', () => {
  let element: GrChangeListActionBar;
  let model: BulkActionsModel;

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChangeNums$, selectedChangeNums =>
      selectedChangeNums.includes(change._number)
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

    expect(element).shadowDom.to.equal(/* HTML */ `
      <td></td>
      <td><input type="checkbox" /></td>
      <td>
        <div class="container">
          <div class="selectionInfo">
            <span>1 change selected</span>
          </div>
          <div class="actionButtons">
            <gr-change-list-bulk-vote-flow></gr-change-list-bulk-vote-flow>
            <gr-change-list-topic-flow></gr-change-list-topic-flow>
            <gr-change-list-hashtag-flow></gr-change-list-hashtag-flow>
            <gr-change-list-reviewer-flow></gr-change-list-reviewer-flow>
          </div>
        </div>
      </td>
    `);
  });

  test('label reflects number of selected changes', async () => {
    // zero case
    let numSelectedLabel = query<HTMLSpanElement>(
      element,
      '.selectionInfo span'
    );
    assert.isUndefined(numSelectedLabel);

    // single case
    await selectChange(change1);
    numSelectedLabel = queryAndAssert<HTMLSpanElement>(
      element,
      '.selectionInfo span'
    );
    assert.equal(numSelectedLabel.innerText, '1 change selected');

    // plural case
    await selectChange(change2);

    numSelectedLabel = queryAndAssert<HTMLSpanElement>(
      element,
      '.selectionInfo span'
    );
    assert.equal(numSelectedLabel.innerText, '2 changes selected');
  });

  test('checkbox matches partial and fully selected state', async () => {
    // zero case
    let checkbox = queryAndAssert<HTMLInputElement>(element, 'input');
    assert.isFalse(checkbox.checked);
    assert.isFalse(checkbox.indeterminate);

    // partial case
    await selectChange(change1);
    checkbox = queryAndAssert<HTMLInputElement>(element, 'input');
    assert.isTrue(checkbox.indeterminate);

    // plural case
    await selectChange(change2);

    checkbox = queryAndAssert<HTMLInputElement>(element, 'input');
    assert.isFalse(checkbox.indeterminate);
    assert.isTrue(checkbox.checked);
  });

  test('clicking checkbox clears selection', async () => {
    await selectChange(change1);
    await selectChange(change2);
    let selectedChangeNums = await waitUntilObserved(
      model.selectedChangeNums$,
      s => s.length === 2
    );
    assert.sameMembers(selectedChangeNums, [change1._number, change2._number]);

    const checkbox = queryAndAssert<HTMLInputElement>(element, 'input');
    checkbox.click();

    selectedChangeNums = await waitUntilObserved(
      model.selectedChangeNums$,
      s => s.length === 0
    );
    assert.isEmpty(selectedChangeNums);
  });
});
