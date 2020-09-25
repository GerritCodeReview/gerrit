/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import './gr-dialog.js';
import {isHidden} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-dialog');

suite('gr-dialog tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('events', done => {
    let numEvents = 0;
    function handler() { if (++numEvents == 2) { done(); } }

    element.addEventListener('confirm', handler);
    element.addEventListener('cancel', handler);

    MockInteractions.tap(element.shadowRoot
        .querySelector('gr-button[primary]'));
    MockInteractions.tap(element.shadowRoot
        .querySelector('gr-button:not([primary])'));
  });

  test('confirmOnEnter', () => {
    element.confirmOnEnter = false;
    const handleConfirmStub = sinon.stub(element, '_handleConfirm');
    const handleKeydownSpy = sinon.spy(element, '_handleKeydown');
    MockInteractions.pressAndReleaseKeyOn(element.shadowRoot
        .querySelector('main'),
    13, null, 'enter');
    flush();

    assert.isTrue(handleKeydownSpy.called);
    assert.isFalse(handleConfirmStub.called);

    element.confirmOnEnter = true;
    MockInteractions.pressAndReleaseKeyOn(element.shadowRoot
        .querySelector('main'),
    13, null, 'enter');
    flush();

    assert.isTrue(handleConfirmStub.called);
  });

  test('resetFocus', () => {
    const focusStub = sinon.stub(element.$.confirm, 'focus');
    element.resetFocus();
    assert.isTrue(focusStub.calledOnce);
  });

  suite('tooltip', () => {
    test('tooltip not added by default', () => {
      assert.isNull(element.$.confirm.getAttribute('has-tooltip'));
    });

    test('tooltip added if confirm tooltip is passed', done => {
      element.confirmTooltip = 'confirm tooltip';
      flush(() => {
        assert(element.$.confirm.getAttribute('has-tooltip'));
        done();
      });
    });
  });

  test('empty cancel label hides cancel btn', () => {
    assert.isFalse(isHidden(element.$.cancel));
    element.cancelLabel = '';
    flush();

    assert.isTrue(isHidden(element.$.cancel));
  });
});

