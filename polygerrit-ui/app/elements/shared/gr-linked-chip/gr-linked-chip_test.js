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
import './gr-linked-chip.js';

const basicFixture = fixtureFromElement('gr-linked-chip');

suite('gr-linked-chip tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('remove fired', () => {
    const spy = sinon.spy();
    element.addEventListener('remove', spy);
    flush();
    MockInteractions.tap(element.$.remove);
    assert.isTrue(spy.called);
  });
});

