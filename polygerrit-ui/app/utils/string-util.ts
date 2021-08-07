/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
 * Returns a count plus string that is pluralized when necessary.
 */
export function pluralize(count: number, noun: string): string {
  if (count === 0) return '';
  return `${count} ${noun}` + (count > 1 ? 's' : '');
}

export function addQuotesWhen(string: string, cond: boolean): string {
  return cond ? `"${string}"` : string;
}

export function charsOnly(s: string): string {
  return s.replace(/[^a-zA-Z]+/g, '');
}

export function ordinal(n?: number): string {
  if (n === undefined) return '';
  if (n % 10 === 1 && n % 100 !== 11) return `${n}st`;
  if (n % 10 === 2 && n % 100 !== 12) return `${n}nd`;
  if (n % 10 === 3 && n % 100 !== 13) return `${n}rd`;
  return `${n}th`;
}

/**
 * This converts any inputed value into string.
 *
 * This is so typescript checker doesn't fail.
 */
export function convertToString(key?: unknown) {
  return key !== undefined ? String(key) : '';
}
