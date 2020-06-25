
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


<meta charset="utf-8">







<test-fixture id="basic">
  <template>
    <gr-create-change-help></gr-create-change-help>
  </template>
</test-fixture>


import '../../../test/common-test-setup.js';
import './gr-create-change-help.js';
suite('gr-create-change-help tests', () => {
  let element;

  setup(() => {
    element = fixture('basic');
  });

  test('Create change tap', done => {
    element.addEventListener('create-tap', () => done());
    MockInteractions.tap(element.shadowRoot
        .querySelector('gr-button'));
  });
});

