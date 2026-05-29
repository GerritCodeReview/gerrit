/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

const FOCUSABLE_QUERY =
  'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

/**
 * Gets an ordered list of focusable elements nested within a containing
 * element that may contain shadow DOMs.
 *
 * This goes depth-first, so that the order of elements follows the a11y tree.
 */
export function* getFocusableElements(
  el: HTMLElement | SVGElement
): Generator<HTMLElement | SVGElement> {
  const style = window.getComputedStyle(el);
  if (style.display === 'none' || style.visibility === 'hidden') return;
  if (el.matches(FOCUSABLE_QUERY)) {
    yield el;
  }

  let children = [];
  if (el.localName === 'slot') {
    children = (el as HTMLSlotElement).assignedNodes({flatten: true});
  } else {
    children = [...(el.shadowRoot || el).children];
  }

  for (const node of children.filter(
    node => node instanceof HTMLElement || node instanceof SVGElement
  )) {
    yield* getFocusableElements(node);
  }
}

/**
 * Gets an ordered list of focusable elements nested within a containing
 * element that may contain shadow DOMs.
 *
 * This returns in reverse a11 order.
 */
export function* getFocusableElementsReverse(
  el: HTMLElement | SVGElement
): Generator<HTMLElement | SVGElement> {
  const style = window.getComputedStyle(el);
  if (style.display === 'none' || style.visibility === 'hidden') return;

  let children = [];
  if (el.localName === 'slot') {
    children = (el as HTMLSlotElement).assignedNodes({flatten: true});
  } else {
    children = [...(el.shadowRoot || el).children];
  }

  for (const node of children
    .filter(node => node instanceof HTMLElement || node instanceof SVGElement)
    .reverse()) {
    yield* getFocusableElementsReverse(node);
  }

  if (el.matches(FOCUSABLE_QUERY)) {
    yield el;
  }
}
