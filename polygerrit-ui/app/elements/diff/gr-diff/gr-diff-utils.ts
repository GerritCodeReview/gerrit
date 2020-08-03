/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {CommentRange} from '../../../types/common';

export enum DiffSide {
  LEFT = 'left',
  RIGHT = 'right',
}

/**
 * Compare two ranges. Either argument may be falsy, but will only return
 * true if both are falsy or if neither are falsy and have the same position
 * values.
 */
export function rangesEqual(a: CommentRange, b: CommentRange): boolean {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  return (
    a.start_line === b.start_line &&
    a.start_character === b.start_character &&
    a.end_line === b.end_line &&
    a.end_character === b.end_character
  );
}
