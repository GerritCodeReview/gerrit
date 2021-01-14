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
  * Prevents "Duck-typing" and forces providing the
  * correct named type and not anything compatible.
  */
export type BrandType<T, BrandName extends string> = T &
  {[__brand in BrandName]: never};

/** Identifier for a patch set. */
export declare type PatchSetNum = BrandType<
  'PARENT' | 'edit' | number,
  '_patchSet'
>;

/**
 * Defines a patch ranges. Used as input for gr-rest-api-interface methods,
 * doesn't exist in Rest API
 */
export declare interface PatchRange {
  patchNum: PatchSetNum;
  basePatchNum: PatchSetNum;
}

/**
 * The CommentRange entity describes the range of an inline comment.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#comment-range
 */
export interface CommentRange {
  start_line: number;
  start_character: number;
  end_line: number;
  end_character: number;
}
