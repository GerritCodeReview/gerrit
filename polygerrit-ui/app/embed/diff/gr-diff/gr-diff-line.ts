/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  GrDiffLine as GrDiffLineApi,
  GrDiffLineType,
  LineNumber,
  Side,
} from '../../../api/diff';

export {GrDiffLineType};
export type {LineNumber};

export const FILE = 'FILE';

export class GrDiffLine implements GrDiffLineApi {
  constructor(
    readonly type: GrDiffLineType,
    public beforeNumber: LineNumber = 0,
    public afterNumber: LineNumber = 0
  ) {}

  hasIntralineInfo = false;

  highlights: Highlights[] = [];

  text = '';

  lineNumber(side: Side) {
    return side === Side.LEFT ? this.beforeNumber : this.afterNumber;
  }

  // TODO(TS): remove this properties
  static readonly Type = GrDiffLineType;

  static readonly File = FILE;

  getIntraStart() {
    if (this.highlights.length === 0) return this.text.length;
    return this.highlights[0].startIndex;
  }

  getIntraEnd() {
    if (this.highlights.length === 0) return 0 - this.text.length;
    return (
      (this.highlights[this.highlights.length - 1].endIndex ??
        this.text.length) - this.text.length
    );
  }

  innerText() {
    if (!this.hasIntralineInfo) return this.text;
    if (this.highlights.length === 0) return '';
    const start = this.highlights[0].startIndex;
    const end =
      this.highlights[this.highlights.length - 1].endIndex ?? this.text.length;
    return this.text.substring(start, Math.min(end, this.text.length));
  }
}

/**
 * A line highlight object consists of three fields:
 * - contentIndex: The index of the chunk `content` field (the line
 *   being referred to).
 * - startIndex: Index of the character where the highlight should begin.
 * - endIndex: (optional) Index of the character where the highlight should
 *   end. If omitted, the highlight is meant to be a continuation onto the
 *   next line.
 */
export interface Highlights {
  contentIndex: number;
  startIndex: number;
  endIndex?: number;
}

export const BLANK_LINE = new GrDiffLine(GrDiffLineType.BLANK);
