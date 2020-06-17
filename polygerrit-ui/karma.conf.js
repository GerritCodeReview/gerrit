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

const runUnderBazel = !!process.env["RUNFILES_DIR"];
const path = require('path');

function getModulesDir() {
  if(runUnderBazel) {
    // Run under bazel
    return [
      `external/ui_npm/node_modules`,
      `external/ui_dev_npm/node_modules`
    ];
  }

  // Run from intellij or npm run test:kdebug
  return [
    path.join(__dirname, 'app/node_modules'),
    path.join(__dirname, 'node_modules'),
  ];
}

function getUiDevNpmFilePath(importPath) {
  if(runUnderBazel) {
    return `external/ui_dev_npm/node_modules/${importPath}`;
  }
  else {
    return `polygerrit-ui/node_modules/${importPath}`
  }
}

module.exports = function(config) {
  const rootDir = runUnderBazel ?
      'polygerrit-ui/app/ts_test_out/' : 'polygerrit-ui/app/';
  const testFilesLocationPattern =
      `${rootDir}**/!(template_test_srcs)/`;
  // Use --test-files to specify pattern for a test files.
  // It can be just a file name, without a path:
  // --test-files async-foreach-behavior_test.js
  // If you specify --test-files without pattern, it gets true value
  // In this case we ill run all tests (usefull for package.json "debugtest"
  // script)
  const testFilesPattern = (typeof config.testFiles == 'string') ?
      testFilesLocationPattern + config.testFiles :
      testFilesLocationPattern + '*_test.js';
  config.set({
    // base path that will be used to resolve all patterns (eg. files, exclude)
    basePath: '../',
    plugins: [
      // Do not use karma-* to load all installed plugin
      // This can lead to unexpected behavior under bazel
      // if you forget to add a plugin in a bazel rule.
      require.resolve('@open-wc/karma-esm'),
      'karma-mocha',
      'karma-chrome-launcher',
      'karma-mocha-reporter',
    ],
    // frameworks to use
    // available frameworks: https://npmjs.org/browse/keyword/karma-adapter
    frameworks: ['mocha', 'esm'],

    // list of files / patterns to load in the browser
    files: [
      getUiDevNpmFilePath('accessibility-developer-tools/dist/js/axs_testing.js'),
      getUiDevNpmFilePath('sinon/pkg/sinon.js'),
      { pattern: testFilesPattern, type: 'module' },
    ],
    esm: {
      nodeResolve: true,
      moduleDirs: getModulesDir(),
      // Bazel and yarn uses symlinks for files.
      // preserveSymlinks is necessary for correct modules paths resolving
      preserveSymlinks: true,
      // By default, esm-dev-server uses 'auto' compatibility mode.
      // In the 'auto' mode it incorrectly applies polyfills and
      // breaks tests in some browser versions
      // (for example, Chrome 69 on gerrit-ci).
      compatibility: 'none',
    },
    // test results reporter to use
    // possible values: 'dots', 'progress'
    // available reporters: https://npmjs.org/browse/keyword/karma-reporter
    reporters: ['mocha'],


    // web server port
    port: 9876,


    // enable / disable colors in the output (reporters and logs)
    colors: true,


    // level of logging
    // possible values: config.LOG_DISABLE || config.LOG_ERROR || config.LOG_WARN || config.LOG_INFO || config.LOG_DEBUG
    logLevel: config.LOG_INFO,


    // enable / disable watching file and executing tests whenever any file changes
    autoWatch: false,


    // start these browsers
    // available browser launchers: https://npmjs.org/browse/keyword/karma-launcher
    browsers: ["CustomChromeHeadless"],
    browserForDebugging: "CustomChromeHeadlessWithDebugPort",


    // Continuous Integration mode
    // if true, Karma captures browsers, runs the tests and exits
    singleRun: true,

    // Concurrency level
    // how many browser should be started simultaneous
    concurrency: Infinity,

    client: {
      mocha: {
        ui: 'tdd',
        timeout: 5000,
      }
    },

    customLaunchers: {
      // Based on https://developers.google.com/web/updates/2017/06/headless-karma-mocha-chai
      "CustomChromeHeadless": {
        base: 'ChromeHeadless',
        flags: ['--disable-translate', '--disable-extensions'],
      },
      "ChromeDev": {
        base: 'Chrome',
        flags: ['--disable-extensions', ' --auto-open-devtools-for-tabs'],
      },
      "CustomChromeHeadlessWithDebugPort": {
        base: 'CustomChromeHeadless',
        flags: ['--remote-debugging-port=9222'],
      }
    }
  });
};
