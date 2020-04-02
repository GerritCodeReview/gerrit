// Karma configuration
// Generated on Tue Mar 31 2020 19:01:50 GMT+0200 (Central European Summer Time)

module.exports = function(config) {
  config.set({

    // base path that will be used to resolve all patterns (eg. files, exclude)
    basePath: '',

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
      'node_modules/sinon/pkg/sinon.js',
      { pattern: 'app/**/*_ktest.js', type: 'module' },
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
      moduleDirs: [
          'app/node_modules',
          'node_modules'
      ]
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
    browsers: ['Chrome'],


    // Continuous Integration mode
    // if true, Karma captures browsers, runs the tests and exits
    singleRun: false,

    // Concurrency level
    // how many browser should be started simultaneous
    concurrency: Infinity,

    client: {
      mocha: {
        ui: 'tdd'
      }
    }
  })
};
