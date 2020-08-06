/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export type LineNumber = number | 'FILE';

export class GrDiffLine {
  constructor(
    readonly type: GrDiffLineType,
    public beforeNumber: LineNumber = 0,
    public afterNumber: LineNumber = 0
  ) {}

  hasIntralineInfo = false;

  readonly highlights: Highlights[] = [];

  text = '';
}

export enum GrDiffLineType {
  ADD = 'add',
  BOTH = 'both',
  BLANK = 'blank',
  REMOVE = 'remove',
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

export const FILE = 'FILE';

export const BLANK_LINE = new GrDiffLine(GrDiffLineType.BLANK);
