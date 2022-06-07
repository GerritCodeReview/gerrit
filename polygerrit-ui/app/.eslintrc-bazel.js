/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// This file has a special settings for bazel.
// The settings is required because bazel uses different location
// for node_modules.

function getBazelSettings() {
  const runFilesDir = process.env['RUNFILES_DIR'];
  if (!runFilesDir) {
    // eslint is executed with 'bazel run ...' to fix the source code. It runs
    // against real source code, no special paths for node_modules is set.
    return {};
  }
  // eslint is executed with 'bazel test...'. Set path to required node_modules
  return {
    'import/resolver': {
      node: {
        paths: [
          `${runFilesDir}/ui_npm/node_modules`,
          `${runFilesDir}/ui_dev_npm/node_modules`,
        ],
      },
    },
  };
}

module.exports = {
  extends: './.eslintrc.js',
  settings: getBazelSettings(),
};
