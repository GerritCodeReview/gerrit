/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This is a very simple resolver for the 'js import ts' case. It is used only
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
