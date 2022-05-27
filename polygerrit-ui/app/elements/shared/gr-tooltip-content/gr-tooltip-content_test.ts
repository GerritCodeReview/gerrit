/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-tooltip-content';
import {GrTooltipContent} from './gr-tooltip-content';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrTooltip} from '../gr-tooltip/gr-tooltip';
import {query} from '../../../test/test-utils';

suite('gr-tooltip-content tests', () => {
  let element: GrTooltipContent;

  function makeTooltip(tooltipRect: DOMRect, parentRect: DOMRect) {
    return {
      arrowCenterOffset: '0',
      getBoundingClientRect() {
        return tooltipRect;
      },
      style: {left: '0', top: '0'},
      parentElement: {
        getBoundingClientRect() {
          return parentRect;
        },
      },
    };
  }

  setup(async () => {
    element = await fixture<GrTooltipContent>(html`
      <gr-tooltip-content></gr-tooltip-content>
    `);
    element.title = 'title';
    await element.updateComplete;
  });

  test('icon is not visible by default', () => {
    assert.isNotOk(query(element, 'iron-icon'));
  });

  test('icon is visible with showIcon property', async () => {
    element.showIcon = true;
    await element.updateComplete;
    assert.isOk(query(element, 'iron-icon'));
  });

  test('position-below attribute is reflected', async () => {
    assert.isFalse(element.hasAttribute('position-below'));
    element.positionBelow = true;
    await element.updateComplete;
    assert.isTrue(element.hasAttribute('position-below'));
  });

  test('normal position', () => {
    sinon
      .stub(element, 'getBoundingClientRect')
      .callsFake(() => ({top: 100, left: 100, width: 200} as DOMRect));
    const tooltip = makeTooltip(
      {height: 30, width: 50} as DOMRect,
      {top: 0, left: 0, width: 1000} as DOMRect
    ) as GrTooltip;

    element._positionTooltip(tooltip);
    assert.equal(tooltip.arrowCenterOffset, '0');
    assert.equal(tooltip.style.left, '175px');
    assert.equal(tooltip.style.top, '100px');
  });

  test('left side position', async () => {
    sinon
      .stub(element, 'getBoundingClientRect')
      .callsFake(() => ({top: 100, left: 10, width: 50} as DOMRect));
    const tooltip = makeTooltip(
      {height: 30, width: 120} as DOMRect,
      {top: 0, left: 0, width: 1000} as DOMRect
    ) as GrTooltip;

    element._positionTooltip(tooltip);
    await element.updateComplete;
    assert.isBelow(parseFloat(tooltip.arrowCenterOffset.replace(/px$/, '')), 0);
    assert.equal(tooltip.style.left, '0px');
    assert.equal(tooltip.style.top, '100px');
  });

  test('right side position', () => {
    sinon
      .stub(element, 'getBoundingClientRect')
      .callsFake(() => ({top: 100, left: 950, width: 50} as DOMRect));
    const tooltip = makeTooltip(
      {height: 30, width: 120} as DOMRect,
      {top: 0, left: 0, width: 1000} as DOMRect
    ) as GrTooltip;

    element._positionTooltip(tooltip);
    assert.isAbove(parseFloat(tooltip.arrowCenterOffset.replace(/px$/, '')), 0);
    assert.equal(tooltip.style.left, '915px');
    assert.equal(tooltip.style.top, '100px');
  });

  test('position to bottom', () => {
    sinon
      .stub(element, 'getBoundingClientRect')
      .callsFake(
        () => ({top: 100, left: 950, width: 50, height: 50} as DOMRect)
      );
    const tooltip = makeTooltip(
      {height: 30, width: 120} as DOMRect,
      {top: 0, left: 0, width: 1000} as DOMRect
    ) as GrTooltip;

    element.positionBelow = true;
    element._positionTooltip(tooltip);
    assert.isAbove(parseFloat(tooltip.arrowCenterOffset.replace(/px$/, '')), 0);
    assert.equal(tooltip.style.left, '915px');
    assert.equal(tooltip.style.top, '157.2px');
  });

  test('hides tooltip when detached', async () => {
    const handleHideTooltipStub = sinon.stub(element, '_handleHideTooltip');
    element.remove();
    await element.updateComplete;
    assert.isTrue(handleHideTooltipStub.called);
  });

  test('sets up listeners when has-tooltip is changed', async () => {
    const addListenerStub = sinon.stub(element, 'addEventListener');
    element.hasTooltip = true;
    await element.updateComplete;
    assert.isTrue(addListenerStub.called);
  });

  test('clean up listeners when has-tooltip changed to false', async () => {
    const removeListenerStub = sinon.stub(element, 'removeEventListener');
    element.hasTooltip = true;
    await element.updateComplete;
    element.hasTooltip = false;
    await element.updateComplete;
    assert.isTrue(removeListenerStub.called);
  });

  test('do not display tooltips on touch devices', async () => {
    // On touch devices, tooltips should not be shown.
    element.isTouchDevice = true;
    await element.updateComplete;

    // fire mouse-enter
    await element._handleShowTooltip();
    await element.updateComplete;
    assert.isNotOk(element.tooltip);

    // fire mouse-enter
    element._handleHideTooltip();
    await element.updateComplete;
    assert.isNotOk(element.tooltip);

    // On other devices, tooltips should be shown.
    element.isTouchDevice = false;

    // fire mouse-enter
    await element._handleShowTooltip();
    await element.updateComplete;
    assert.isOk(element.tooltip);

    // fire mouse-enter
    element._handleHideTooltip();
    await element.updateComplete;
    assert.isNotOk(element.tooltip);
  });
});
