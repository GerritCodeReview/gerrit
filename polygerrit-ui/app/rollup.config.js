/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

const path = require('path');

// In this file word "plugin" refers to rollup plugin, not Gerrit plugin.
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
//    location.
// This workaround also gives us additional power - we can place .config.js
// file anywhere in a source tree and add all plugins in the same package.json
// file as rollup node module.
function requirePlugin(id) {
  const rollupBinDir = path.dirname(process.argv[1]);
  const pluginPath = require.resolve(id, {paths: [__dirname, rollupBinDir]});
  return require(pluginPath);
}

const resolve = requirePlugin('rollup-plugin-node-resolve');
const {terser} = requirePlugin('rollup-plugin-terser');

// @polymer/font-roboto-local uses import.meta.url value
// as a base path to fonts. We should substitute a correct javascript
// code to get a base path for font-roboto-local fonts.
const importLocalFontMetaUrlResolver = function() {
  return {
    name: 'import-meta-url-resolver',
    resolveImportMeta(property, data) {
      if (property === 'url' && data.moduleId.endsWith('/@polymer/font-roboto-local/roboto.js')) {
        return 'new URL("..", document.baseURI).href';
      }
      return null;
    },
  };
};

export default {
  treeshake: false,
  onwarn: warning => {
    // No warnings from rollupjs are allowed.
    // Most of the warnings are real error in our code (for example,
    // if some import couldn't be resolved we can't continue, but rollup
    // reports it as a warning)
    throw new Error(warning.message);
  },
  output: {
    format: 'iife',
    compact: true,
    plugins: [
      terser({
        output: {
          comments: false,
        },
      }),
    ],
  },
  // Context must be set to window to correctly processing global variables
  context: 'window',
  plugins: [resolve({
    customResolveOptions: {
      // By default, it tries to use page.mjs file instead of page.js
      // when importing 'page/page'.
      extensions: ['.js'],
      moduleDirectory: 'external/ui_npm/node_modules',
    },
  }), importLocalFontMetaUrlResolver()],
};
