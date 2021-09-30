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
import './gr-limited-text.js';

const basicFixture = fixtureFromElement('gr-limited-text');

suite('gr-limited-text tests', () => {
  let element;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('tooltip without title input', async () => {
    element.text = 'abc 123';
    await element.updateComplete;
    assert.isNotOk(element.shadowRoot.querySelector('gr-tooltip-content'));

    element.limit = 10;
    await element.updateComplete;
    assert.isNotOk(element.shadowRoot.querySelector('gr-tooltip-content'));

    element.limit = 3;
    await element.updateComplete;
    assert.isOk(element.shadowRoot.querySelector('gr-tooltip-content'));
    assert.equal(
      element.shadowRoot.querySelector('gr-tooltip-content').title, 'abc 123');

    element.limit = 100;
    await element.updateComplete;
    assert.isNotOk(element.shadowRoot.querySelector('gr-tooltip-content'));

    element.limit = null;
    await element.updateComplete;
    assert.isNotOk(element.shadowRoot.querySelector('gr-tooltip-content'));
  });

  test('with tooltip input', async () => {
    element.tooltip = 'abc 123';
    await element.updateComplete;
    let tooltipContent = element.shadowRoot.querySelector('gr-tooltip-content');
    assert.isOk(tooltipContent);
    assert.equal(tooltipContent.title, 'abc 123');

    element.text = 'abc';
    await element.updateComplete;
    tooltipContent = element.shadowRoot.querySelector('gr-tooltip-content');
    assert.isOk(tooltipContent);
    assert.equal(tooltipContent.title, 'abc 123');

    element.text = 'abcdef';
    element.limit = 3;
    await element.updateComplete;
    tooltipContent = element.shadowRoot.querySelector('gr-tooltip-content');
    assert.isOk(tooltipContent);
    assert.equal(tooltipContent.title, 'abcdef (abc 123)');
  });

  test('_computeDisplayText', () => {
    element.text = 'foo bar';
    element.limit = 100;
    assert.equal(element.renderText(), 'foo bar');
    element.limit = 4;
    assert.equal(element.renderText(), 'foo…');
    element.limit = 0;
    assert.equal(element.renderText(), 'foo bar');
  });
});

