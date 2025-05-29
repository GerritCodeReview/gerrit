/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {query, queryAndAssert} from '../../../test/test-utils';
import {GrTooltipContent} from '../gr-tooltip-content/gr-tooltip-content';
import './gr-limited-text';
import {GrLimitedText} from './gr-limited-text';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-limited-text tests', () => {
  let element: GrLimitedText;

  setup(async () => {
    element = await fixture<GrLimitedText>(
      html`<gr-limited-text></gr-limited-text>`
    );
  });

  test('render', async () => {
    element.text = 'abc 123';
    element.limit = 5;
    element.tooltip = 'tip';
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-tooltip-content has-tooltip="" title="abc 123 (tip)">
          abc …
        </gr-tooltip-content>
      `
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
