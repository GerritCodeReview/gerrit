
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


<meta charset="utf-8">







import '../../test/common-test-setup.js';
/** @type {string} */
window.CANONICAL_PATH = '/r';

<test-fixture id="basic">
  <template>
    <test-element></test-element>
  </template>
</test-fixture>

<test-fixture id="within-overlay">
  <template>
    <gr-overlay>
      <test-element></test-element>
    </gr-overlay>
  </template>
</test-fixture>


import '../../test/common-test-setup.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {BaseUrlBehavior} from './base-url-behavior.js';
suite('base-url-behavior tests', () => {
  let element;
  // eslint-disable-next-line no-unused-vars
  let overlay;

  suiteSetup(() => {
    // Define a Polymer element that uses this behavior.
    Polymer({
      is: 'test-element',
      behaviors: [
        BaseUrlBehavior,
      ],
    });
  });

  setup(() => {
    element = fixture('basic');
    overlay = fixture('within-overlay');
  });

  test('getBaseUrl', () => {
    assert.deepEqual(element.getBaseUrl(), '/r');
  });
});

