// Copyright (C) 2020 The Android Open Source Project
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
  plugins: [resolve({
    customResolveOptions: {
      moduleDirectory: 'node_modules'
    }
  })],
};
