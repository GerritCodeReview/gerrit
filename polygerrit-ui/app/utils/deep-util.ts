/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
export function deepEqual<T>(a: T, b: T): boolean {
  if (a === b) return true;
  if (a === undefined || b === undefined) return false;
  if (a === null || b === null) return false;
  if (a instanceof Date && b instanceof Date)
    return a.getTime() === b.getTime();

  if (a instanceof Set && b instanceof Set) {
    if (a.size !== b.size) return false;
    for (const ai of a) if (!b.has(ai)) return false;
    return true;
  }
  if (a instanceof Map && b instanceof Map) {
    if (a.size !== b.size) return false;
    for (const [akey, avalue] of a.entries()) {
      if (!b.has(akey) || !deepEqual(avalue, b.get(akey))) return false;
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
