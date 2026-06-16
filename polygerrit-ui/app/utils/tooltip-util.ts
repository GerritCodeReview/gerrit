/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export const ARROW_HEIGHT = 7.2;

export function computeTooltipLeft(
  tooltipRect: DOMRect,
  hoveredCenter: number,
  parentWidth: number
): number {
  let left = hoveredCenter - 0.5 * tooltipRect.width;
  if (left + tooltipRect.width > parentWidth - 1) {
    // Add 1px of extra padding. Without it on some browser zoom levels
    // the hovercard is still considered going out of bounds and gets
    // reshaped.
    left = parentWidth - tooltipRect.width - 1;
  }
  return Math.max(0, left);
}

export function computeTooltipTop(
  tooltipRect: DOMRect,
  hoveredRect: DOMRect,
  parentRect: DOMRect,
  positionBelow: boolean,
  arrowHeight = ARROW_HEIGHT
): {
  isBelow: boolean;
  top: number;
} {
  const top =
    hoveredRect.top - parentRect.top - tooltipRect.height - arrowHeight;
  if (positionBelow || top < 0) {
    return {
      isBelow: true,
      top: hoveredRect.bottom - parentRect.top + arrowHeight,
    };
  }
  return {isBelow: false, top};
}
