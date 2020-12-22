/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

// Eslint 7 introduced a breaking change - it uses the current workdir instead
// of the configuration file directory for resolving relative paths:
// https://eslint.org/docs/user-guide/migrating-to-7.0.0#base-path-change
// This file is loaded before the eslint and sets the current directory
// back to the location of configuration file.

const path = require('path');
const configParamIndex =
    process.argv.findIndex(arg => arg === '-c' || arg === '---config');
if (configParamIndex >= 0 && configParamIndex + 1 < process.argv.length) {
  const dirName = path.dirname(process.argv[configParamIndex + 1]);
  process.chdir(dirName);
}
