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

import '../../../test/common-test-setup-karma.js';
import './gr-alert.js';
suite('gr-alert tests', () => {
  let element;

  setup(() => {
    element = document.createElement('gr-alert');
  });

  teardown(() => {
    if (element.parentNode) {
      element.parentNode.removeChild(element);
    }
  });

  test('show/hide', () => {
    assert.isNull(element.parentNode);
    element.show();
    assert.equal(element.parentNode, document.body);
    element.updateStyles({'--gr-alert-transition-duration': '0ms'});
    element.hide();
    assert.isNull(element.parentNode);
  });

  test('action event', () => {
    const spy = sinon.spy();
    element.show();
    element._actionCallback = spy;
    assert.isFalse(spy.called);
    MockInteractions.tap(element.shadowRoot.querySelector('.action'));
    assert.isTrue(spy.called);
  });
});

