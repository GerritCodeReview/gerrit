'use strict';

// Include Gulp & tools we'll use
var gulp = require('gulp');
var $ = require('gulp-load-plugins')();
var del = require('del');
var runSequence = require('run-sequence');
var browserSync = require('browser-sync');
var merge = require('merge-stream');
var path = require('path');
var polybuild = require('polybuild');

var AUTOPREFIXER_BROWSERS = [
  'ie >= 10',
  'ie_mob >= 10',
  'ff >= 30',
  'chrome >= 34',
  'safari >= 7',
  'opera >= 23',
  'ios >= 7',
  'android >= 4.4',
  'bb >= 10'
];

var styleTask = function (stylesPath, srcs) {
  return gulp.src(srcs.map(function(src) {
      return path.join('app', stylesPath, src);
    }))
    .pipe($.changed(stylesPath, {extension: '.css'}))
    .pipe($.autoprefixer(AUTOPREFIXER_BROWSERS))
    .pipe(gulp.dest('.tmp/' + stylesPath))
    .pipe($.cssmin())
    .pipe(gulp.dest('dist/' + stylesPath))
    .pipe($.size({title: stylesPath}));
};

var jshintTask = function (src) {
  return gulp.src(src)
    .pipe($.jshint.extract()) // Extract JS from .html files
    .pipe($.jshint())
    .pipe($.jshint.reporter('jshint-stylish'))
    .pipe($.if(!browserSync.active, $.jshint.reporter('fail')));
};

var imageOptimizeTask = function (src, dest) {
  return gulp.src(src)
    .pipe($.cache($.imagemin({
      progressive: true,
      interlaced: true
    })))
    .pipe(gulp.dest(dest))
    .pipe($.size({title: 'images'}));
};

var optimizeHtmlTask = function (src, dest) {
  var assets = $.useref.assets({searchPath: ['.tmp', 'app', 'dist']});

  return gulp.src(src)
    // Replace path for vulcanized assets
    .pipe($.if('*.html', $.replace('elements/elements.html', 'elements/elements.vulcanized.html')))
    .pipe(assets)
    // Concatenate and minify JavaScript
    .pipe($.if('*.js', $.uglify({preserveComments: 'some'})))
    // Concatenate and minify styles
    // In case you are still using useref build blocks
    .pipe($.if('*.css', $.cssmin()))
    .pipe(assets.restore())
    .pipe($.useref())
    // Minify any HTML
    .pipe($.if('*.html', $.minifyHtml({
      quotes: true,
      empty: true,
      spare: true
    })))
    // Output files
    .pipe(gulp.dest(dest))
    .pipe($.size({title: 'html'}));
};

// Compile and automatically prefix stylesheets
gulp.task('styles', function () {
  return styleTask('styles', ['**/*.css']);
});

gulp.task('elements', function () {
  return styleTask('elements', ['**/*.css']);
});

// Lint JavaScript
gulp.task('jshint', function () {
  return jshintTask([
      'app/scripts/**/*.js',
      'app/elements/**/*.js',
      'app/elements/**/*.html',
      'gulpfile.js'
    ])
    .pipe($.jshint.extract()) // Extract JS from .html files
    .pipe($.jshint())
    .pipe($.jshint.reporter('jshint-stylish'))
    .pipe($.if(!browserSync.active, $.jshint.reporter('fail')));
});

// Optimize images
gulp.task('images', function () {
  return imageOptimizeTask('app/images/**/*', 'dist/images');
});

// Copy all files at the root level (app)
gulp.task('copy', function () {
  var app = gulp.src([
    'app/*',
    '!app/test'
  ], {
    dot: true
  }).pipe(gulp.dest('dist'));

  var bower = gulp.src([
    'app/bower_components/**/*'
  ]).pipe(gulp.dest('dist/bower_components'));

  var elements = gulp.src(['app/elements/**/*.html',
                           'app/elements/**/*.css',
                           'app/elements/**/*.js'])
    .pipe(gulp.dest('dist/elements'));

  var vulcanized = gulp.src(['app/elements/elements.html'])
    .pipe($.rename('elements.vulcanized.html'))
    .pipe(gulp.dest('dist/elements'));

  return merge(app, bower, elements, vulcanized)
    .pipe($.size({title: 'copy'}));
});

// Copy web fonts to dist
gulp.task('fonts', function () {
  return gulp.src(['app/fonts/**'])
    .pipe(gulp.dest('dist/fonts'))
    .pipe($.size({title: 'fonts'}));
});

// Scan your HTML for assets & optimize them
gulp.task('html', function () {
  return optimizeHtmlTask(
    ['app/**/*.html', '!app/{elements,test}/**/*.html'],
    'dist');
});

// Polybuild will take care of inlining HTML imports,
// scripts and CSS for you.
gulp.task('vulcanize', function () {
  return gulp.src('dist/index.html')
    .pipe(polybuild({maximumCrush: true}))
    .pipe(gulp.dest('dist/'));
});

// If you require more granular configuration of Vulcanize
// than polybuild provides, follow instructions from readme at:
// https://github.com/PolymerElements/polymer-starter-kit/#if-you-require-more-granular-configuration-of-vulcanize-than-polybuild-provides-you-an-option-by

// Rename Polybuild's index.build.html to index.html
gulp.task('rename-index', function () {
  gulp.src('dist/index.build.html')
    .pipe($.rename('index.html'))
    .pipe(gulp.dest('dist/'));
  return del(['dist/index.build.html']);
});

// Clean output directory
gulp.task('clean', function (cb) {
  del(['.tmp', 'dist'], cb);
});

// Build production files, the default task
gulp.task('default', ['clean'], function (cb) {
  // Uncomment 'cache-config' after 'rename-index' if you are going to use service workers.
  runSequence(
    ['copy', 'styles'],
    'elements',
    ['jshint', 'images', 'fonts', 'html'],
    'vulcanize','rename-index', // 'cache-config',
    cb);
});

// Load tasks for web-component-tester
// Adds tasks for `gulp test:local` and `gulp test:remote`
require('web-component-tester').gulp.init(gulp);

// Load custom tasks from the `tasks` directory
try { require('require-dir')('tasks'); } catch (err) {}
