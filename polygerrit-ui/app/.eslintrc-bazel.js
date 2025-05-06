/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// This file has a special settings for bazel.
// The settings is required because bazel uses different location
// for node_modules.

import baseConfig from './eslint.config.js';

const runFilesDir = process.env['RUNFILES_DIR'];

const bazelImportResolver = runFilesDir
  ? {
      settings: {
        'import/resolver': {
          node: {
            paths: [
              `${runFilesDir}/ui_npm/node_modules`,
              `${runFilesDir}/ui_dev_npm/node_modules`,
            ],
          },
        },
      },
    }
  : {};

export default [
  ...baseConfig,
  bazelImportResolver,
];
