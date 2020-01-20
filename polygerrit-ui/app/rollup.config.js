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

const path = require('path');

// By default, require(plugin_name) tries to find module plugin_name starting
// from the folder where this file (rollup.config.js) is located
// (see https://www.typescriptlang.org/docs/handbook/module-resolution.html#node
// and https://nodejs.org/api/modules.html#modules_all_together).
// So, rollup.config.js can't be in polygerrit-ui/app dir and it should be in
// tools/node_tools directory (where all plugins are installed).
// But rollup_bundle rule copy this .config.js file to another directory,
// so require(plugin_name) can't find a plugin.
// To fix it, requirePlugin tries:
// 1. resolve module id using default behavior, i.e. it starts from __dirname
// 2. if module not found - it tries to resolve module starting from rollupBin
//      location.
// This workaround also gives us additional power - we can place .config.js
// file anywhere in a source tree and add all plugins to the same package.json
// file as rollup node module.
function requirePlugin(id) {
  const rollupBinDir = path.dirname(process.argv[1]);
  const pluginPath = require.resolve(id, {paths: [__dirname, rollupBinDir] });
  return require(pluginPath);
}

const resolve = requirePlugin('rollup-plugin-node-resolve');
const {terser} = requirePlugin('rollup-plugin-terser');

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
  plugins: [resolve({
    customResolveOptions: {
      moduleDirectory: 'node_modules'
    }
  })],
};
