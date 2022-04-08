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

import {fixture, html} from '@open-wc/testing-helpers';
import '../../../test/common-test-setup-karma';
import {queryAndAssert} from '../../../utils/common-util';
import './gr-confirm-cherrypick-conflict-dialog';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrConfirmCherrypickConflictDialog} from './gr-confirm-cherrypick-conflict-dialog';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-confirm-cherrypick-conflict-dialog tests', () => {
  let element: GrConfirmCherrypickConflictDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-confirm-cherrypick-conflict-dialog></gr-confirm-cherrypick-conflict-dialog>`
    );
  });

  test('render', async () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <gr-dialog confirm-label="Continue" role="dialog">
        <div class="header" slot="header">Cherry Pick Conflict!</div>
        <div class="main" slot="main">
          <span>Cherry Pick failed! (merge conflicts)</span>
          <span
            >Please select "Continue" to continue with conflicts or select
            "cancel" to close the dialog.</span
          >
        </div>
      </gr-dialog>
    `);
  });

  test('confirm', async () => {
    const confirmHandler = sinon.stub();
    element.addEventListener('confirm', confirmHandler);

    queryAndAssert<GrDialog>(element, 'gr-dialog').confirmButton!.click();
    await element.updateComplete;

    assert.isTrue(confirmHandler.called);
    assert.isTrue(confirmHandler.calledOnce);
  });

  test('cancel', async () => {
    const cancelHandler = sinon.stub();
    element.addEventListener('cancel', cancelHandler);

    queryAndAssert<GrButton>(
      queryAndAssert<GrDialog>(element, 'gr-dialog'),
      'gr-button#cancel'
    )!.click();
    await element.updateComplete;

    assert.isTrue(cancelHandler.called);
    assert.isTrue(cancelHandler.calledOnce);
  });
});
