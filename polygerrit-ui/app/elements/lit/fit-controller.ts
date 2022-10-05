/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ReactiveController, ReactiveControllerHost} from 'lit';

/**
 * horizontalAlign = left;
 * The orientation against which to align the element horizontally
 * relative to the `positionTarget`.
 */

/**
 * verticalAlign = top;
 * The orientation against which to align the element vertically
 * relative to the `positionTarget`.
 */

export interface FitControllerHost {
  /**
   * A pixel value that will be added to the position calculated for the
   * given `horizontalAlign`, in the direction of alignment. You can think
   * of it as increasing or decreasing the distance to the side of the
   * screen given by `horizontalAlign`.
   *
   * If `horizontalAlign` is "left" or "center", this offset will increase or
   * decrease the distance to the left side of the screen: a negative offset
   * will move the dropdown to the left; a positive one, to the right.
   *
   * Conversely if `horizontalAlign` is "right", this offset will increase
   * or decrease the distance to the right side of the screen: a negative
   * offset will move the dropdown to the right; a positive one, to the left.
   */
  horizontalOffset: number;
  /**
   * A pixel value that will be added to the position calculated for the
   * given `verticalAlign`, in the direction of alignment. You can think
   * of it as increasing or decreasing the distance to the side of the
   * screen given by `verticalAlign`.
   *
   * If `verticalAlign` is "top" or "middle", this offset will increase or
   * decrease the distance to the top side of the screen: a negative offset
   * will move the dropdown upwards; a positive one, downwards.
   *
   * Conversely if `verticalAlign` is "bottom", this offset will increase
   * or decrease the distance to the bottom side of the screen: a negative
   * offset will move the dropdown downwards; a positive one, upwards.
   */
  verticalOffset: number;
}

// Information needed to position and size the target element
interface FitInfo {
  sizedBy: {
    minWidth: number;
    minHeight: number;
  };
  margin: {
    top: number;
    right: number;
    bottom: number;
    left: number;
  };
}

//  `FitController` fits an element in another element using `max-height`
//  and `max-width`, and optionally centers it in the window or another element.

//  The element will only be sized and/or positioned if it has not already been
//  sized and/or positioned by CSS.

//  CSS properties            | Action
//  --------------------------|-------------------------------------------
//  `position` set            | Element is not centered horizontally or vertically
//  `top` or `bottom` set     | Element is not vertically centered
//  `left` or `right` set     | Element is not horizontally centered
//  `max-height` set          | Element respects `max-height`
//  `max-width` set           | Element respects `max-width`

//  `FitController` can position an element into another element using
//  `verticalAlign` and `horizontalAlign`. This will override the element's css
//  position.

//      <div class="container">
//        <iron-fit-impl vertical-align="top" horizontal-align="auto">
//          Positioned into the container
//        </iron-fit-impl>
//      </div>

//  Use `horizontalOffset, verticalOffset` to offset the element from its
//  `positionTarget`; `FitController` will collapse these in order to
//  keep the element within `window` boundaries, while preserving the element's
//  CSS margin values.

//      <div class="container">
//        <iron-fit-impl vertical-align="top" vertical-offset="20">
//          With vertical offset
//        </iron-fit-impl>
//      </div>

export class FitController implements ReactiveController {
  host: ReactiveControllerHost & HTMLElement & FitControllerHost;

  private fitInfo?: FitInfo;

  private originalStyles = {};

  private positionTarget?: HTMLElement;

  constructor(host: ReactiveControllerHost & HTMLElement & FitControllerHost) {
    (this.host = host).addController(this);
  }

  hostConnected() {
    this.positionTarget = this.getDefaultPositionTarget();
  }

  hostDisconnected() {}

  private getDefaultPositionTarget() {
    let parent = this.host.parentNode;

    if (parent && parent.nodeType === Node.DOCUMENT_FRAGMENT_NODE) {
      parent = (parent as ShadowRoot).host;
    }

    return parent as HTMLElement;
  }

