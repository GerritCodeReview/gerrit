/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 * @fileoverview Map of profile pictures for specific known robots that post
 * comments in Gerrit used to override the empty user avatar image when
 * possible.
 */
const ROBOT_AVATAR_OVERRIDES: ReadonlyMap<string, string> = new Map([
  [
    'treehugger-gerrit@google.com',
    // TODO: Replace with Treehugger avatar when available
    'https://gstatic.com/buganizer/img/v2/gerrit_logo.svg',
  ],
]);

/**
 * Returns a custom avatar URL for a known robot account, or undefined if not
 * found.
 */
export function getRobotAvatarUrl(email?: string): string | undefined {
  if (!email) {
    return undefined;
  }
  return ROBOT_AVATAR_OVERRIDES.get(email);
}
