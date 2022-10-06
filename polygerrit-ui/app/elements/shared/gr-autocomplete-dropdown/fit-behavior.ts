/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import '@polymer/polymer/polymer-legacy';

// `FitBehavior` fits an element in another element using `max-height`
// and `max-width`, and optionally centers it in the window or another element.

// The element will only be sized and/or positioned if it has not already been
// sized and/or positioned by CSS.

// CSS properties            | Action
// --------------------------|-------------------------------------------
// `position` set            | Element is not centered horizontally or vertically
// `top` or `bottom` set     | Element is not vertically centered
// `left` or `right` set     | Element is not horizontally centered
// `max-height` set          | Element respects `max-height`
// `max-width` set           | Element respects `max-width`

// `FitBehavior` can position an element into another element using
// `verticalAlign` and `horizontalAlign`. This will override the element's css
// position.

// <div class="container">
//   <iron-fit-impl vertical-align="top" horizontal-align="auto">
//     Positioned into the container
//   </iron-fit-impl>
// </div>

// Use `noOverlap` to position the element around another element without
// overlapping it.

// <div class="container">
//   <iron-fit-impl no-overlap vertical-align="auto" horizontal-align="auto">
//     Positioned around the container
//   </iron-fit-impl>
// </div>

// Use `horizontalOffset, verticalOffset` to offset the element from its
// `positionTarget`; `FitBehavior` will collapse these in order to
// keep the element within `fitInto` boundaries, while preserving the element's
// CSS margin values.

// <div class="container">
//   <iron-fit-impl vertical-align="top" vertical-offset="20">
//     With vertical offset
//   </iron-fit-impl>
// </div>

