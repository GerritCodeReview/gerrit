/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {assert} from '@open-wc/testing';
import {computeTooltipLeft, computeTooltipTop} from './tooltip-util';

suite('tooltip-util tests', () => {
  test('computeTooltipLeft', () => {
    const tooltipRect = {width: 100} as DOMRect;

    // Normal case (centered)
    assert.equal(computeTooltipLeft(tooltipRect, 200, 500), 150);

    // Left boundary case (clamped to 0)
    assert.equal(computeTooltipLeft(tooltipRect, 30, 500), 0);

    // Right boundary case (clamped to parentWidth - width - 1)
    assert.equal(computeTooltipLeft(tooltipRect, 480, 500), 399);
  });

  test('computeTooltipTop', () => {
    const tooltipRect = {height: 50} as DOMRect;
    const parentRect = {top: 10} as DOMRect;

    // Above target, fits
    const hoveredRect1 = {top: 100, bottom: 120} as DOMRect;
    const res1 = computeTooltipTop(
      tooltipRect,
      hoveredRect1,
      parentRect,
      false
    );
    assert.isFalse(res1.isBelow);
    assert.closeTo(res1.top, 32.8, 0.01);

    // Above target, doesn't fit (flips to below)
    const hoveredRect2 = {top: 20, bottom: 40} as DOMRect;
    const res2 = computeTooltipTop(
      tooltipRect,
      hoveredRect2,
      parentRect,
      false
    );
    assert.isTrue(res2.isBelow);
    assert.closeTo(res2.top, 37.2, 0.01);

    // Forced below
    const res3 = computeTooltipTop(tooltipRect, hoveredRect1, parentRect, true);
    assert.isTrue(res3.isBelow);
    assert.closeTo(res3.top, 117.2, 0.01);
  });
});
