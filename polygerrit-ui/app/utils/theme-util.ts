/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AppTheme} from '../constants/constants';

export function getThemePreference(): AppTheme {
  if (window.localStorage.getItem('dark-theme')) {
    return AppTheme.DARK;
  }
  if (window.localStorage.getItem('light-theme')) {
    return AppTheme.LIGHT;
  }
  return AppTheme.AUTO;
}

// https://css-tricks.com/a-complete-guide-to-dark-mode-on-the-web/#aa-javascript
function isDarkThemeInOs() {
  const prefersDarkScheme = window.matchMedia('(prefers-color-scheme: dark)');
  return prefersDarkScheme.matches;
}

export function isDarkTheme() {
  const preference = getThemePreference();
  if (preference === AppTheme.AUTO) return isDarkThemeInOs();

  return preference === AppTheme.DARK;
}