  /**
   * Memoize information needed to position and size the host element.
   *
   */
  private discoverInfo() {
    if (this.fitInfo) {
      return;
    }
    const hostStyles = (window as Window).getComputedStyle(this.host);

    // These properties are changes in position() hence keep the original
    // values to reset the host styles later.
    this.originalStyles = {
      top: this.host.style.top || '',
      left: this.host.style.left || '',
      position: this.host.style.position || '',
      maxWidth: this.host.style.maxWidth || '',
      maxHeight: this.host.style.maxHeight || '',
      boxSizing: this.host.style.boxSizing || '',
    };

    this.fitInfo = {
      sizedBy: {
        minWidth: Number(hostStyles.minWidth) || 0,
        minHeight: Number(hostStyles.minHeight) || 0,
      },
      margin: {
        top: Number(hostStyles.marginTop) || 0,
        right: Number(hostStyles.marginRight) || 0,
        bottom: Number(hostStyles.marginBottom) || 0,
        left: Number(hostStyles.marginLeft) || 0,
      },
    };
  }

  /**
   * Reset the host style, and clear the memoized data.
   */
  private resetStyles() {
    Object.assign(this.host.style, this.originalStyles);
    this.originalStyles = {};
    this.fitInfo = undefined;
  }

  /**
   * Equivalent to calling `resetStyles()` and `fit()`.
   * Useful to call this after the element or the `window` element has
   * been resized, or if any of the positioning properties
   * (e.g. `horizontalAlign, verticalAlign`) is updated.
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

  /**
   * Positions the element according to `horizontalAlign, verticalAlign`.
   */
  private position() {
    this.discoverInfo();

    this.host.style.position = 'fixed';
    // Need border-box for margin/padding.
    this.host.style.boxSizing = 'border-box';
    // Set to 0, 0 in order to discover any offset caused by parent stacking
    // contexts.
    this.host.style.left = '0px';
    this.host.style.top = '0px';

    const rect = this.host.getBoundingClientRect();
    const positionRect = this.getNormalizedRect(this.positionTarget!);
    const fitRect = this.getNormalizedRect(window);

    const margin = this.fitInfo!.margin;

    let leftPosition =
      positionRect.left + this.host.horizontalOffset + margin.left;
    let topPosition = positionRect.top + this.host.verticalOffset + margin.top;

    // We first limit right/bottom within window respecting the margin,
    // then use those values to limit top/left.
    const right = Math.min(
      fitRect.right - margin.right,
      leftPosition + rect.width
    );
    const bottom = Math.min(
      fitRect.bottom - margin.bottom,
      topPosition + rect.height
    );

    // Keep left/top within fitInto respecting the margin.
    leftPosition = Math.max(
      fitRect.left + margin.left,
      Math.min(leftPosition, right - this.fitInfo!.sizedBy.minWidth)
    );
    topPosition = Math.max(
      fitRect.top + margin.top,
      Math.min(topPosition, bottom - this.fitInfo!.sizedBy.minHeight)
    );

    // Use right/bottom to set maxWidth/maxHeight, and respect
    // minWidth/minHeight.
    const maxWidth = Math.max(
      right - leftPosition,
      this.fitInfo!.sizedBy.minWidth
    );
    const maxHeight = Math.max(
      bottom - topPosition,
      this.fitInfo!.sizedBy.minHeight
    );

    this.host.style.maxWidth = maxWidth.toString() + 'px';
    this.host.style.maxHeight = maxHeight.toString() + 'px';

    // Remove the offset caused by any stacking context.
    leftPosition = leftPosition - rect.left;
    topPosition = topPosition - rect.top;
    this.host.style.left = `${leftPosition}px`;
    this.host.style.top = `${topPosition}px`;
  }

  private getNormalizedRect(target: Window | HTMLElement): DOMRect {
    if (target === document.documentElement || target === window) {
      return {
        top: 0,
        left: 0,
        width: window.innerWidth,
        height: window.innerHeight,
        right: window.innerWidth,
        bottom: window.innerHeight,
      } as DOMRect;
    }
    return (target as HTMLElement).getBoundingClientRect();
  }
}
