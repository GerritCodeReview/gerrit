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













import '../../../test/common-test-setup.js';
import './gr-default-editor.js';

const basicFixture = fixtureFromElement('gr-default-editor');


suite('gr-default-editor tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
    element.fileContent = '';
  });

  test('fires content-change event', done => {
    const contentChangedHandler = e => {
      assert.equal(e.detail.value, 'test');
      done();
    };
    const textarea = element.$.textarea;
    element.addEventListener('content-change', contentChangedHandler);
    textarea.value = 'test';
    textarea.dispatchEvent(new CustomEvent('input',
        {target: textarea, bubbles: true, composed: true}));
  });
});

