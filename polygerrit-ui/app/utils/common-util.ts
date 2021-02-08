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

// TODO(TS): move to common types once we have type utils
//  Required for constructor signature.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Constructor<T> = new (...args: any[]) => T;

/**
 * Use the function for compile-time checking that all possible input
 * values are processed
 */
export function assertNever(obj: never, msg: string): never {
  console.error(msg, obj);
  throw new Error(msg);
}

/**
 * Throws an error with the provided error message if the condition is false.
 */
export function check(
  condition: boolean,
  errorMessage: string
): asserts condition {
  if (!condition) throw new Error(errorMessage);
}

/**
 * Throws an error if the property is not defined.
 */
export function checkProperty(
  condition: boolean,
  propertyName: string
): asserts condition {
  check(condition, `missing required property '${propertyName}'`);
}

/**
 * Returns true, if both sets contain the same members.
 */
export function areSetsEqual<T>(a: Set<T>, b: Set<T>): boolean {
  if (a.size !== b.size) {
    return false;
  }
  return containsAll(a, b);
}

/**
 * Returns true, if 'set' contains 'subset'.
 */
export function containsAll<T>(set: Set<T>, subSet: Set<T>): boolean {
  for (const value of subSet) {
    if (!set.has(value)) {
      return false;
    }
  }
  return true;
}
