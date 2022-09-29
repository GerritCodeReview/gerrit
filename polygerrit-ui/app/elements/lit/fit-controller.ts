/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ReactiveController, ReactiveControllerHost} from 'lit';

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
  host: ReactiveControllerHost & HTMLElement;

  /**
   * The orientation against which to align the element horizontally
   * relative to the `positionTarget`. Possible values are "left", "right",
   * "center", "auto".
   */
  private horizontalAlign?: string | null;

  /**
   * The orientation against which to align the element vertically
   * relative to the `positionTarget`. Possible values are "top", "bottom",
   * "middle", "auto".
   */
  private verticalAlign?: string | null;

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
  private horizontalOffset = 0;

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
  private verticalOffset = 0;

  private fitInfo?: FitInfo | null;

  constructor(
    host: ReactiveControllerHost & HTMLElement,
    horizontalOffset?: number,
    verticalOffset?: number,
    horizontalAlign?: string,
    verticalAlign?: string
  ) {
    // TODO(dhruvsri): ensure this is passed from parent in constructor
    (this.host = host).addController(this);
    this.horizontalOffset = horizontalOffset ?? 0;
    this.verticalOffset = verticalOffset ?? 0;
    this.horizontalAlign = horizontalAlign;
    this.verticalAlign = verticalAlign;
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

    return parent;
  }

  /**
   * True if the element should be positioned instead of centered.
   *
   * @private
   */
  get shouldPosition() {
    return (
      (this.horizontalAlign || this.verticalAlign) &&
      this._defaultPositionTarget
    );
  }

  /** @override */
  attached() {}

  /** @override */
  detached() {}

  /**
   * Positions and fits the element into the `window` element.
   */
  fit() {
    this.position();
    this.constrain();
    this.center();
  }

  /**
   * Memoize information needed to position and size the target element.
   *
   * @suppress {deprecated}
   */
  _discoverInfo() {
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
  resetFit() {
    const info = this.fitInfo;
    if (info) {
      // cannot spread style here as it's a readOnly property
      for (var property in info.sizerInlineStyle) {
        // style is readonly
        this.host.style[property] = info.sizerInlineStyle[property];
      }
      for (var property in info.inlineStyle) {
        this.host.style[property] = info.inlineStyle[property];
      }
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
  position() {
    if (!this.shouldPosition) {
      // needs to be centered, and it is done after constrain.
      return;
    }
    this._discoverInfo();

    this.host.style.position = 'fixed';
    // Need border-box for margin/padding.
    this.host.style.boxSizing = 'border-box';
    // Set to 0, 0 in order to discover any offset caused by parent stacking
    // contexts.
    this.host.style.left = '0px';
    this.host.style.top = '0px';

    const rect = this.host.getBoundingClientRect();
    // TODO(dhruvsi): verify cast
    const positionRect = this.getNormalizedRect(
      this._defaultPositionTarget as HTMLElement
    );
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

    this.host.style.maxWidth = maxWidth + 'px';
    this.host.style.maxHeight = maxHeight + 'px';

    // Remove the offset caused by any stacking context.
    const leftPosition = left - rect.left;
    const topPosition = top - rect.top;
    this.host.style.left = `${leftPosition}px`;
    this.host.style.top = `${topPosition}px`;
  }

  /**
   * Constrains the size of the element to `window` by setting `max-height`
   * and/or `max-width`.
   */
  constrain() {
    if (this.shouldPosition) {
      return;
    }
    this._discoverInfo();

    const info = this.fitInfo!;
    // position at (0px, 0px) if not already positioned, so we can measure the
    // natural size.
    if (!info.positionedBy.vertically) {
      this.host.style.position = 'fixed';
      this.host.style.top = '0px';
    }
    if (!info.positionedBy.horizontally) {
      this.host.style.position = 'fixed';
      this.host.style.left = '0px';
    }

    // need border-box for margin/padding
    this.host.style.boxSizing = 'border-box';
    // constrain the width and height if not already set
    const rect = this.host.getBoundingClientRect();
    if (!info.sizedBy.height) {
      this.sizeDimension(
        rect,
        info.positionedBy.vertically,
        'top',
        'bottom',
        'Height'
      );
    }
    if (!info.sizedBy.width) {
      this.sizeDimension(
        rect,
        info.positionedBy.horizontally,
        'left',
        'right',
        'Width'
      );
    }
  }

  /**
   * @private
   */
  sizeDimension(
    rect: DOMRect,
    positionedBy: string | null,
    start: 'top' | 'left',
    end: 'bottom' | 'right',
    extent: string
  ) {
    const info = this.fitInfo;
    const fitRect = this.getNormalizedRect(window);
    const max = extent === 'Width' ? fitRect.width : fitRect.height;
    const flip = positionedBy === end;
    const offset = flip ? max - rect[end] : rect[start];
    const margin = info?.margin[flip ? start : end] ?? 0;
    // TODO(dhruvsri): verify sizingOffset is 0
    const sizingOffset = 0;
    this.host.style['max' + extent] =
      max - margin - offset - sizingOffset + 'px';
  }

  /**
   * Centers horizontally and vertically if not already positioned. This also
   * sets `position:fixed`.
   */
  center() {
    if (this.shouldPosition) {
      return;
    }
    this._discoverInfo();

    const positionedBy = this.fitInfo!.positionedBy;
    if (positionedBy.vertically && positionedBy.horizontally) {
      // Already positioned.
      return;
    }
    // Need position:fixed to center
    this.host.style.position = 'fixed';
    // Take into account the offset caused by parents that create stacking
    // contexts (e.g. with transform: translate3d). Translate to 0,0 and
    // measure the bounding rect.
    if (!positionedBy.vertically) {
      this.host.style.top = '0px';
    }
    if (!positionedBy.horizontally) {
      this.host.style.left = '0px';
    }
    // It will take in consideration margins and transforms
    const rect = this.host.getBoundingClientRect();
    const fitRect = this.getNormalizedRect(window);
    if (!positionedBy.vertically) {
      const top = fitRect.top - rect.top + (fitRect.height - rect.height) / 2;
      this.host.style.top = top + 'px';
    }
    if (!positionedBy.horizontally) {
      const left = fitRect.left - rect.left + (fitRect.width - rect.width) / 2;
      this.host.style.left = left + 'px';
    }
  }

  getNormalizedRect(target: HTMLElement | Window): DOMRect {
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

  getOffscreenArea(
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

  getPosition(
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
        top: positionRect.top + this.verticalOffset,
        left: positionRect.left + this.horizontalOffset,
      },
      {
        verticalAlign: 'top',
        horizontalAlign: 'right',
        top: positionRect.top + this.verticalOffset,
        left: positionRect.right - size.width - this.horizontalOffset,
      },
      {
        verticalAlign: 'bottom',
        horizontalAlign: 'left',
        top: positionRect.bottom - size.height - this.verticalOffset,
        left: positionRect.left + this.horizontalOffset,
      },
      {
        verticalAlign: 'bottom',
        horizontalAlign: 'right',
        top: positionRect.bottom - size.height - this.verticalOffset,
        left: positionRect.right - size.width - this.horizontalOffset,
      },
    ];

    // Consider auto as null for coding convenience.
    this.verticalAlign =
      this.verticalAlign === 'auto' ? null : this.verticalAlign;
    this.horizontalAlign =
      this.horizontalAlign === 'auto' ? null : this.horizontalAlign;

    if (!this.horizontalAlign || this.horizontalAlign === 'center') {
      positions.push({
        verticalAlign: 'top',
        horizontalAlign: 'center',
        top:
          positionRect.top +
          this.verticalOffset +
          (false ? positionRect.height : 0),
        left:
          positionRect.left -
          sizeNoMargins.width / 2 +
          positionRect.width / 2 +
          this.horizontalOffset,
      });
      positions.push({
        verticalAlign: 'bottom',
        horizontalAlign: 'center',
        top:
          positionRect.bottom -
          size.height -
          this.verticalOffset -
          (false ? positionRect.height : 0),
        left:
          positionRect.left -
          sizeNoMargins.width / 2 +
          positionRect.width / 2 +
          this.horizontalOffset,
      });
    }

    if (!this.verticalAlign || this.verticalAlign === 'middle') {
      positions.push({
        verticalAlign: 'middle',
        horizontalAlign: 'left',
        top:
          positionRect.top -
          sizeNoMargins.height / 2 +
          positionRect.height / 2 +
          this.verticalOffset,
        left:
          positionRect.left +
          this.horizontalOffset +
          (false ? positionRect.width : 0),
      });
      positions.push({
        verticalAlign: 'middle',
        horizontalAlign: 'right',
        top:
          positionRect.top -
          sizeNoMargins.height / 2 +
          positionRect.height / 2 +
          this.verticalOffset,
        left:
          positionRect.right -
          size.width -
          this.horizontalOffset -
          (false ? positionRect.width : 0),
      });
    }

    if (this.verticalAlign === 'middle' && this.horizontalAlign === 'center') {
      positions.push({
        verticalAlign: 'middle',
        horizontalAlign: 'center',
        top:
          positionRect.top -
          sizeNoMargins.height / 2 +
          positionRect.height / 2 +
          this.verticalOffset,
        left:
          positionRect.left -
          sizeNoMargins.width / 2 +
          positionRect.width / 2 +
          this.horizontalOffset,
      });
    }

    let position;
    for (let i = 0; i < positions.length; i++) {
      const candidate = positions[i];
      const vAlignOk = candidate.verticalAlign === this.verticalAlign;
      const hAlignOk = candidate.horizontalAlign === this.horizontalAlign;

      // If both this.verticalAlign and this.horizontalAlign are defined, return exact match.
      // For dynamicAlign and noOverlap we'll have more than one candidate, so
      // we'll have to check the offscreenArea to make the best choice.
      if (!false && vAlignOk && hAlignOk) {
        position = candidate;
        break;
      }

      // Align is ok if alignment preferences are respected. If no preferences,
      // it is considered ok.
      const alignOk =
        (!this.verticalAlign || vAlignOk) &&
        (!this.horizontalAlign || hAlignOk);

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
