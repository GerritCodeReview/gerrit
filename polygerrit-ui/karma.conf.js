/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

const runUnderBazel = !!process.env["RUNFILES_DIR"];
const path = require('path');

function getModulesDir() {
  if(runUnderBazel) {
    // Run under bazel
    return [
      `external/plugins_npm/node_modules`,
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

function runInIde() {
  // A simple detection of IDE.
  // Default browserNoActivityTimeout is 30 seconds. An IDE usually
  // runs karma in background and send commands when a user wants to
  // execute test. If interval between user executed tests is bigger than
  // browserNoActivityTimeout, the IDE reports error and doesn't restart
  // server.
  // We want to increase browserNoActivityTimeout when tests run in IDE.
  // Wd don't want to increase it in other cases, oterhise hanging tests
  // can slow down CI.
  return !runUnderBazel &&
      process.argv.some(arg => arg.toLowerCase().contains('intellij'));
}

module.exports = function(config) {
  let root = config.root;
  if (!root) {
    console.warn(`--root argument not set. Falling back to __dirname.`)
    root = path.resolve(__dirname) + '/';
  }
  // Use --test-files to specify pattern for a test files.
  // It can be just a file name, without a path:
  // --test-files async-foreach-behavior_test.js
  // If you specify --test-files without pattern, it gets true value
  // In this case we will run all tests (usefull for package.json "debugtest"
  // script)
  // We will convert a .ts argument to .js and fill in .js if no extension is
  // given.
  let filePattern;
  if (typeof config.testFiles === "string") {
    if (config.testFiles.endsWith('.ts')) {
      filePattern = config.testFiles.substr(0, config.testFiles.lastIndexOf(".")) + ".js";
    } else if (config.testFiles.endsWith('.js')) {
      filePattern = config.testFiles;
    } else {
      filePattern = config.testFiles + '.js';
    }
  } else {
    filePattern = '*_test.js';
  }
  const testFilesPattern = root + '**/' + filePattern;

  console.info(`Karma test file pattern: ${testFilesPattern}`)
  // Special patch for grep parameters (see details in the grep-patch-karam.js)
  const additionalFiles = runUnderBazel ? [] : ['polygerrit-ui/grep-patch-karma.js'];
  config.set({
    browserNoActivityTimeout: runInIde ? 60 * 60 * 1000 : 30 * 1000,
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
      ...additionalFiles,
      getUiDevNpmFilePath('source-map-support/browser-source-map-support.js'),
      getUiDevNpmFilePath('accessibility-developer-tools/dist/js/axs_testing.js'),
      {pattern: getUiDevNpmFilePath('@open-wc/semantic-dom-diff/index.js'), type: 'module' },
      {pattern: getUiDevNpmFilePath('@open-wc/testing-helpers/index.js'), type: 'module' },
      getUiDevNpmFilePath('sinon/pkg/sinon.js'),
      { pattern: testFilesPattern, type: 'module' },
    ],
    esm: {
      nodeResolve: {
        // By default, it tries to use page.mjs file instead of page.js
        // when importing 'page/page', so we shouldn't use .mjs extension
        // in node resolve.
        // The .ts extension is required to display source code in browser
        // (otherwise esm plugin crashes)
        extensions: ['.js', '.ts'],
      },
      moduleDirs: getModulesDir(),
      // Bazel and yarn uses symlinks for files.
      // preserveSymlinks is necessary for correct modules paths resolving
      preserveSymlinks: true,
      // By default, esm-dev-server uses 'auto' compatibility mode.
      // In the 'auto' mode it incorrectly applies polyfills and
      // breaks tests in some browser versions
      // (for example, Chrome 69 on gerrit-ci).
      compatibility: 'none',
      plugins: [
        {
          resolveImport(importSpecifier) {
            // esm-dev-server interprets .ts files as .js files and
            // tries to replace all module imports with relative/absolute
            // paths. In most cases this works correctly. However if
            // a ts file imports type from .d.ts and there is no
            // associated .js file then the esm-dev-server responds with
            // 500 error.
            // For example the following import .ts file causes problem
            // import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
            // To avoid problems, we don't resolve imports in .ts files
            // and instead always return original path
            if (importSpecifier.context.originalUrl.endsWith(".ts")) {
              return importSpecifier.source;
            }
            return undefined;
          }
        },
        {
          transform(context) {
            if (context.path.endsWith('/node_modules/page/page.js')) {
              const orignalBody = context.body;
              // Can't import page.js directly, because this is undefined.
              // Replace it with window
              // The same replace exists in server.go
              // Rollup makes this replacement automatically
              const transformedBody = orignalBody.replace(
                  '}(this, (function () { \'use strict\';',
                  '}(window, (function () { \'use strict\';'
              );
              if(orignalBody.length === transformedBody.length) {
                console.error('The page.js was updated. Please update transform accordingly');
                process.exit(1);
              }
              return {body: transformedBody};
            }
          },
        }
      ]
    },
    // test results reporter to use
    // possible values: 'dots', 'progress'
    // available reporters: https://npmjs.org/browse/keyword/karma-reporter
    reporters: ['mocha'],

    mochaReporter: {
      showDiff: true
    },

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
