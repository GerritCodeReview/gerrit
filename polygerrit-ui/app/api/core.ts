/**
 * @fileoverview Core API types for Gerrit.
 *
 * Core types are types used in many places in Gerrit, such as the Side enum.
 *
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

/**
 * The CommentRange entity describes the range of an inline comment.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#comment-range
 *
 * The range includes all characters from the start position, specified by
 * start_line and start_character, to the end position, specified by end_line
 * and end_character. The start position is inclusive and the end position is
 * exclusive.
 *
 * So, a range over part of a line will have start_line equal to end_line;
 * however a range with end_line set to 5 and end_character equal to 0 will not
 * include any characters on line 5.
 */
export interface CommentRange {
  /** The start line number of the range. (1-based) */
  start_line: number;

  /** The character position in the start line. (0-based) */
  start_character: number;

  /** The end line number of the range. (1-based) */
  end_line: number;

  /** The character position in the end line. (0-based) */
  end_character: number;
}
