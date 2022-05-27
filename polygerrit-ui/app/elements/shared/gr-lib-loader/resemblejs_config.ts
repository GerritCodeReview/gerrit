/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LibraryConfig} from './gr-lib-loader';

export const RESEMBLEJS_LIBRARY_CONFIG: LibraryConfig = {
  src: 'bower_components/resemblejs/resemble.js',
  checkPresent: () => window.resemble !== undefined,
  configureCallback: () => {
    window.resemble.outputSettings({
      errorColor: {red: 255, green: 0, blue: 255},
      errorType: 'flat',
      transparency: 0,
      // Disable large image threshold; by default this otherwise skips pixels
      // if width or height exceed 1200 pixels.
      largeImageThreshold: 0,
    });
    return window.resemble;
  },
};
