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
import {GrButton} from '../../shared/gr-button/gr-button';
import './gr-change-list-action-bar';
import type {GrChangeListActionBar} from './gr-change-list-action-bar';

const change1 = {...createChange(), _number: 1 as NumericChangeId};
const change2 = {...createChange(), _number: 2 as NumericChangeId};

suite('gr-change-list-action-bar tests', () => {
  let element: GrChangeListActionBar;
  let model: BulkActionsModel;

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(
      model.selectedChangeNums$,
      selectedChangeNums => selectedChangeNums.length > 0
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

  test('renders all buttons', async () => {
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
            <gr-button
              flatten=""
              aria-disabled="false"
              role="button"
              tabindex="0"
              >abandon</gr-button
            >
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

  test('abandoned clicked', async () => {
    await selectChange(change1);
    const consoleInfoSpy = sinon.spy(console, 'info');
    const button = queryAndAssert<GrButton>(
      element,
      '.actionButtons gr-button'
    );

    button.click();

    assert.isTrue(consoleInfoSpy.calledWith('abandon clicked'));
  });
});
