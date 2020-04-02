// Karma configuration
// Generated on Tue Mar 31 2020 19:01:50 GMT+0200 (Central European Summer Time)

//console.error(require("fs").readdirSync("polygerrit-ui/app/elements/admin/gr-access-section"));
//process.exit(1);
const runFilesDir = process.env["RUNFILES_DIR"];
// console.log(runFilesDir);
// console.log(require("fs").readdirSync(runFilesDir + '/ui_dev_npm/node_modules/chai'));
// process.exit(1);

function getModulesDir() {
  if(runFilesDir) {
    return [
      `external/ui_npm/node_modules`,
      `external/ui_dev_npm/node_modules`
      // 'app/node_modules',
      // 'node_modules'
    ];
  }
  return [
    'polygerrit-ui/app/node_modules',
    'polygerrit-ui/node_modules',
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
  // Object.defineProperty(config, "frameworks", {
  //   // karma_web_test rule overrides framework,
  //   // Workaround to protect it from override
  //   get() {
  //     return ['mocha', 'esm']
  //   },
  //   set(value) {
  //   }
  // });
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
    //frameworks: ['browserify', 'mocha', 'esm'],
    frameworks: ['mocha', 'esm'],


    // list of files / patterns to load in the browser
    files: [
      getSinonPath(),
      { pattern: 'polygerrit-ui/app/**/*_ktest.js', type: 'module' },
      // {
      //   pattern: '**/*.js',
      //   included: false,
      //   served: false,
      //   nocache: true,
      //   watched: false,
      // },
      // //{ pattern: 'behaviors/**/karma_test.js', type: module },
      // {
      //   pattern: '**/*.*',
      //   included: false,
      //   served: true,
      //   nocache: true,
      //   watched: false,
      // }
    ],
    // proxies: {
    //   '/base/app': 'http://localhost:8081',
    //   '/base': 'http://localhost:8081',
    //   '/': 'http://localhost:8081',
    //
    // },
    esm: {
      nodeResolve: true,
      moduleDirs: getModulesDir(),
      preserveSymlinks: true,
    },
    //
    //
    // // list of files / patterns to exclude
    // exclude: [
    //     "node_modules/**"
    // ],
    //
    //
    // preprocess matching files before serving them to the browser
    // available preprocessors: https://npmjs.org/browse/keyword/karma-preprocessor
    preprocessors: {
      //'app/**/*.js': [ 'browserify' ],
    },

    // browserify: {
    //   "transform": [["babelify", {"presets": ["@babel/preset-env"]}]]
    // },

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
    //browsers: ['ChromeWithoutExtensions'],
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
        flags: ['--disable-translate', '--disable-extensions']
      },
      "ChromeWithoutExtensions": {
        base: 'Chrome',
        flags: ['--disable-extensions', ' --auto-open-devtools-for-tabs']
      },
      "CustomChromeHeadlessWithDebugPort": {
        base: 'CustomChromeHeadless',
        flags: ['--remote-debugging-port=9222']
      }
    }
  });
};
