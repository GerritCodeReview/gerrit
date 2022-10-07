/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ReactiveController, ReactiveControllerHost} from 'lit';

/**
 * horizontalAlign
 * The orientation against which to align the element horizontally
 * relative to the `positionTarget`.
 * The only value supported initially is left
 */

/**
 * verticalAlign;
 * The orientation against which to align the element vertically
 * relative to the `positionTarget`.
 * The only value supported initially is top.
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
  inlineStyle: {
    top: string;
    left: string;
    position: string;
  };
  sizerInlineStyle: {
    maxWidth: string;
    maxHeight: string;
    boxSizing: string;
  };
  positionedBy: {
    vertically: string | null;
    horizontally: string | null;
  };
  sizedBy: {
    height: boolean;
    width: boolean;
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

interface Positions {
  verticalAlign: string;
  horizontalAlign: string;
  top: number;
  left: number;
  offscreenArea?: number;
}

/**
   `FitController` fits an element in another element using `max-height`
   and `max-width`, and optionally centers it in the window or another element.
 
   The element will only be sized and/or positioned if it has not already been
   sized and/or positioned by CSS.
 
   CSS properties            | Action
   --------------------------|-------------------------------------------
   `position` set            | Element is not centered horizontally or vertically
   `top` or `bottom` set     | Element is not vertically centered
   `left` or `right` set     | Element is not horizontally centered
   `max-height` set          | Element respects `max-height`
   `max-width` set           | Element respects `max-width`
 
   `FitController` can position an element into another element using
   `verticalAlign` and `horizontalAlign`. This will override the element's css
   position.
 
       <div class="container">
         <iron-fit-impl vertical-align="top" horizontal-align="auto">
           Positioned into the container
         </iron-fit-impl>
       </div>
 
   Use `noOverlap` to position the element around another element without
   overlapping it.
 
       <div class="container">
         <iron-fit-impl no-overlap vertical-align="auto" horizontal-align="auto">
           Positioned around the container
         </iron-fit-impl>
       </div>
 
   Use `horizontalOffset, verticalOffset` to offset the element from its
   `positionTarget`; `FitController` will collapse these in order to
   keep the element within `window` boundaries, while preserving the element's
   CSS margin values.
 
       <div class="container">
         <iron-fit-impl vertical-align="top" vertical-offset="20">
           With vertical offset
         </iron-fit-impl>
       </div>
 
 */
export class FitController implements ReactiveController {
  host: ReactiveControllerHost & HTMLElement & FitControllerHost;

  private positionTarget?: HTMLElement;

  private fitInfo?: FitInfo | null;

  constructor(host: ReactiveControllerHost & HTMLElement & FitControllerHost) {
    (this.host = host).addController(this);
    this.positionTarget = this.positionTarget || this._defaultPositionTarget;
  }

  hostConnected() {}

  hostDisconnected() {}

  /**
   * The element that should be used to position the element,
   * if no position target is configured.
   */
  get _defaultPositionTarget() {
    let parent = this.host.parentNode;

    if (parent && parent.nodeType === Node.DOCUMENT_FRAGMENT_NODE) {
      parent = (parent as ShadowRoot).host;
    }

    return parent as HTMLElement;
  }

  /**
   * Positions and fits the element into the `window` element.
   */
  fit() {
    this.position();
  }

  setPositionTarget(target: HTMLElement) {
    this.positionTarget = target;
  }

  /**
   * Memoize information needed to position and size the target element.
   *
   */
  private discoverInfo() {
    if (this.fitInfo) {
      return;
    }
    const target = (window as Window).getComputedStyle(this.host);
    const sizer = (window as Window).getComputedStyle(this.host);

    this.fitInfo = {
      inlineStyle: {
        top: this.host.style.top || '',
        left: this.host.style.left || '',
        position: this.host.style.position || '',
      },
      sizerInlineStyle: {
        maxWidth: this.host.style.maxWidth || '',
        maxHeight: this.host.style.maxHeight || '',
        boxSizing: this.host.style.boxSizing || '',
      },
      positionedBy: {
        vertically:
          target.top !== 'auto'
            ? 'top'
            : target.bottom !== 'auto'
            ? 'bottom'
            : null,
        horizontally:
          target.left !== 'auto'
            ? 'left'
            : target.right !== 'auto'
            ? 'right'
            : null,
      },
      sizedBy: {
        height: sizer.maxHeight !== 'none',
        width: sizer.maxWidth !== 'none',
        minWidth: parseInt(sizer.minWidth, 10) || 0,
        minHeight: parseInt(sizer.minHeight, 10) || 0,
      },
      margin: {
        top: parseInt(target.marginTop, 10) || 0,
        right: parseInt(target.marginRight, 10) || 0,
        bottom: parseInt(target.marginBottom, 10) || 0,
        left: parseInt(target.marginLeft, 10) || 0,
      },
    };
  }

  /**
   * Resets the target element's position and size constraints, and clear
   * the memoized data.
   */
  private resetFit() {
    const info = this.fitInfo;
    if (info) {
      Object.assign(this.host.style, info.sizerInlineStyle);
      Object.assign(this.host.style, info.inlineStyle);
    }
    this.fitInfo = null;
  }

