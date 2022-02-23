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

import '../../../test/common-test-setup-karma';
import {query, queryAndAssert} from '../../../test/test-utils';
import {GrTooltipContent} from '../gr-tooltip-content/gr-tooltip-content';
import './gr-limited-text';
import {GrLimitedText} from './gr-limited-text';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-limited-text tests', () => {
  let element: GrLimitedText;

  setup(async () => {
    element = await fixture<GrLimitedText>(
      html`<gr-limited-text></gr-limited-text>`
    );
  });

  test('tooltip without title input', async () => {
    element.text = 'abc 123';
    await element.updateComplete;
    assert.isNotOk(query(element, 'gr-tooltip-content'));

    element.limit = 10;
    await element.updateComplete;
    assert.isNotOk(query(element, 'gr-tooltip-content'));

    element.limit = 3;
    await element.updateComplete;
    assert.isOk(query(element, 'gr-tooltip-content'));
    assert.equal(
      queryAndAssert<GrTooltipContent>(element, 'gr-tooltip-content').title,
      'abc 123'
    );

    element.limit = 100;
    await element.updateComplete;
    assert.isNotOk(query(element, 'gr-tooltip-content'));

    element.limit = null as any;
    await element.updateComplete;
    assert.isNotOk(query(element, 'gr-tooltip-content'));
  });

  test('with tooltip input', async () => {
    element.tooltip = 'abc 123';
    await element.updateComplete;
    let tooltipContent = queryAndAssert<GrTooltipContent>(
      element,
      'gr-tooltip-content'
    );
    assert.isOk(tooltipContent);
    assert.equal(tooltipContent.title, 'abc 123');

    element.text = 'abc';
    await element.updateComplete;
    tooltipContent = queryAndAssert<GrTooltipContent>(
      element,
      'gr-tooltip-content'
    );
    assert.isOk(tooltipContent);
    assert.equal(tooltipContent.title, 'abc 123');

    element.text = 'abcdef';
    element.limit = 3;
    await element.updateComplete;
    tooltipContent = queryAndAssert(element, 'gr-tooltip-content');
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
