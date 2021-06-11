/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import './gr-confirm-cherrypick-conflict-dialog.js';
import { GrConfirmCherrypickConflictDialog } from './gr-confirm-cherrypick-conflict-dialog.js';

const basicFixture =
    fixtureFromElement('gr-confirm-cherrypick-conflict-dialog');

suite('gr-confirm-cherrypick-conflict-dialog tests', () => {
  let element: GrConfirmCherrypickConflictDialog;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('_handleConfirmTap', () => {
    const confirmHandler = sinon.stub();
    element.addEventListener('confirm', confirmHandler);
    const confirmTapStub = sinon.spy(element, '_handleConfirmTap');
    element.shadowRoot!
        .querySelector('gr-dialog')!.dispatchEvent(
            new CustomEvent('confirm', {
              composed: true, bubbles: true,
            }));
    assert.isTrue(confirmHandler.called);
    assert.isTrue(confirmHandler.calledOnce);
    assert.isTrue(confirmTapStub.called);
    assert.isTrue(confirmTapStub.calledOnce);
  });

  test('_handleCancelTap', () => {
    const cancelHandler = sinon.stub();
    element.addEventListener('cancel', cancelHandler);
    const cancelTapStub = sinon.spy(element, '_handleCancelTap');
    element.shadowRoot!
        .querySelector('gr-dialog')!.dispatchEvent(
            new CustomEvent('cancel', {
              composed: true, bubbles: true,
            }));
    assert.isTrue(cancelHandler.called);
    assert.isTrue(cancelHandler.calledOnce);
    assert.isTrue(cancelTapStub.called);
    assert.isTrue(cancelTapStub.calledOnce);
  });
});

