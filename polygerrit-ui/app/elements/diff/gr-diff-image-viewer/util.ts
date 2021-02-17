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

export function fitToFrame(
    content: Dimensions, frame: Dimensions): FittedContent {
  const contentAspectRatio = content.width / content.height;
  const frameAspectRatio = frame.width / frame.height;
  // If the content is wider than the frame, it will be letterboxed, otherwise
  // it will be pillarboxed. When letterboxed, content and frame width will
  // match exactly, when pillarboxed, content and frame height will match
  // exactly.
  const isLetterboxed = contentAspectRatio > frameAspectRatio;
  let width;
  let height;
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
  const x = part.dimensions.width <= bounds.width ?
      clamp(part.origin.x, 0, bounds.width - part.dimensions.width) :
      (bounds.width - part.dimensions.width) / 2;
  const y = part.dimensions.height <= bounds.height ?
      clamp(part.origin.y, 0, bounds.height - part.dimensions.height) :
      (bounds.height - part.dimensions.height) / 2;
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
  private scale: number = 1;

  private frame:
      Rect = {origin: {x: 0, y: 0}, dimensions: {width: 0, height: 0}};

  getCenter(): Point {
    return {...this.center};
  }

  getFrame(): Rect {
    return {
      origin: {...this.frame.origin},
      dimensions: {...this.frame.dimensions},
    };
  }

  requestCenter(center: Point) {
    this.center = {...center};

    this.ensureFrameInBounds();
  }

  setFrameSize(frameSize: Dimensions) {
    if (frameSize.width <= 0 || frameSize.height <= 0) return;
    this.frameSize = {...frameSize};

    this.ensureFrameInBounds();
  }

  setBounds(bounds: Dimensions) {
    if (bounds.width <= 0 || bounds.height <= 0) return;
    this.bounds = {...bounds};

    this.ensureFrameInBounds();
  }

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
    const requestedFrame = {
      origin: {
        x: scaledCenter.x - this.frameSize.width / 2,
        y: scaledCenter.y - this.frameSize.height / 2,
      },
      dimensions: this.frameSize,
    };
    this.frame = ensureInBounds(requestedFrame, scaledBounds);
    this.center = {
      x: (this.frame.origin.x + this.frame.dimensions.width / 2) / this.scale,
      y: (this.frame.origin.y + this.frame.dimensions.height / 2) / this.scale,
    };
  }
}
