/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-confirm-delete-item-dialog';
import {GrConfirmDeleteItemDialog} from './gr-confirm-delete-item-dialog';
import {queryAndAssert} from '../../../test/test-utils';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';

const basicFixture = fixtureFromElement('gr-confirm-delete-item-dialog');

suite('gr-confirm-delete-item-dialog tests', () => {
  let element: GrConfirmDeleteItemDialog;

  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
  });

  test('render', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-dialog
        confirm-label="Delete UNKNOWN ITEM TYPE"
        confirm-on-enter=""
        role="dialog"
      >
        <div class="header" slot="header">UNKNOWN ITEM TYPE Deletion</div>
        <div class="main" slot="main">
          <label for="branchInput">
            Do you really want to delete the following UNKNOWN ITEM TYPE?
          </label>
          <div>UNKNOWN ITEM</div>
        </div>
      </gr-dialog>
    `);
  });

  test('_handleConfirmTap', () => {
    const confirmHandler = sinon.stub();
    element.addEventListener('confirm', confirmHandler);
    queryAndAssert<GrDialog>(element, 'gr-dialog').dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: false,
      })
    );
    assert.equal(confirmHandler.callCount, 1);
  });

  test('_handleCancelTap', () => {
    const cancelHandler = sinon.stub();
    element.addEventListener('cancel', cancelHandler);
    queryAndAssert<GrDialog>(element, 'gr-dialog').dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: false,
      })
    );
    assert.equal(cancelHandler.callCount, 1);
  });
});
