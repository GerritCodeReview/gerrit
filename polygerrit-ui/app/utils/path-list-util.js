/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import {SpecialFilePath} from '../constants/constants.js';

/**
 * @param {string} a
 * @param {string} b
 * @return {number}
 */
export function specialFilePathCompare(a, b) {
  // The commit message always goes first.
  if (a === SpecialFilePath.COMMIT_MESSAGE) {
    return -1;
  }
  if (b === SpecialFilePath.COMMIT_MESSAGE) {
    return 1;
  }

  // The merge list always comes next.
  if (a === SpecialFilePath.MERGE_LIST) {
    return -1;
  }
  if (b === SpecialFilePath.MERGE_LIST) {
    return 1;
  }

  const aLastDotIndex = a.lastIndexOf('.');
  const aExt = a.substr(aLastDotIndex + 1);
  const aFile = a.substr(0, aLastDotIndex) || a;

  const bLastDotIndex = b.lastIndexOf('.');
  const bExt = b.substr(bLastDotIndex + 1);
  const bFile = b.substr(0, bLastDotIndex) || b;

  // Sort header files above others with the same base name.
  const headerExts = ['h', 'hxx', 'hpp'];
  if (aFile.length > 0 && aFile === bFile) {
    if (headerExts.includes(aExt) && headerExts.includes(bExt)) {
      return a.localeCompare(b);
    }
    if (headerExts.includes(aExt)) {
      return -1;
    }
    if (headerExts.includes(bExt)) {
      return 1;
    }
  }
  return aFile.localeCompare(bFile) || a.localeCompare(b);
}

export function shouldHideFile(file) {
  return file === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
}

export function addUnmodifiedFiles(files, commentedPaths) {
  if (!commentedPaths) return;
  Object.keys(commentedPaths).forEach(commentedPath => {
    if (files.hasOwnProperty(commentedPath) ||
      shouldHideFile(commentedPath)) { return; }
    files[commentedPath] = {status: 'U'};
  });
}

export function computeDisplayPath(path) {
  if (path === SpecialFilePath.COMMIT_MESSAGE) {
    return 'Commit message';
  } else if (path === SpecialFilePath.MERGE_LIST) {
    return 'Merge list';
  }
  return path;
}

export function isMagicPath(path) {
  return !!path &&
      (path === SpecialFilePath.COMMIT_MESSAGE || path ===
          SpecialFilePath.MERGE_LIST);
}

export function computeTruncatedPath(path) {
  return truncatePath(
      computeDisplayPath(path));
}

/**
 * Truncates URLs to display filename only
 * Example
 * // returns '.../text.html'
 * util.truncatePath.('dir/text.html');
 * Example
 * // returns 'text.html'
 * util.truncatePath.('text.html');
 *
 * @param {string} path
 * @param {number=} opt_threshold
 * @return {string} Returns the truncated value of a URL.
 */
export function truncatePath(path, opt_threshold) {
  const threshold = opt_threshold || 1;
  const pathPieces = path.split('/');

  if (pathPieces.length <= threshold) { return path; }

  const index = pathPieces.length - threshold;
  // Character is an ellipsis.
  return `\u2026/${pathPieces.slice(index).join('/')}`;
}
