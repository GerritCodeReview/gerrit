/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {SpecialFilePath, FileInfoStatus} from '../constants/constants';
import {FileInfo, FileNameToFileInfoMap} from '../types/common';
import {hasOwnProperty} from './common-util';

export function specialFilePathCompare(a: string, b: string) {
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
  const headerExts = ['h', 'hh', 'hxx', 'hpp'];
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

export function shouldHideFile(file: string) {
  return file === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
}

// In case there are files with comments on them but they are unchanged, then
// we explicitly displays the file to render the comments with Unchanged status
export function addUnmodifiedFiles(
  files: FileNameToFileInfoMap,
  commentedPaths: {[fileName: string]: boolean}
) {
  if (!commentedPaths) return;
  Object.keys(commentedPaths).forEach(commentedPath => {
    if (hasOwnProperty(files, commentedPath) || shouldHideFile(commentedPath)) {
      return;
    }

    // if file is Renamed but has comments, then do not show the entry for the
    // old file path name
    if (
      Object.values(files).some(
        file =>
          file.status === FileInfoStatus.RENAMED &&
          file.old_path === commentedPath
      )
    ) {
      return;
    }
    // TODO(TS): either change FileInfo to mark delta and size optional
    // or fill in 0 here
    files[commentedPath] = {
      status: FileInfoStatus.UNMODIFIED,
    } as FileInfo;
  });
}

export function computeDisplayPath(path?: string) {
  if (path === SpecialFilePath.COMMIT_MESSAGE) {
    return 'Commit message';
  } else if (path === SpecialFilePath.MERGE_LIST) {
    return 'Merge list';
  }
  return path ?? '';
}

export function isMagicPath(path?: string) {
  return (
    !!path &&
    (path === SpecialFilePath.COMMIT_MESSAGE ||
      path === SpecialFilePath.MERGE_LIST)
  );
}

export function computeTruncatedPath(path?: string) {
  return truncatePath(computeDisplayPath(path));
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
 */
export function truncatePath(path: string, threshold = 1) {
  const pathPieces = path.split('/');

  if (pathPieces.length <= threshold) {
    return path;
  }

  const index = pathPieces.length - threshold;
  // Character is an ellipsis.
  return `\u2026/${pathPieces.slice(index).join('/')}`;
}
