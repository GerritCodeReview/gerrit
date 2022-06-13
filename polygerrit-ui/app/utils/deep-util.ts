/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
export function deepEqual<T>(a: T, b: T): boolean {
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
    for (const [aKey, aValue] of a.entries()) {
      if (!b.has(aKey) || !deepEqual(aValue, b.get(aKey))) return false;
    }
    return true;
  }

  if (typeof a === 'object') {
    if (typeof b !== 'object') return false;
    const aObj = a as Record<string, unknown>;
    const bObj = b as Record<string, unknown>;
    const aKeys = Object.keys(aObj);
    const bKeys = Object.keys(bObj);
    if (aKeys.length !== bKeys.length) return false;
    for (const key of aKeys) {
      if (!deepEqual(aObj[key], bObj[key])) return false;
    }
    return true;
  }

  return false;
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
