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

// This file has a special settings for bazel.
// The settings is required because bazel uses different location
// for node_modules.

function getBazelSettings() {
  const runFilesDir = process.env["RUNFILES_DIR"];
  if (!runFilesDir) {
    // eslint is executed with 'bazel run ...' to fix the source code. It runs
    // against real source code, no special paths for node_modules is set.
    return {};
  }
  // eslint is executed with 'bazel test...'. Set path to required node_modules
  return {
    "import/resolver": {
      "node": {
        "paths": [
          `${runFilesDir}/ui_npm/node_modules`,
          `${runFilesDir}/ui_dev_npm/node_modules`
        ]
      }
    }
  };
}

module.exports = {
  "extends": "./.eslintrc.js",
  "settings": getBazelSettings(),
};
