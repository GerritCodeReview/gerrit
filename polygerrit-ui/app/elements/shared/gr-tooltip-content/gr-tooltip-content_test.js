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

import '../../../test/common-test-setup-karma.js';
import './gr-tooltip-content.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromTemplate(html`
<gr-tooltip-content>
    </gr-tooltip-content>
`);

suite('gr-tooltip-content tests', () => {
  let element;
  setup(() => {
    element = basicFixture.instantiate();
  });

  test('icon is not visible by default', () => {
    assert.equal(dom(element.root)
        .querySelector('iron-icon').hidden, true);
  });

  test('position-below attribute is reflected', () => {
    assert.isFalse(element.hasAttribute('position-below'));
    element.positionBelow = true;
    assert.isTrue(element.hasAttribute('position-below'));
  });

  test('icon is visible with showIcon property', () => {
    element.showIcon = true;
    assert.equal(dom(element.root)
        .querySelector('iron-icon').hidden, false);
  });
});

