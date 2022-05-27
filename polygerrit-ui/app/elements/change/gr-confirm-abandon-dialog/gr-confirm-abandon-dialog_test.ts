/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-confirm-abandon-dialog';
import {GrConfirmAbandonDialog} from './gr-confirm-abandon-dialog';
import {queryAndAssert} from '../../../test/test-utils';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';

const basicFixture = fixtureFromElement('gr-confirm-abandon-dialog');

suite('gr-confirm-abandon-dialog tests', () => {
  let element: GrConfirmAbandonDialog;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('handleConfirmTap', () => {
    const confirmHandler = sinon.stub();
    element.addEventListener('confirm', confirmHandler);
    const confirmTapSpy = sinon.spy(element, 'handleConfirmTap');
    const confirmSpy = sinon.spy(element, 'confirm');
    queryAndAssert<GrDialog>(element, 'gr-dialog').dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: true,
      })
    );
    assert.isTrue(confirmHandler.called);
    assert.isTrue(confirmHandler.calledOnce);
    assert.isTrue(confirmTapSpy.called);
    assert.isTrue(confirmSpy.called);
    assert.isTrue(confirmSpy.calledOnce);
  });

  test('handleCancelTap', () => {
    const cancelHandler = sinon.stub();
    element.addEventListener('cancel', cancelHandler);
    const cancelTapSpy = sinon.spy(element, 'handleCancelTap');
    queryAndAssert<GrDialog>(element, 'gr-dialog').dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: true,
      })
    );
    assert.isTrue(cancelHandler.called);
    assert.isTrue(cancelHandler.calledOnce);
    assert.isTrue(cancelTapSpy.called);
    assert.isTrue(cancelTapSpy.calledOnce);
  });
});
