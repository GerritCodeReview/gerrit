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

export function charsOnly(s: string): string {
  return s.replace(/[^a-zA-Z]+/g, '');
}

export function isCharacterLetter(ch: string): boolean {
  return ch.length === 1 && ch.toLowerCase() !== ch.toUpperCase();
}

export function isUpperCase(ch: string): boolean {
  return ch === ch.toUpperCase();
}

export function ordinal(n?: number): string {
  if (n === undefined) return '';
  if (n % 10 === 1 && n % 100 !== 11) return `${n}st`;
  if (n % 10 === 2 && n % 100 !== 12) return `${n}nd`;
  if (n % 10 === 3 && n % 100 !== 13) return `${n}rd`;
  return `${n}th`;
}

/** Escape operator value to avoid affecting overall query.
 *
 * Escapes quotes (") and backslashes (\). Wraps in quotes so the value can
 * contain spaces and colons.
 */
export function escapeAndWrapSearchOperatorValue(value: string): string {
  return `"${value.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"`;
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

export function trimWithEllipsis(
  s: string | undefined,
  maxLength: number
): string {
  if (!s) return '';
  if (s.length <= maxLength) return s;
  return s.substring(0, maxLength - 3) + '...';
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
 * Computes the Levenshtein edit distance between two strings.
 */
export function levenshteinDistance(str1: string, str2: string): number {
  const m = str1.length;
  const n = str2.length;

  // Create a matrix to store edit distances
  const dp: number[][] = Array.from({length: m + 1}, () =>
    Array(n + 1).fill(0)
  );

  // Initialize first row and column with base cases
  for (let i = 0; i <= m; i++) {
    dp[i][0] = i;
  }
  for (let j = 0; j <= n; j++) {
    dp[0][j] = j;
  }

  // Calculate edit distances for all substrings
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (str1[i - 1] === str2[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1];
      } else {
        dp[i][j] = Math.min(
          dp[i - 1][j] + 1, // Deletion
          dp[i][j - 1] + 1, // Insertion
          dp[i - 1][j - 1] + 1 // Substitution
        );
      }
    }
  }

  // Return the final edit distance
  return dp[m][n];
}
