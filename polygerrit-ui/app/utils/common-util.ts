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

/**
 * @fileoverview Functions in this file contains some widely used
 * code patterns. If you noticed a repeated code and none of the existing util
 * files are appropriate for it - you can wrap the code in a function and put it
 * here. If you notice that several functions can be group together - create
 * a separate util file for them.
 */

/**
 * Wrapper for the Object.prototype.hasOwnProperty method
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function hasOwnProperty(obj: any, prop: PropertyKey) {
  // Typescript rules don't allow to use obj.hasOwnProperty directly
  return Object.prototype.hasOwnProperty.call(obj, prop);
}
