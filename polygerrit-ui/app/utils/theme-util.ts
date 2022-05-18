/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {Theme} from '../constants/constants';

export function getThemePreference(): Theme {
  if (window.localStorage.getItem('dark-theme')) {
    return Theme.DARK;
  }
  if (window.localStorage.getItem('light-theme')) {
    return Theme.LIGHT;
  }
  return Theme.AUTO;
}

// https://css-tricks.com/a-complete-guide-to-dark-mode-on-the-web/#aa-javascript
function isDarkThemeInOs() {
  const prefersDarkScheme = window.matchMedia('(prefers-color-scheme: dark)');
  return prefersDarkScheme.matches;
}

export function isDarkTheme() {
  const preference = getThemePreference();
  if (preference === Theme.AUTO) return isDarkThemeInOs();

  return preference === Theme.DARK;
}
