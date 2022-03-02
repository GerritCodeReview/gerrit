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
import {queryAndAssert, waitUntilObserved} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import './gr-change-list-mark-active-flow';
import type {GrChangeListMarkActiveFlow} from './gr-change-list-mark-active-flow';

const change1 = {...createChange(), _number: 1 as NumericChangeId};
const change2 = {...createChange(), _number: 2 as NumericChangeId};

suite('gr-change-list-mark-active-flow tests', () => {
  let element: GrChangeListMarkActiveFlow;
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
          html`<gr-change-list-mark-active-flow></gr-change-list-mark-active-flow>`,
          bulkActionsModelToken,
          model
        )
      )
    ).querySelector('gr-change-list-mark-active-flow')!;
    await element.updateComplete;
    await selectChange(change1);
    await selectChange(change2);
    await element.updateComplete;
  });

  test('renders flow', async () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-button flatten>mark as active</gr-button>
      <gr-overlay aria-hidden="true" style="outline: none; display: none;">
        <gr-dialog>
          <div slot="header">Mark Changes as Active</div>
          <div slot="main">
            <div>Selected changes: 1, 2</div>
          </div>
        </gr-dialog>
      </gr-overlay>
    `);
  });

  test('button enabled when changes selected', async () => {
    const button = queryAndAssert<GrButton>(element, 'gr-button');
    assert.isFalse(button.disabled);
  });

  test('button disabled when no changes selected', async () => {
    model.clearSelectedChangeNums();
    await waitUntilObserved(model.selectedChangeNums$, s => s.length === 0);
    await element.updateComplete;

    const button = queryAndAssert<GrButton>(element, 'gr-button');
    assert.isTrue(button.disabled);
  });

  test('overlay hidden before button clicked', async () => {
    const overlay = queryAndAssert<GrOverlay>(element, 'gr-overlay');
    assert.isFalse(overlay.opened);
  });

  test('button click shows overlay', async () => {
    const button = queryAndAssert<GrButton>(element, 'gr-button');

    button.click();
    await element.updateComplete;

    const overlay = queryAndAssert<GrOverlay>(element, 'gr-overlay');
    assert.isTrue(overlay.opened);
  });
});
