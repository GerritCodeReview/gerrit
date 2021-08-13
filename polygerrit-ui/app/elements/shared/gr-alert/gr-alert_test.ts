/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import './gr-alert';
import {GrAlert} from './gr-alert';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

suite('gr-alert tests', () => {
  let element: GrAlert;

  setup(() => {
    element = document.createElement('gr-alert');
  });

  teardown(() => {
    if (element.parentNode) {
      element.parentNode.removeChild(element);
    }
  });

  test('show/hide', async () => {
    assert.isNull(element.parentNode);
    element.show('Alert text');
    // wait for element to be rendered after being attached to DOM
    await flush();
    assert.equal(element.parentNode, document.body);
    element.style.setProperty('--gr-alert-transition-duration', '0ms');
    element.hide();
    assert.isNull(element.parentNode);
  });

  test('action event', async () => {
    const spy = sinon.spy();
    element.show('Alert text');
    await flush();
    element._actionCallback = spy;
    assert.isFalse(spy.called);
    MockInteractions.tap(element.shadowRoot!.querySelector('.action')!);
    assert.isTrue(spy.called);
  });
});
