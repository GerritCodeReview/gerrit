/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * This is a very simple resolver for the 'js imports ts' case. It is used only
 * by eslint and must be removed after switching to typescript is finished.
 * The resolver searches for .ts files instead of .js
 */

const path = require('path');
const fs = require('fs');

function isRelativeImport(source) {
  return source.startsWith('./') || source.startsWith('../');
}

module.exports = {
  interfaceVersion: 2,
  resolve: function(source, file, config) {
    if (!isRelativeImport(source) || !source.endsWith('.js')) {
      return {found: false};
    }
    const tsSource = source.slice(0, -3) + '.ts';

    const fullPath = path.resolve(path.dirname(file), tsSource);
    if (!fs.existsSync(fullPath)) {
      return {found: false};
    }
    return {found: true, path: fullPath};
  }
};
