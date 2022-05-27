/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  GrDiffLine as GrDiffLineApi,
  GrDiffLineType,
  LineNumber,
} from '../../../api/diff';

export {GrDiffLineType, LineNumber};

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

  // TODO(TS): remove this properties
  static readonly Type = GrDiffLineType;

  static readonly File = FILE;
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