export const FitBehavior = {

  properties: {

    /**
     * The element that will receive a `max-height`/`width`. By default it is
     * the same as `this`, but it can be set to a child element. This is useful,
     * for example, for implementing a scrolling region inside the element.
     *
     * @type {!Element}
     */
    sizingTarget: {
      type: Object,
      value() {
        return this;
      },
    },

    /**
     * The element to fit `this` into.
     */
    fitInto: {type: Object, value: window},

    /**
     * Will position the element around the positionTarget without overlapping
     * it.
     */
    noOverlap: {type: Boolean},

    /**
     * The element that should be used to position the element. If not set, it
     * will default to the parent node.
     *
     */
    positionTarget: {type: Element},

    /**
     * The orientation against which to align the element horizontally
     * relative to the `positionTarget`. Possible values are "left", "right",
     * "center", "auto".
     */
    horizontalAlign: {type: String},

    /**
     * The orientation against which to align the element vertically
     * relative to the `positionTarget`. Possible values are "top", "bottom",
     * "middle", "auto".
     */
    verticalAlign: {type: String},

    /**
     * If true, it will use `horizontalAlign` and `verticalAlign` values as
     * preferred alignment and if there's not enough space, it will pick the
     * values which minimize the cropping.
     */
    dynamicAlign: {type: Boolean},

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
    horizontalOffset: {type: Number, value: 0, notify: true},

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
    verticalOffset: {type: Number, value: 0, notify: true},

    /** @type {?Object} */
    _fitInfo: {type: Object},
  },

  /**
   * The element that should be used to position the element,
   * if no position target is configured.
   */
  get _defaultPositionTarget() {
    let parent = this.parentNode;

    if (parent && parent.nodeType === Node.DOCUMENT_FRAGMENT_NODE) {
      parent = parent.host;
    }

    return parent;
  },

  /**
   * The horizontal align value, accounting for the RTL/LTR text direction.
   */
  get _localeHorizontalAlign() {
    if (this._isRTL) {
      // In RTL, "left" becomes "right".
      if (this.horizontalAlign === 'right') {
        return 'left';
      }
      if (this.horizontalAlign === 'left') {
        return 'right';
      }
    }
    return this.horizontalAlign;
  },

  /**
   * True if the element should be positioned instead of centered.
   *
   * @private
   */
  get __shouldPosition() {
    return (this.horizontalAlign || this.verticalAlign) && this.positionTarget;
  },

  /**
   * True if the component is RTL.
   *
   * @private
   */
  get _isRTL() {
    // Memoize this to avoid expensive calculations & relayouts.
    // Make sure we do it only once
    if (typeof this._memoizedIsRTL === 'undefined') {
      this._memoizedIsRTL = window.getComputedStyle(this).direction == 'rtl';
    }
    return this._memoizedIsRTL;
  },

  /** @override */
  attached() {
    this.positionTarget = this.positionTarget || this._defaultPositionTarget;
  },

  /** @override */
  detached() {},

  /**
   * Positions and fits the element into the `fitInto` element.
   */
  fit() {
    this.position();
    this.constrain();
    this.center();
  },

  /**
   * Memoize information needed to position and size the target element.
   *
   */
  _discoverInfo() {
    if (this._fitInfo) {
      return;
    }
    const target = window.getComputedStyle(this);
    const sizer = window.getComputedStyle(this.sizingTarget);

    this._fitInfo = {
      inlineStyle: {
        top: this.style.top || '',
        left: this.style.left || '',
        position: this.style.position || '',
      },
      sizerInlineStyle: {
        maxWidth: this.sizingTarget.style.maxWidth || '',
        maxHeight: this.sizingTarget.style.maxHeight || '',
        boxSizing: this.sizingTarget.style.boxSizing || '',
      },
      positionedBy: {
        vertically: target.top !== 'auto' ?
          'top' :
          (target.bottom !== 'auto' ? 'bottom' : null),
        horizontally: target.left !== 'auto' ?
          'left' :
          (target.right !== 'auto' ? 'right' : null),
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
  },

  /**
   * Resets the target element's position and size constraints, and clear
   * the memoized data.
   */
  resetFit() {
    const info = this._fitInfo || {};
    Object.assign(this.host.style, info.sizerInlineStyle);
    Object.assign(this.host.style, info.inlineStyle);
    this._fitInfo = null;
  },

  /**
   * Equivalent to calling `resetFit()` and `fit()`. Useful to call this after
   * the element or the `fitInto` element has been resized, or if any of the
   * positioning properties (e.g. `horizontalAlign, verticalAlign`) is updated.
   * It preserves the scroll position of the sizingTarget.
   */
  refit() {
    const scrollLeft = this.sizingTarget.scrollLeft;
    const scrollTop = this.sizingTarget.scrollTop;
    this.resetFit();
    this.fit();
    this.sizingTarget.scrollLeft = scrollLeft;
    this.sizingTarget.scrollTop = scrollTop;
  },

  /**
   * Positions the element according to `horizontalAlign, verticalAlign`.
   */
  position() {
    if (!this.__shouldPosition) {
      // needs to be centered, and it is done after constrain.
      return;
    }
    this._discoverInfo();

    this.style.position = 'fixed';
    // Need border-box for margin/padding.
    this.sizingTarget.style.boxSizing = 'border-box';
    // Set to 0, 0 in order to discover any offset caused by parent stacking
    // contexts.
    this.style.left = '0px';
    this.style.top = '0px';

    const rect = this.getBoundingClientRect();
    const positionRect = this.__getNormalizedRect(this.positionTarget);
    const fitRect = this.__getNormalizedRect(this.fitInto);

    const margin = this._fitInfo.margin;

    // Consider the margin as part of the size for position calculations.
    const size = {
      width: rect.width + margin.left + margin.right,
      height: rect.height + margin.top + margin.bottom,
    };

    const position = this.__getPosition(
        this._localeHorizontalAlign, this.verticalAlign, size, rect,
        positionRect, fitRect);

    let left = position.left + margin.left;
    let top = position.top + margin.top;

    // We first limit right/bottom within fitInto respecting the margin,
    // then use those values to limit top/left.
    const right = Math.min(fitRect.right - margin.right, left + rect.width);
    const bottom = Math.min(fitRect.bottom - margin.bottom, top + rect.height);

    // Keep left/top within fitInto respecting the margin.
    left = Math.max(
        fitRect.left + margin.left,
        Math.min(left, right - this._fitInfo.sizedBy.minWidth));
    top = Math.max(
        fitRect.top + margin.top,
        Math.min(top, bottom - this._fitInfo.sizedBy.minHeight));

    // Use right/bottom to set maxWidth/maxHeight, and respect
    // minWidth/minHeight.
    const maxWidth = Math.max(right - left, this._fitInfo.sizedBy.minWidth);
    const maxHeight = Math.max(bottom - top, this._fitInfo.sizedBy.minHeight);
    this.sizingTarget.style.maxWidth = maxWidth + 'px';
    this.sizingTarget.style.maxHeight = maxHeight + 'px';

    // Remove the offset caused by any stacking context.
    const leftPosition = left - rect.left;
    const topPosition = top - rect.top;
    this.style.left = `${leftPosition}px`;
    this.style.top = `${topPosition}px`;
  },

  /**
   * Constrains the size of the element to `fitInto` by setting `max-height`
   * and/or `max-width`.
   */
  constrain() {
    if (this.__shouldPosition) {
      return;
    }
    this._discoverInfo();

    const info = this._fitInfo;
    // position at (0px, 0px) if not already positioned, so we can measure the
    // natural size.
    if (!info.positionedBy.vertically) {
      this.style.position = 'fixed';
      this.style.top = '0px';
    }
    if (!info.positionedBy.horizontally) {
      this.style.position = 'fixed';
      this.style.left = '0px';
    }

    // need border-box for margin/padding
    this.sizingTarget.style.boxSizing = 'border-box';
    // constrain the width and height if not already set
    const rect = this.getBoundingClientRect();
    if (!info.sizedBy.height) {
      this.__sizeDimension(
          rect, info.positionedBy.vertically, 'top', 'bottom', 'Height');
    }
    if (!info.sizedBy.width) {
      this.__sizeDimension(
          rect, info.positionedBy.horizontally, 'left', 'right', 'Width');
    }
  },

  /**
   * @protected
   * @deprecated
   */
  _sizeDimension(rect, positionedBy, start, end, extent) {
    this.__sizeDimension(rect, positionedBy, start, end, extent);
  },

  /**
   * @private
   */
  __sizeDimension(rect, positionedBy, start, end, extent) {
    const info = this._fitInfo;
    const fitRect = this.__getNormalizedRect(this.fitInto);
    const max = extent === 'Width' ? fitRect.width : fitRect.height;
    const flip = (positionedBy === end);
    const offset = flip ? max - rect[end] : rect[start];
    const margin = info.margin[flip ? start : end];
    const offsetExtent = 'offset' + extent;
    const sizingOffset = this[offsetExtent] - this.sizingTarget[offsetExtent];
    this.sizingTarget.style['max' + extent] =
        (max - margin - offset - sizingOffset) + 'px';
  },

  /**
   * Centers horizontally and vertically if not already positioned. This also
   * sets `position:fixed`.
   */
  center() {
    if (this.__shouldPosition) {
      return;
    }
    this._discoverInfo();

    const positionedBy = this._fitInfo.positionedBy;
    if (positionedBy.vertically && positionedBy.horizontally) {
      // Already positioned.
      return;
    }
    // Need position:fixed to center
    this.style.position = 'fixed';
    // Take into account the offset caused by parents that create stacking
    // contexts (e.g. with transform: translate3d). Translate to 0,0 and
    // measure the bounding rect.
    if (!positionedBy.vertically) {
      this.style.top = '0px';
    }
    if (!positionedBy.horizontally) {
      this.style.left = '0px';
    }
    // It will take in consideration margins and transforms
    const rect = this.getBoundingClientRect();
    const fitRect = this.__getNormalizedRect(this.fitInto);
    if (!positionedBy.vertically) {
      const top = fitRect.top - rect.top + (fitRect.height - rect.height) / 2;
      this.style.top = top + 'px';
    }
    if (!positionedBy.horizontally) {
      const left = fitRect.left - rect.left + (fitRect.width - rect.width) / 2;
      this.style.left = left + 'px';
    }
  },

  __getNormalizedRect(target) {
    if (target === document.documentElement || target === window) {
      return {
        top: 0,
        left: 0,
        width: window.innerWidth,
        height: window.innerHeight,
        right: window.innerWidth,
        bottom: window.innerHeight,
      };
    }
    return target.getBoundingClientRect();
  },

  __getOffscreenArea(position, size, fitRect) {
    const verticalCrop = Math.min(0, position.top) +
        Math.min(0, fitRect.bottom - (position.top + size.height));
    const horizontalCrop = Math.min(0, position.left) +
        Math.min(0, fitRect.right - (position.left + size.width));
    return Math.abs(verticalCrop) * size.width +
        Math.abs(horizontalCrop) * size.height;
  },

  __getPosition(
      hAlign, vAlign, size, sizeNoMargins, positionRect, fitRect) {
    // All the possible configurations.
    // Ordered as top-left, top-right, bottom-left, bottom-right.
    const positions = [
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

    if (this.noOverlap) {
      // Duplicate.
      for (let i = 0, l = positions.length; i < l; i++) {
        const copy = {};
        for (const key of Object.keys(positions[i])) {
          copy[key] = positions[i][key];
        }
        positions.push(copy);
      }
      // Horizontal overlap only.
      positions[0].top = positions[1].top += positionRect.height;
      positions[2].top = positions[3].top -= positionRect.height;
      // Vertical overlap only.
      positions[4].left = positions[6].left += positionRect.width;
      positions[5].left = positions[7].left -= positionRect.width;
    }

    // Consider auto as null for coding convenience.
    vAlign = vAlign === 'auto' ? null : vAlign;
    hAlign = hAlign === 'auto' ? null : hAlign;

    if (!hAlign || hAlign === 'center') {
      positions.push({
        verticalAlign: 'top',
        horizontalAlign: 'center',
        top: positionRect.top + this.verticalOffset +
            (this.noOverlap ? positionRect.height : 0),
        left: positionRect.left - sizeNoMargins.width / 2 +
            positionRect.width / 2 + this.horizontalOffset,
      });
      positions.push({
        verticalAlign: 'bottom',
        horizontalAlign: 'center',
        top: positionRect.bottom - size.height - this.verticalOffset -
            (this.noOverlap ? positionRect.height : 0),
        left: positionRect.left - sizeNoMargins.width / 2 +
            positionRect.width / 2 + this.horizontalOffset,
      });
    }

    if (!vAlign || vAlign === 'middle') {
      positions.push({
        verticalAlign: 'middle',
        horizontalAlign: 'left',
        top: positionRect.top - sizeNoMargins.height / 2 +
            positionRect.height / 2 + this.verticalOffset,
        left: positionRect.left + this.horizontalOffset +
            (this.noOverlap ? positionRect.width : 0),
      });
      positions.push({
        verticalAlign: 'middle',
        horizontalAlign: 'right',
        top: positionRect.top - sizeNoMargins.height / 2 +
            positionRect.height / 2 + this.verticalOffset,
        left: positionRect.right - size.width - this.horizontalOffset -
            (this.noOverlap ? positionRect.width : 0),
      });
    }

    if (vAlign === 'middle' && hAlign === 'center') {
      positions.push({
        verticalAlign: 'middle',
        horizontalAlign: 'center',
        top: positionRect.top - sizeNoMargins.height / 2 +
            positionRect.height / 2 + this.verticalOffset,
        left: positionRect.left - sizeNoMargins.width / 2 +
            positionRect.width / 2 + this.horizontalOffset,
      });
    }

    let position;
    for (let i = 0; i < positions.length; i++) {
      const candidate = positions[i];
      const vAlignOk = candidate.verticalAlign === vAlign;
      const hAlignOk = candidate.horizontalAlign === hAlign;

      // If both vAlign and hAlign are defined, return exact match.
      // For dynamicAlign and noOverlap we'll have more than one candidate, so
      // we'll have to check the offscreenArea to make the best choice.
      if (!this.dynamicAlign && !this.noOverlap && vAlignOk && hAlignOk) {
        position = candidate;
        break;
      }

      // Align is ok if alignment preferences are respected. If no preferences,
      // it is considered ok.
      const alignOk = (!vAlign || vAlignOk) && (!hAlign || hAlignOk);

      // Filter out elements that don't match the alignment (if defined).
      // With dynamicAlign, we need to consider all the positions to find the
      // one that minimizes the cropped area.
      if (!this.dynamicAlign && !alignOk) {
        continue;
      }

      candidate.offscreenArea =
          this.__getOffscreenArea(candidate, size, fitRect);
      // If not cropped and respects the align requirements, keep it.
      // This allows to prefer positions overlapping horizontally over the
      // ones overlapping vertically.
      if (candidate.offscreenArea === 0 && alignOk) {
        position = candidate;
        break;
      }
      position = position || candidate;
      const diff = candidate.offscreenArea - position.offscreenArea;
      // Check which crops less. If it crops equally, check if at least one
      // align setting is ok.
      if (diff < 0 || (diff === 0 && (vAlignOk || hAlignOk))) {
        position = candidate;
      }
    }

    return position;
  },

};
