/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AppTheme} from '../constants/constants';

// https://css-tricks.com/a-complete-guide-to-dark-mode-on-the-web/#aa-javascript
function isDarkThemeInOs() {
  const prefersDarkScheme = prefersDarkColorScheme();
  return prefersDarkScheme.matches;
}

export function prefersDarkColorScheme() {
  return window.matchMedia('(prefers-color-scheme: dark)');
}

export function isDarkTheme(theme: AppTheme) {
  if (theme === AppTheme.AUTO) return isDarkThemeInOs();
  return theme === AppTheme.DARK;
}
