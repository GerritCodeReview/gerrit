/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// This file has a special settings for bazel.
// The settings is required because bazel uses different location
// for node_modules.

const {defineConfig, globalIgnores} = require('eslint/config');
const js = require('@eslint/js');
const {FlatCompat} = require('@eslint/eslintrc');

const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
  allConfig: js.configs.all,
});

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

module.exports = defineConfig([
  globalIgnores([
    '**/node_modules',
    '**/rollup.config.js',
    '**/node_modules_licenses/',
    '**/.prettierrc.js',
    '**/.eslint-ts-resolver.config.js',
  ]),
  ...compat.config({
    extends: './eslint.config.js',
    settings: getBazelSettings(),
  }),
]);
