/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

declare global {
  interface Window {
    CANONICAL_PATH?: string;
  }
}

/** Returns the base URL canonical path, or empty string if unset. */
export function getBaseUrl(): string {
  // window is not defined in service worker, therefore no CANONICAL_PATH
  if (typeof window === 'undefined') return '';
  return self.CANONICAL_PATH || '';
}
