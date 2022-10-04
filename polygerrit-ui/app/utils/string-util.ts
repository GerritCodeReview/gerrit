/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {computeDisplayPath} from './path-list-util';

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

/**
 *  Separates a path into:
 *  - The part that matches another path,
 *  - The part that does not match the other path,
 *  - The file name
 *
 *  For example:
 *    diffFilePaths('same/part/new/part/foo.js', 'same/part/different/foo.js');
 *  yields: {
 *      matchingFolders: 'same/part/',
 *      newFolders: 'new/part/',
 *      fileName: 'foo.js',
 *    }
 */
export function diffFilePaths(filePath: string, otherFilePath?: string) {
  // Separate each string into an array of folder names + file name.
  const displayPath = computeDisplayPath(filePath);
  const previousFileDisplayPath = computeDisplayPath(otherFilePath);
  const displayPathParts = displayPath.split('/');
  const previousFileDisplayPathParts = previousFileDisplayPath.split('/');

  // Construct separate strings for matching folders, new folders, and file
  // name.
  const firstDifferencePartIndex = displayPathParts.findIndex(
    (part, index) => previousFileDisplayPathParts[index] !== part
  );
  const matchingSection = displayPathParts
    .slice(0, firstDifferencePartIndex)
    .join('/');
  const newFolderSection = displayPathParts
    .slice(firstDifferencePartIndex, -1)
    .join('/');
  const fileNameSection = displayPathParts[displayPathParts.length - 1];

  // Note: folder sections need '/' appended back.
  return {
    matchingFolders: matchingSection.length > 0 ? `${matchingSection}/` : '',
    newFolders: newFolderSection.length > 0 ? `${newFolderSection}/` : '',
    fileName: fileNameSection,
  };
}

/**
 * Returns the characters that are identical at the start of both strings.
 *
 * For example, `countMatchingStart('12345678', '1234zz78')` would return '1234'
 */
export function getSharedPrefix(a: string, b: string): string {
  for (let i = 0; i < a.length; ++i) {
    if (a[i] !== b[i]) {
      return a.substring(0, i);
    }
  }
  return a;
}

/**
 * Returns the characters that are identical at the end of both strings.
 *
 * For example, `countMatchingEnd('12345678', '1234zz78')` would return '78'
 */
export function getSharedSuffix(a: string, b: string): string {
  for (let iFromEnd = 0; iFromEnd < a.length; ++iFromEnd) {
    if (a[a.length - 1 - iFromEnd] !== b[b.length - 1 - iFromEnd]) {
      return a.substring(a.length - iFromEnd, a.length);
    }
  }
  return a;
}

/**
 * removes text at the start of `a` that is also at the start of `b`, and
 * likewise for the ending.
 *
 * For example, `trimMatching('aaBBcc', 'aabbcc') would return 'BB'
 */
export function trimMatching(a: string, b: string): string {
  const sameStart = getSharedPrefix(a, b).length;
  const sameEnd = getSharedSuffix(a, b).length;
  return a.substring(sameStart, a.length - sameEnd);
}
