/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// NOTE: This algorithm has the following limitations:
// It does not support deep-value-equality of values in sets that are not
// `===`.  The same applies for keys in a map.
export function deepEqual<T>(a: T, b: T): boolean {
  // The pairs of objects that are currently being compared. If a pair is
  // encountered again while on the stack, we shouldn't go any deeper, as we
  // would only be walking through same pairs again infinitely. Such pairs are
  // equal as long as all non-recursive pairs are equal, ie. given an infinite
  // traversal we would've never reached a pair of values that are not equal to
  // each other.
  const onStackValuePair = new Map();
  // Cache of compared object instances. This allows as to avoid comparing same
  // pair of large objects repeatedly in cases where the reference to the same
  // object is stored in many different attributes in the tree.
  const equalValues = new Map();
  function deepEqualImpl(a: unknown, b: unknown) {
    if (a === b) return true;
    if (a === undefined || b === undefined) return false;
    if (a === null || b === null) return false;
    if (a instanceof Date || b instanceof Date) {
      if (!(a instanceof Date && b instanceof Date)) return false;
      return a.getTime() === b.getTime();
    }

    // Check cache first for container types.
    if (equalValues?.get(a)?.has(b)) return true;

    if (a instanceof Set || b instanceof Set) {
      if (!(a instanceof Set && b instanceof Set)) return false;
      if (a.size !== b.size) return false;
      for (const ai of a) if (!b.has(ai)) return false;
      equalValues.set(a, (equalValues.get(a) ?? new Set()).add(b));
      return true;
    }
    if (a instanceof Map || b instanceof Map) {
      if (!(a instanceof Map && b instanceof Map)) return false;
      if (a.size !== b.size) return false;
      if (onStackValuePair.get(a)?.has(b)) return true;
      onStackValuePair.set(a, (onStackValuePair.get(a) ?? new Set()).add(b));

      for (const [aKey, aValue] of a.entries()) {
        if (!b.has(aKey) || !deepEqualImpl(aValue, b.get(aKey))) return false;
      }
      onStackValuePair.get(a)!.delete(b);
      equalValues.set(a, (equalValues.get(a) ?? new Set()).add(b));
      return true;
    }

    if (typeof a === 'object') {
      if (typeof b !== 'object') return false;
      if (onStackValuePair.get(a)?.has(b)) return true;
      onStackValuePair.set(a, (onStackValuePair.get(a) ?? new Set()).add(b));

      const aObj = a as Record<string, unknown>;
      const bObj = b as Record<string, unknown>;
      const aKeys = Object.keys(aObj);
      const bKeys = Object.keys(bObj);
      if (aKeys.length !== bKeys.length) return false;
      for (const key of aKeys) {
        if (!deepEqualImpl(aObj[key], bObj[key])) return false;
      }
      onStackValuePair.get(a)!.delete(b);
      equalValues.set(a, (equalValues.get(a) ?? new Set()).add(b));
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
