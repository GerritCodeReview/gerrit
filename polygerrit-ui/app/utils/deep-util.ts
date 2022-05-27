/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
export function deepEqual<T>(a: T, b: T): boolean {
  if (a === b) return true;
  if (a === undefined || b === undefined) return false;
  if (a === null || b === null) return false;
  if (a instanceof Date && b instanceof Date)
    return a.getTime() === b.getTime();

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
