/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// This file has a special settings for bazel.
// The settings is required because bazel uses different location
// for node_modules.

const {defineConfig} = require("eslint/config");
const js = require("@eslint/js");
const {FlatCompat} = require("@eslint/eslintrc");

const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
    allConfig: js.configs.all
});

module.exports = defineConfig([
  {
    ignores: [
      '**/node_modules',
      '**/rollup.config.js',
      'node_modules_licenses'
    ],
  },
  {
      extends: "./.eslintrc.js",
      settings: getBazelSettings(),
  }]);

