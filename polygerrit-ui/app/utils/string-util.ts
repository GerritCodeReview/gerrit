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
 * Returns the index of the first character in `a` that is not the same as in
 * `b`. If both strings are identical or `a` is empty, returns -1.
 *
 * For example, `firstDifference('01234567', '0123zz67')` would return 4
 */
export function firstDifference(a: string, b: string): number {
  if (a === b) {
    return -1;
  }
  const aChars = a.split('');
  const bChars = b.split('');
  const differenceIndex = aChars.findIndex((aChar, i) => aChar !== bChars[i]);
  // Since the identical strings case is early returned, when no difference is
  // found from `findIndex` it must be because `b` has more characters than `a`.
  // In that case the difference is at `a.length`.
  return differenceIndex >= 0 ? differenceIndex : a.length;
}

/**
 * Returns the index of the last character in `a` that is not the same as in
 * `b`, counting from the end. If both strings are identical, returns -1.
 *
 * For example, `lastDifference('01234567', '0123zz67')` would return 5
 */
export function lastDifference(a: string, b: string) {
  if (a === b) {
    return -1;
  }
  const aCharsReverse = a.split('').reverse();
  const bCharsReverse = b.split('').reverse();
  const differenceIndex = aCharsReverse.findIndex(
    (aChar, i) => aChar !== bCharsReverse[i]
  );
  // The empty string case doesn't semantically make sense, but 0 is relatively
  // consistent with the likely use case of string splicing.
  return differenceIndex >= 0 ? a.length - 1 - differenceIndex : 0;
}

/**
 * removes text at the start of `a` that is also at the start of `b`, and
 * likewise for the ending.
 */
export function trimMatching(a: string, b: string) {
  const firstDiff = firstDifference(a, b);
  const lastDiff = lastDifference(a, b);
  return a.substring(firstDiff, lastDiff + 1);
}
