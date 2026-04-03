/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Bazel-specific ESLint wrapper.
//
// This file is intentionally thin:
// - `eslint.config.js` remains the source of truth for rules
// - this file only adapts module resolution for Bazel runfiles
//
// In Bazel test mode, npm dependencies are located under runfiles
// directories such as ui_dev_npm/node_modules. Extend the Node resolver
// so that ESLint can locate those packages.

const {defineConfig, globalIgnores} = require('eslint/config');
const js = require('@eslint/js');
const {FlatCompat} = require('@eslint/eslintrc');
const path = require('path');
const fs = require('fs');

const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
  allConfig: js.configs.all,
});

function pathExists(p) {
  try {
    return fs.existsSync(p);
  // eslint-disable-next-line no-unused-vars
  } catch (unusedError) {
    return false;
  }
}

function getRunfilesRoot() {
  return process.env.RUNFILES_DIR || process.env.TEST_SRCDIR || '';
}

function getNodeModulesPaths() {
  const runfilesRoot = getRunfilesRoot();

  if (runfilesRoot) {
    // Bazel test mode: collect node_modules from runfiles
    return [
      path.join(runfilesRoot, 'ui_dev_npm/node_modules'),
      path.join(runfilesRoot, 'ui_npm/node_modules'),
      path.join(runfilesRoot, '_main/external/ui_dev_npm/node_modules'),
      path.join(runfilesRoot, '_main/external/ui_npm/node_modules'),
      path.join(runfilesRoot, '_main/polygerrit-ui/app/node_modules'),
    ].filter(pathExists);
  }

  // Workspace mode
  return [
    path.join(__dirname, 'node_modules'),
    path.join(__dirname, '../../node_modules'),
    path.join(process.cwd(), 'node_modules'),
    path.join(process.cwd(), '../../node_modules'),
  ].filter(pathExists);
}

function getBazelSettings() {
  const paths = getNodeModulesPaths();
  if (paths.length === 0) return {};

  return {
    'import/resolver': {
      node: {
        paths,
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
