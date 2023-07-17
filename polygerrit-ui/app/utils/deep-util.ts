/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// NOTE: This algorithm has the following limitations:
// It does not support deep-value-equality of values in sets that are not
// `===`.  The same applies for keys in a map.
export function deepEqual<T>(a: T, b: T): boolean {
  // These maps keep track of whether we've already seen an object for either
  // `a` (`left`) or `b` (`right`).  They store the corresponding object they
  // were being compared against. This enables us to short-circuit deep-equaling
  // objects in case we've already seen them before and the corresponding other
  // object has the same pointer.
  const left = new Map();
  const right = new Map();
  function deepEqualImpl(a: unknown, b: unknown) {
    if (a === b) return true;
    if (a === undefined || b === undefined) return false;
    if (a === null || b === null) return false;
    if (a instanceof Date || b instanceof Date) {
      if (!(a instanceof Date && b instanceof Date)) return false;
      return a.getTime() === b.getTime();
    }

    if (a instanceof Set || b instanceof Set) {
      if (!(a instanceof Set && b instanceof Set)) return false;
      if (a.size !== b.size) return false;
      for (const ai of a) if (!b.has(ai)) return false;
      return true;
    }
    if (a instanceof Map || b instanceof Map) {
      if (!(a instanceof Map && b instanceof Map)) return false;
      if (a.size !== b.size) return false;
      if (left.get(a)?.has(a)) return true;
      if (right.get(b)?.has(a)) return true;
      left.set(a, (left.get(a) ?? new Set()).add(b));
      right.set(b, (right.get(b) ?? new Set()).add(a));
      for (const [aKey, aValue] of a.entries()) {
        if (!b.has(aKey) || !deepEqualImpl(aValue, b.get(aKey))) return false;
      }
      return true;
    }

    if (typeof a === 'object') {
      if (typeof b !== 'object') return false;
      if (left.get(a)?.has(a)) return true;
      if (right.get(b)?.has(a)) return true;
      left.set(a, (left.get(a) ?? new Set()).add(b));
      right.set(b, (right.get(b) ?? new Set()).add(a));

      const aObj = a as Record<string, unknown>;
      const bObj = b as Record<string, unknown>;
      const aKeys = Object.keys(aObj);
      const bKeys = Object.keys(bObj);
      if (aKeys.length !== bKeys.length) return false;
      for (const key of aKeys) {
        if (!deepEqualImpl(aObj[key], bObj[key])) return false;
      }
      return true;
    }
    return false;
  }

  return deepEqualImpl(a, b);
}

export function notDeepEqual<T>(a: T, b: T): boolean {
  return !deepEqual(a, b);
}

/**
 * @param obj Object
 */
export function deepClone(obj?: object) {
  if (!obj) return undefined;
  return JSON.parse(JSON.stringify(obj));
}
