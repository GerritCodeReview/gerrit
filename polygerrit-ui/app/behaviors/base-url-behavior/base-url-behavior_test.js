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

import '../../test/common-test-setup-karma.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {BaseUrlBehavior} from './base-url-behavior.js';

const basicFixture = fixtureFromElement('base-url-behavior-test-element');

suite('base-url-behavior tests', () => {
  let element;
  let originialCanonicalPath;

  suiteSetup(() => {
    originialCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = '/r';
    // Define a Polymer element that uses this behavior.
    Polymer({
      is: 'base-url-behavior-test-element',
      behaviors: [
        BaseUrlBehavior,
      ],
    });
  });

  suiteTeardown(() => {
    window.CANONICAL_PATH = originialCanonicalPath;
  });

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('getBaseUrl', () => {
    assert.deepEqual(element.getBaseUrl(), '/r');
  });
});
