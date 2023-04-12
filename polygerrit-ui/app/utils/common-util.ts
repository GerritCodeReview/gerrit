/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {fireAlert} from './event-util';

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
export function assert(
  condition: boolean,
  errorMessage: string
): asserts condition {
  if (!condition) throw new Error(errorMessage);
}

/**
 * Throws an error if the property is not defined.
 */
export function assertIsDefined<T>(
  val: T,
  variableName = 'variable'
): asserts val is NonNullable<T> {
  if (val === undefined || val === null) {
    throw new Error(`${variableName} is not defined`);
  }
}

export function queryAll<E extends Element = Element>(
  el: Element,
  selector: string
): NodeListOf<E> {
  if (!el) throw new Error('element not defined');
  if (el.shadowRoot) {
    const r = el.shadowRoot.querySelectorAll<E>(selector);
    if (r.length > 0) return r;
  }
  return el.querySelectorAll<E>(selector);
}

export function query<E extends Element = Element>(
  el: Element | null | undefined,
  selector: string
): E | undefined {
  if (!el) return undefined;
  if (el.shadowRoot) {
    const r = el.shadowRoot.querySelector<E>(selector);
    if (r) return r;
  }
  return el.querySelector<E>(selector) ?? undefined;
}

export function queryAndAssert<E extends Element = Element>(
  el: Element | null | undefined,
  selector: string
): E {
  const found = query<E>(el, selector);
  if (!found) throw new Error(`selector '${selector}' did not match anything'`);
  return found;
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

/**
 * Add value, if the set does not contain it. Otherwise remove it.
 */
export function toggleSet<T>(set: Set<T>, value: T): void {
  if (set.has(value)) {
    set.delete(value);
  } else {
    set.add(value);
  }
}

export function toggle<T>(array: T[], item: T): T[] {
  if (array.includes(item)) {
    return array.filter(r => r !== item);
  } else {
    return array.concat([item]);
  }
}

export function unique<T>(item: T, index: number, array: T[]) {
  return array.indexOf(item) === index;
}

/**
 * Returns the elements that are present in every sub-array. If a compareBy
 * predicate is passed in, it will be used instead of strict equality. A new
 * array is always returned even if there is already just a single array.
 */
export function intersection<T>(
  arrays: T[][],
  compareBy: (t: T, u: T) => boolean = (t, u) => t === u
): T[] {
  // Array.prototype.reduce needs either an initialValue or a non-empty array.
  // Since there is no good initialValue for intersecting (∅ ∩ X = ∅), the
  // empty array must be checked separately.
  if (arrays.length === 0) {
    return [];
  }
  if (arrays.length === 1) {
    return [...arrays[0]];
  }
  return arrays.reduce((result, array) =>
    result.filter(t => array.find(u => compareBy(t, u)))
  );
}

/**
 * Returns the elements that are present in A but not present in B.
 */
export function difference<T>(
  a: T[],
  b: T[],
  compareBy: (t: T, u: T) => boolean = (t, u) => t === u
): T[] {
  return a.filter(aVal => !b.some(bVal => compareBy(aVal, bVal)));
}

export async function copyToClipbard(text: string, copyTargetName?: string) {
  await navigator.clipboard.writeText(text);
  fireAlert(document, `${copyTargetName ?? text} was copied to clipboard`);
}

/**
 * Produces strings such as `y364b4tm28n`.
 */
export function uuid() {
  return Math.random().toString(36).substring(2);
}
