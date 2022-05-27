/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ImageDiffAction} from '../../../api/diff';

export interface Point {
  x: number;
  y: number;
}

export interface Dimensions {
  width: number;
  height: number;
}

export interface Rect {
  origin: Point;
  dimensions: Dimensions;
}

export interface FittedContent {
  top: number;
  left: number;
  width: number;
  height: number;
  scale: number;
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(value, max));
}

/**
 * Fits content of the given dimensions into the given frame, maintaining the
 * aspect ratio of the content and applying letterboxing / pillarboxing as
 * needed.
 */
export function fitToFrame(
  content: Dimensions,
  frame: Dimensions
): FittedContent {
  const contentAspectRatio = content.width / content.height;
  const frameAspectRatio = frame.width / frame.height;
  // If the content is wider than the frame, it will be letterboxed, otherwise
  // it will be pillarboxed. When letterboxed, content and frame width will
  // match exactly, when pillarboxed, content and frame height will match
  // exactly.
  const isLetterboxed = contentAspectRatio > frameAspectRatio;
  let width: number;
  let height: number;
  if (isLetterboxed) {
    width = Math.min(frame.width, content.width);
    height = content.height * (width / content.width);
  } else {
    height = Math.min(frame.height, content.height);
    width = content.width * (height / content.height);
  }
  const top = (frame.height - height) / 2;
  const left = (frame.width - width) / 2;
  const scale = width / content.width;
  return {top, left, width, height, scale};
}

function ensureInBounds(part: Rect, bounds: Dimensions): Rect {
  const x =
    part.dimensions.width <= bounds.width
      ? clamp(part.origin.x, 0, bounds.width - part.dimensions.width)
      : (bounds.width - part.dimensions.width) / 2;
  const y =
    part.dimensions.height <= bounds.height
      ? clamp(part.origin.y, 0, bounds.height - part.dimensions.height)
      : (bounds.height - part.dimensions.height) / 2;
  return {origin: {x, y}, dimensions: part.dimensions};
}

/**
 * Maintains a given frame inside given bounds, adjusting requested positions
 * for the frame as needed. This supports the non-destructive application of a
 * scaling factor, so that e.g. the magnification of an image can be changed
 * easily while keeping the frame centered over the same spot. Changing bounds
 * or frame size also keeps the frame position when possible.
 */
export class FrameConstrainer {
  private center: Point = {x: 0, y: 0};

  private frameSize: Dimensions = {width: 0, height: 0};

  private bounds: Dimensions = {width: 0, height: 0};

  private scale = 1;

  private unscaledFrame: Rect = {
    origin: {x: 0, y: 0},
    dimensions: {width: 0, height: 0},
  };

  private scaledFrame: Rect = {
    origin: {x: 0, y: 0},
    dimensions: {width: 0, height: 0},
  };

  getCenter(): Point {
    return {...this.center};
  }

  /**
   * Returns the frame at its original size, positioned within the given bounds
   * at the given scale; its origin will be in scaled bounds coordinates.
   *
   * Ex: for given bounds 100x50 and frame size 30x20, centered over (50, 25),
   * all at 1x scale, when setting scale to 2, this will return a frame of size
   * 30x20, centered over (100, 50), within bounds 200x100.
   *
   * Useful for positioning a viewport of fixed size over a magnified image.
   */
  getUnscaledFrame(): Rect {
    return {
      origin: {...this.unscaledFrame.origin},
      dimensions: {...this.unscaledFrame.dimensions},
    };
  }

  /**
   * Returns the scaled down frame–a scale of 2 will result in frame dimensions
   * being halved—position within the given bounds at 1x scale; its origin will
   * be in unscaled bounds coordinates.
   *
   * Ex: for given bounds 100x50 and frame size 30x20, centered over (50, 25),
   * all at 1x scale, when setting scale to 2, this will return a frame of size
   * 15x10, centered over (50, 25), within bounds 100x50.
   *
   * Useful for highlighting the magnified portion of an image as determined by
   * getUnscaledFrame() in an overview image of fixed size.
   */
  getScaledFrame(): Rect {
    return {
      origin: {...this.scaledFrame.origin},
      dimensions: {...this.scaledFrame.dimensions},
    };
  }

  /**
   * Requests the frame to be centered over the given point, in unscaled bounds
   * coordinates. This will keep the frame within the given bounds, also when
   * requesting a center point fully outside the given bounds.
   */
  requestCenter(center: Point) {
    this.center = {...center};

    this.ensureFrameInBounds();
  }

  /**
   * Sets the frame size, while keeping the frame within the given bounds, and
   * maintaining the current center if possible.
   */
  setFrameSize(frameSize: Dimensions) {
    if (frameSize.width <= 0 || frameSize.height <= 0) return;
    this.frameSize = {...frameSize};

    this.ensureFrameInBounds();
  }

  /**
   * Sets the bounds, while keeping the frame within them, and maintaining the
   * current center if possible.
   */
  setBounds(bounds: Dimensions) {
    if (bounds.width <= 0 || bounds.height <= 0) return;
    this.bounds = {...bounds};

    this.ensureFrameInBounds();
  }

  /**
   * Sets the applied scale, while keeping the frame within the given bounds,
   * and maintaining the current center if possible (both relevant moving from
   * a larger scale to a smaller scale).
   */
  setScale(scale: number) {
    if (!scale || scale <= 0) return;
    this.scale = scale;

    this.ensureFrameInBounds();
  }

  private ensureFrameInBounds() {
    const scaledCenter = {
      x: this.center.x * this.scale,
      y: this.center.y * this.scale,
    };
    const scaledBounds = {
      width: this.bounds.width * this.scale,
      height: this.bounds.height * this.scale,
    };
    const scaledFrameSize = {
      width: this.frameSize.width / this.scale,
      height: this.frameSize.height / this.scale,
    };

    const requestedUnscaledFrame = {
      origin: {
        x: scaledCenter.x - this.frameSize.width / 2,
        y: scaledCenter.y - this.frameSize.height / 2,
      },
      dimensions: this.frameSize,
    };
    const requestedScaledFrame = {
      origin: {
        x: this.center.x - scaledFrameSize.width / 2,
        y: this.center.y - scaledFrameSize.height / 2,
      },
      dimensions: scaledFrameSize,
    };

    this.unscaledFrame = ensureInBounds(requestedUnscaledFrame, scaledBounds);
    this.scaledFrame = ensureInBounds(requestedScaledFrame, this.bounds);

    this.center = {
      x: this.scaledFrame.origin.x + this.scaledFrame.dimensions.width / 2,
      y: this.scaledFrame.origin.y + this.scaledFrame.dimensions.height / 2,
    };
  }
}

export function createEvent(
  detail: ImageDiffAction
): CustomEvent<ImageDiffAction> {
  return new CustomEvent('image-diff-action', {
    detail,
    bubbles: true,
    composed: true,
  });
}
