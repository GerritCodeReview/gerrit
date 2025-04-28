/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LibraryConfig} from './gr-lib-loader';

export const EMOJIS_LIBRARY_CONFIG: LibraryConfig = {
  src: 'bower_components/emojis/emojis.min.js',
  checkPresent: () => window.emojis !== undefined,
  configureCallback: () => window.emojis,
};