  /**
   * Equivalent to calling `resetFit()` and `fit()`. Useful to call this after
   * the element or the `window` element has been resized, or if any of the
   * positioning properties (e.g. `horizontalAlign, verticalAlign`) is updated.
   * It preserves the scroll position of the sizingTarget.
   */
  refit() {
    const scrollLeft = this.host.scrollLeft;
    const scrollTop = this.host.scrollTop;
    this.resetFit();
    this.fit();
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
    // TODO(dhruvsi): verify cast
    const positionRect = this.getNormalizedRect(this.positionTarget!);
    const fitRect = this.getNormalizedRect(window);

    const margin = this.fitInfo!.margin;

    // Consider the margin as part of the size for position calculations.
    const size = {
      width: rect.width + margin.left + margin.right,
      height: rect.height + margin.top + margin.bottom,
    };

    const position = this.getPosition(size, rect, positionRect, fitRect);

    let left = (position?.left ?? 0) + margin.left;
    let top = (position?.top ?? 0) + margin.top;

    // We first limit right/bottom within window respecting the margin,
    // then use those values to limit top/left.
    const right = Math.min(fitRect.right - margin.right, left + rect.width);
    const bottom = Math.min(fitRect.bottom - margin.bottom, top + rect.height);

    // Keep left/top within fitInto respecting the margin.
    left = Math.max(
      fitRect.left + margin.left,
      Math.min(left, right - this.fitInfo!.sizedBy.minWidth)
    );
    top = Math.max(
      fitRect.top + margin.top,
      Math.min(top, bottom - this.fitInfo!.sizedBy.minHeight)
    );

    // Use right/bottom to set maxWidth/maxHeight, and respect
    // minWidth/minHeight.
    const maxWidth = Math.max(right - left, this.fitInfo!.sizedBy.minWidth);
    const maxHeight = Math.max(bottom - top, this.fitInfo!.sizedBy.minHeight);

    this.host.style.maxWidth = maxWidth.toString() + 'px';
    this.host.style.maxHeight = maxHeight.toString() + 'px';

    // Remove the offset caused by any stacking context.
    const leftPosition = left - rect.left;
    const topPosition = top - rect.top;
    this.host.style.left = `${leftPosition}px`;
    this.host.style.top = `${topPosition}px`;
  }

  private getNormalizedRect(target: HTMLElement | Window): DOMRect {
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

  private getOffscreenArea(
    position: Positions,
    size: {
      width: number;
      height: number;
    },
    fitRect: DOMRect
  ) {
    const verticalCrop =
      Math.min(0, position.top) +
      Math.min(0, fitRect.bottom - (position.top + size.height));
    const horizontalCrop =
      Math.min(0, position.left) +
      Math.min(0, fitRect.right - (position.left + size.width));
    return (
      Math.abs(verticalCrop) * size.width +
      Math.abs(horizontalCrop) * size.height
    );
  }

  private getPosition(
    size: {
      width: number;
      height: number;
    },
    sizeNoMargins: DOMRect,
    positionRect: DOMRect,
    fitRect: DOMRect
  ) {
    // All the possible configurations.
    // Ordered as top-left, top-right, bottom-left, bottom-right.
    const positions: Positions[] = [
      {
        verticalAlign: 'top',
        horizontalAlign: 'left',
        top: positionRect.top + this.host.verticalOffset,
        left: positionRect.left + this.host.horizontalOffset,
      },
      {
        verticalAlign: 'top',
        horizontalAlign: 'right',
        top: positionRect.top + this.host.verticalOffset,
        left: positionRect.right - size.width - this.host.horizontalOffset,
      },
      {
        verticalAlign: 'bottom',
        horizontalAlign: 'left',
        top: positionRect.bottom - size.height - this.host.verticalOffset,
        left: positionRect.left + this.host.horizontalOffset,
      },
      {
        verticalAlign: 'bottom',
        horizontalAlign: 'right',
        top: positionRect.bottom - size.height - this.host.verticalOffset,
        left: positionRect.right - size.width - this.host.horizontalOffset,
      },
    ];

    let position;
    for (let i = 0; i < positions.length; i++) {
      const candidate = positions[i];
      const vAlignOk = candidate.verticalAlign === 'top';
      const hAlignOk = candidate.horizontalAlign === 'left';

      // If both 'top' and 'left' are defined, return exact match.
      // For dynamicAlign and noOverlap we'll have more than one candidate, so
      // we'll have to check the offscreenArea to make the best choice.
      if (vAlignOk && hAlignOk) {
        position = candidate;
        break;
      }

      // Align is ok if alignment preferences are respected. If no preferences,
      // it is considered ok.
      const alignOk = (!'top' || vAlignOk) && (!'left' || hAlignOk);

      // Filter out elements that don't match the alignment (if defined).
      // With dynamicAlign, we need to consider all the positions to find the
      // one that minimizes the cropped area.
      if (!alignOk) {
        continue;
      }

      candidate.offscreenArea = this.getOffscreenArea(candidate, size, fitRect);
      // If not cropped and respects the align requirements, keep it.
      // This allows to prefer positions overlapping horizontally over the
      // ones overlapping vertically.
      if (candidate.offscreenArea === 0 && alignOk) {
        position = candidate;
        break;
      }
      position = position || candidate;
      const diff = candidate.offscreenArea - (position.offscreenArea ?? 0);
      // Check which crops less. If it crops equally, check if at least one
      // align setting is ok.
      if (diff < 0 || (diff === 0 && (vAlignOk || hAlignOk))) {
        position = candidate;
      }
    }

    return position;
  }
}
