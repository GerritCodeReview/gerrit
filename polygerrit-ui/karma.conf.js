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

const runFilesDir = process.env["RUNFILES_DIR"];
const path = require('path');

function getModulesDir() {
  if(runFilesDir) {
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

function getSinonPath() {
  if(runFilesDir) {
    return "external/ui_dev_npm/node_modules/sinon/pkg/sinon.js";
  }
  else {
    return "polygerrit-ui/node_modules/sinon/pkg/sinon.js"
  }
}

module.exports = function(config) {
    // Use --test-files to specify pattern for a test files.
    // It can be just a file name, without a path:
    // --test-files async-foreach-behavior_ktest.js
    // If you specify --test-files without pattern, it gets true value
    // In this case we ill run all tests (usefull for package.json "debugtest" script)
    const testFilesPattern = (typeof config.testFiles == 'string') ? 'polygerrit-ui/app/**/' + config.testFiles
      : 'polygerrit-ui/app/**/*_ktest.js';
    config.set({
    // base path that will be used to resolve all patterns (eg. files, exclude)
    basePath: '../',

    plugins: [
      // load plugin
      require.resolve('@open-wc/karma-esm'),

      // fallback: resolve any karma- plugins
      'karma-*',
    ],

    // frameworks to use
    // available frameworks: https://npmjs.org/browse/keyword/karma-adapter
    frameworks: ['mocha', 'esm'],


    // list of files / patterns to load in the browser
    files: [
      getSinonPath(),
      { pattern: testFilesPattern, type: 'module' },
    ],
    esm: {
      nodeResolve: true,
      moduleDirs: getModulesDir(),
      preserveSymlinks: true,
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
        ui: 'tdd'
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
