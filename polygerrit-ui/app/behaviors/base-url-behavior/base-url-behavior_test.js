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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

/** @type {string} */

const basicFixture = fixtureFromElement('test-element');
const withinOverlayFixture = fixtureFromTemplate(html`
  <gr-overlay>
    <test-element></test-element>
  </gr-overlay>
`);

suite('base-url-behavior tests', () => {
  let element;
  // eslint-disable-next-line no-unused-vars
  let overlay;
  let originialCanonicalPath;

  suiteSetup(() => {
    originialCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = '/r';
    // Define a Polymer element that uses this behavior.
    Polymer({
      is: 'test-element',
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
    overlay = withinOverlayFixture.instantiate();
  });

  test('getBaseUrl', () => {
    assert.deepEqual(element.getBaseUrl(), '/r');
  });
});
