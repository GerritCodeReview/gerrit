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
import './gr-tooltip.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromTemplate(html`
<gr-tooltip>
    </gr-tooltip>
`);

suite('gr-tooltip tests', () => {
  let element;
  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
  });

  test('max-width is respected if set', () => {
    element.text = 'Lorem ipsum dolor sit amet, consectetur adipiscing elit' +
        ', sed do eiusmod tempor incididunt ut labore et dolore magna aliqua';
    element.maxWidth = '50px';
    assert.equal(getComputedStyle(element).width, '50px');
  });

  test('the correct arrow is displayed', () => {
    assert.equal(getComputedStyle(element.shadowRoot
        .querySelector('.arrowPositionBelow')).display,
    'none');
    assert.notEqual(getComputedStyle(element.shadowRoot
        .querySelector('.arrowPositionAbove'))
        .display, 'none');
    element.positionBelow = true;
    assert.notEqual(getComputedStyle(element.shadowRoot
        .querySelector('.arrowPositionBelow'))
        .display, 'none');
    assert.equal(getComputedStyle(element.shadowRoot
        .querySelector('.arrowPositionAbove'))
        .display, 'none');
  });
});

