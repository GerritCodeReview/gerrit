// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import resolve from 'rollup-plugin-node-resolve';
import {terser} from 'rollup-plugin-terser';
import fs from 'fs';

//Checks that code do not use any file with !!!GERRIT-DO-NOT-DISTRIBUTE text
//Such text can be created:
//1) By gerrit contributors to ensure that particular file is not included
//      in the final build by accident.
//
//2) By licenses.js tool (used by bazel build) - all files in node_module with
//      inappropriate license are updated - their content is replaced by the text
//      !!!GERRIT-DO-NOT-DISTRIBUTE - LICENSE TYPE IS NOT VALID!!!
function licenseCheck() {
  return {
    name: 'license-check',
    load(id) {
       const content = fs.readFileSync(id, {encoding: 'utf8'});
      if (content.indexOf("!!!GERRIT-DO-NOT-DISTRIBUTE") >= 0) {
        if(content.indexOf("!!!GERRIT-DO-NOT-DISTRIBUTE - LICENSE TYPE IS NOT VALID!!!") >= 0) {
          this.error(`The file has invalid or unknown license. Check that node_modules_licenses folder has a correct license for this file. If the license is correct then you can't import/use this file for production code.`);
        } else {
          this.error(`The file must not be included in the final build (directly or as transitive dependency).`);
        }
      }
       //Do not return any content - let's other plugins or rollup to load this file
       return null;
    }
  }
}

export default {
  treeshake: false,
  onwarn: warning => {
    if(warning.code === 'CIRCULAR_DEPENDENCY') {
      // Temporary allow CIRCULAR_DEPENDENCY.
      // See https://bugs.chromium.org/p/gerrit/issues/detail?id=12090
      // Delete this code after bug is fixed.
      return;
    }
    // No warnings from rollupjs are allowed.
    // Most of the warnings are real error in our code (for example,
    // if some import couldn't be resolved we can't continue, but rollup
    // reports it as a warning)
    throw new Error(warning.message);
  },
  output: {
    format: 'iife',
    compact: true,
    plugins: [terser()]
  },
  //Context must be set to window to correctly processing global variables
  context: "window",
  //licenseCheck must be the first item in the array.
  plugins: [licenseCheck(), resolve({
    customResolveOptions: {
      moduleDirectory: 'node_modules'
    }
  })],
};
