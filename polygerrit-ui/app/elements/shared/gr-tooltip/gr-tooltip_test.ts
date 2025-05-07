/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-tooltip';
import {GrTooltip} from './gr-tooltip';
import {queryAndAssert} from '../../../test/test-utils';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-tooltip tests', () => {
  let element: GrTooltip;

  setup(async () => {
    element = await fixture(html`<gr-tooltip></gr-tooltip>`);
    await element.updateComplete;
  });

  test('render', async () => {
    element.text = 'tooltipText';
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="tooltip" aria-live="polite" role="tooltip">
          <i class="arrow arrowPositionBelow" style="margin-left:0;"> </i>
          <div class="text">tooltipText<br /></div>
          <i class="arrow arrowPositionAbove" style="margin-left:0;"> </i>
        </div>
      `
    );
  });

  test('max-width is respected if set', async () => {
    element.text =
      'Lorem ipsum dolor sit amet, consectetur adipiscing elit' +
      ', sed do eiusmod tempor incididunt ut labore et dolore magna aliqua';
    element.maxWidth = '50px';
    await element.updateComplete;
    assert.equal(getComputedStyle(element).width, '50px');
  });

  test('the correct arrow is displayed', async () => {
    assert.equal(
      getComputedStyle(queryAndAssert(element, '.arrowPositionBelow')).display,
      'none'
    );
    assert.notEqual(
      getComputedStyle(queryAndAssert(element, '.arrowPositionAbove')).display,
      'none'
    );
    element.positionBelow = true;
    await element.updateComplete;
    assert.notEqual(
      getComputedStyle(queryAndAssert(element, '.arrowPositionBelow')).display,
      'none'
    );
    assert.equal(
      getComputedStyle(queryAndAssert(element, '.arrowPositionAbove')).display,
      'none'
    );
  });
});
