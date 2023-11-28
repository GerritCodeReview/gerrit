/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/** See also Patch.java for the backend equivalent. */
export enum FileMode {
  /** Mode indicating an entry is a symbolic link. */
  SYMLINK = 0o120000,

  /** Mode indicating an entry is a non-executable file. */
  REGULAR_FILE = 0o100644,

  /** Mode indicating an entry is an executable file. */
  EXECUTABLE_FILE = 0o100755,

  /** Mode indicating an entry is a submodule commit in another repository. */
  GITLINK = 0o160000,
}

export function fileModeToString(mode?: number, includeNumber = true): string {
  const str = fileModeStr(mode);
  const num = mode?.toString(8);
  return `${str}${includeNumber && str ? ` (${num})` : ''}`;
}

function fileModeStr(mode?: number): string {
  if (mode === FileMode.SYMLINK) return 'symlink';
  if (mode === FileMode.REGULAR_FILE) return 'regular';
  if (mode === FileMode.EXECUTABLE_FILE) return 'executable';
  if (mode === FileMode.GITLINK) return 'gitlink';
  return '';
}

export function expandFileMode(input?: string) {
  if (!input) return input;
  for (const modeNum of Object.values(FileMode) as FileMode[]) {
    const modeStr = modeNum?.toString(8);
    if (input.includes(modeStr)) {
      return input.replace(modeStr, `${fileModeToString(modeNum)}`);
    }
  }
  return input;
}
