/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ReactiveController, ReactiveControllerHost} from 'lit';

export interface FitControllerHost {
  /**
   * This offset will increase or decrease the distance to the left side
   * of the screen, a negative offset will move the dropdown to the left
   * a positive one, to the right.
   *
   */
  horizontalOffset: number;

  /**
   * This offset will increase or decrease the distance to the top
   * side of the screen: a negative offset will move the dropdown upwards
   * , a positive one, downwards.
   *
   */
  verticalOffset: number;
}

export interface PositionStyles {
  top: string;
  left: string;
  position: string;
  maxWidth: string;
  maxHeight: string;
  boxSizing: string;
}

/**
 * `FitController` fits an element in another element using `max-height`
 * and `max-width`.
 *
 * FitController overrides all properties defined in PositionStyles for the
 * host.
 * The element will only be sized and/or positioned if it has not already been
 * sized and/or positioned by CSS.
 *  CSS properties            | Action
 * --------------------------|-------------------------------------------
 * `position` set            | Element is not centered horizontally/vertically
 * `top` or `bottom` set     | Element is not vertically centered
 * `left` or `right` set     | Element is not horizontally centered
 * `max-height` set          | Element respects `max-height`
 * `max-width` set           | Element respects `max-width`
 *
 * `FitController` positions an element into another element and gives it
 * a horizontalAlignment = left and verticalAlignment = top.
 * This will override the element's css position.
 *
 * Use `horizontalOffset, verticalOffset` to offset the element from its
 * `positionTarget`; `FitController` will collapse these in order to
 * keep the element within `window` boundaries, while preserving the element's
 * CSS margin values.
 *
 */
export class FitController implements ReactiveController {
  host: ReactiveControllerHost & HTMLElement & FitControllerHost;

  private originalStyles?: PositionStyles;

  private positionTarget?: HTMLElement;

  constructor(host: ReactiveControllerHost & HTMLElement & FitControllerHost) {
    (this.host = host).addController(this);
  }

  hostConnected() {
    this.positionTarget = this.getPositionTarget();
  }

  hostDisconnected() {}

  // private but used in tests
  getPositionTarget() {
    let parent = this.host.parentNode;

    if (parent && parent.nodeType === Node.DOCUMENT_FRAGMENT_NODE) {
      parent = (parent as ShadowRoot).host;
    }

    return parent as HTMLElement;
  }

  private saveOriginalStyles() {
    // These properties are changed in position() hence keep the original
    // values to reset the host styles later.
    this.originalStyles = {
      top: this.host.style.top || '',
      left: this.host.style.left || '',
      position: this.host.style.position || '',
      maxWidth: this.host.style.maxWidth || '',
      maxHeight: this.host.style.maxHeight || '',
      boxSizing: this.host.style.boxSizing || '',
    };
  }

  /**
   * Reset the host style, and clear the memoized data.
   */
  private resetStyles() {
    // It is necessary to clear the max-width:0px and max-height:0px.
    // A component may call refit() multiple times, in which case we don't
    // want the values assigned from the first call which may not be precisely
    // correct to influence the second call.
    // Hence we reset the styles here.
    if (this.originalStyles !== undefined) {
      Object.assign(this.host.style, this.originalStyles);
    }
    this.originalStyles = undefined;
  }

  setPositionTarget(target: HTMLElement) {
    this.positionTarget = target;
  }

  /**
   * Equivalent to calling `resetStyles()` and `position()`.
   * Useful to call this after the element or the `window` element has
   * been resized, or if any of the positioning properties
   * (e.g. `horizontalOffset, verticalOffset`) are updated.
   * It preserves the scroll position of the host.
   */
  refit() {
    const scrollLeft = this.host.scrollLeft;
    const scrollTop = this.host.scrollTop;
    this.resetStyles();
    this.position();
    this.host.scrollLeft = scrollLeft;
    this.host.scrollTop = scrollTop;
  }

  private position() {
    this.saveOriginalStyles();

    this.host.style.position = 'fixed';
    // Need border-box for margin/padding.
    this.host.style.boxSizing = 'border-box';

    const hostRect = this.host.getBoundingClientRect();
    const positionRect = this.getNormalizedRect(this.positionTarget!);
    const windowRect = this.getNormalizedRect(window);

    this.calculateAndSetPositions(hostRect, positionRect, windowRect);
  }

  // private but used in tests
  calculateAndSetPositions(
    hostRect: DOMRect,
    positionRect: DOMRect,
    windowRect: DOMRect
  ) {
    const hostStyles = (window as Window).getComputedStyle(this.host);
    const hostMinWidth = parseInt(hostStyles.minWidth) || 0;
    const hostMinHeight = parseInt(hostStyles.minHeight) || 0;

    const hostMargin = {
      top: parseInt(hostStyles.marginTop) || 0,
      right: parseInt(hostStyles.marginRight) || 0,
      bottom: parseInt(hostStyles.marginBottom) || 0,
      left: parseInt(hostStyles.marginLeft) || 0,
    };

    let leftPosition =
      positionRect.left + this.host.horizontalOffset + hostMargin.left;
    let topPosition =
      positionRect.top + this.host.verticalOffset + hostMargin.top;

    // Limit right/bottom within window respecting the margin.
    const rightPosition = Math.min(
      windowRect.right - hostMargin.right,
      leftPosition + hostRect.width
    );
    const bottomPosition = Math.min(
      windowRect.bottom - hostMargin.bottom,
      topPosition + hostRect.height
    );

    // Respect hostMinWidth and hostMinHeight
    // Current width is rightPosition - leftPosition or hostRect.width
    //    rightPosition - leftPosition >= hostMinWidth
    // => leftPosition <= rightPosition - hostMinWidth
    leftPosition = Math.min(leftPosition, rightPosition - hostMinWidth);
    topPosition = Math.min(topPosition, bottomPosition - hostMinHeight);

    // Limit left/top within window respecting the margin.
    leftPosition = Math.max(windowRect.left + hostMargin.left, leftPosition);
    topPosition = Math.max(windowRect.top + hostMargin.top, topPosition);

    // Use right/bottom to set maxWidth/maxHeight and respect
    // minWidth/minHeight.
    const maxWidth = Math.max(rightPosition - leftPosition, hostMinWidth);
    const maxHeight = Math.max(bottomPosition - topPosition, hostMinHeight);

    this.host.style.maxWidth = `${maxWidth}px`;
    this.host.style.maxHeight = `${maxHeight}px`;

    this.host.style.left = `${leftPosition}px`;
    this.host.style.top = `${topPosition}px`;
  }

  private getNormalizedRect(target: Window | HTMLElement): DOMRect {
    if (target === document.documentElement || target === window) {
      return new DOMRect(0, 0, window.innerWidth, window.innerHeight);
    }
    return (target as HTMLElement).getBoundingClientRect();
  }
}
