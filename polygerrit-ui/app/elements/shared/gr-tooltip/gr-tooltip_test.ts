/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-tooltip';
import {GrTooltip} from './gr-tooltip';
import {queryAndAssert} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-tooltip');

suite('gr-tooltip tests', () => {
  let element: GrTooltip;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
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
      getComputedStyle(queryAndAssert(element, '.arrowPositionBelow')!).display,
      'none'
    );
    assert.notEqual(
      getComputedStyle(queryAndAssert(element, '.arrowPositionAbove')!).display,
      'none'
    );
    element.positionBelow = true;
    await element.updateComplete;
    assert.notEqual(
      getComputedStyle(queryAndAssert(element, '.arrowPositionBelow')!).display,
      'none'
    );
    assert.equal(
      getComputedStyle(queryAndAssert(element, '.arrowPositionAbove')!).display,
      'none'
    );
  });
});
