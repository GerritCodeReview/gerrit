/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma';
import './gr-confirm-abandon-dialog';
import {GrConfirmAbandonDialog} from './gr-confirm-abandon-dialog';
import {queryAndAssert} from '../../../test/test-utils';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';

const basicFixture = fixtureFromElement('gr-confirm-abandon-dialog');

suite('gr-confirm-abandon-dialog tests', () => {
  let element: GrConfirmAbandonDialog;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('_handleConfirmTap', () => {
    const confirmHandler = sinon.stub();
    element.addEventListener('confirm', confirmHandler);
    const confirmTapSpy = sinon.spy(element, '_handleConfirmTap');
    const confirmSpy = sinon.spy(element, '_confirm');
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

  test('_handleCancelTap', () => {
    const cancelHandler = sinon.stub();
    element.addEventListener('cancel', cancelHandler);
    const cancelTapSpy = sinon.spy(element, '_handleCancelTap');
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
