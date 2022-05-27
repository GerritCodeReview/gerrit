/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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

export function capitalizeFirstLetter(str: string) {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

/**
 * Converts the items into a sentence-friendly format. Examples:
 * listForSentence(["Foo", "Bar", "Baz"])
 * => 'Foo, Bar, and Baz'
 * listForSentence(["Foo", "Bar"])
 * => 'Foo and Bar'
 * listForSentence(["Foo"])
 * => 'Foo'
 */
export function listForSentence(items: string[]): string {
  if (items.length < 2) return items.join('');
  if (items.length === 2) return items.join(' and ');

  const firstItems = items.slice(0, items.length - 1);
  const lastItem = items[items.length - 1];
  return `${firstItems.join(', ')}, and ${lastItem}`;
}
