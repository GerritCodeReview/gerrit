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
 *
 * The type guard will also ensure if it returns true,
 * the obj to check should have the property, see example below:
 *
 * @example
 *
 * interface A {
 *   a: number;
 * }
 *
 * interface B {
 *   b: number;
 * }
 *
 * function testFn(a: A | B) {
 *   if (hasOwnProperty(a, "a")) {
 *     // with the type guard, typescript now knows that a should be A type
 *     // instead of A | B
 *     a.a = 2;
 *   }
 * // without the type guard, it will throw error:
 * // Property 'a' does not exist on type 'A | B'.
 * }
 */
export function hasOwnProperty<X extends {}, Y extends PropertyKey>(
  obj: X,
  prop: Y
): obj is X & Record<Y, unknown> {
  return Object.prototype.hasOwnProperty.call(obj, prop);
}
