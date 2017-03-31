// Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  // Latency reporting constants.
  var TIMING = {
    TYPE: 'timing-report',
    CATEGORY: 'UI Latency',
    // Reported events - alphabetize below.
    APP_STARTED: 'App Started',
    PAGE_LOADED: 'Page Loaded',
  };

  // Navigation reporting constants.
  var NAVIGATION = {
    TYPE: 'nav-report',
    CATEGORY: 'Location Changed',
    PAGE: 'Page',
  };

  var ERROR = {
    TYPE: 'error',
    CATEGORY: 'exception',
  };

  var INTERACTION_TYPE = 'interaction';

  var CHANGE_VIEW_REGEX = /^\/c\/\d+\/?\d*$/;
  var DIFF_VIEW_REGEX = /^\/c\/\d+\/\d+\/.+$/;

  var pending = [];

  var onError = function(oldOnError, msg, url, line, column, error) {
    if (oldOnError) {
      oldOnError(msg, url, line, column, error);
    }
    if (error) {
      line = line || error.lineNumber;
      column = column || error.columnNumber;
      msg = msg || error.toString();
    }
    var payload = {
      url: url,
      line: line,
      column: column,
      error: error,
    };
    GrReporting.prototype.reporter(ERROR.TYPE, ERROR.CATEGORY, msg, payload);
    return true;
  };

  var catchErrors = function(opt_context) {
    var context = opt_context || window;
    context.onerror = onError.bind(null, context.onerror);
  };
  catchErrors();

  var GrReporting = Polymer({
    is: 'gr-reporting',

    properties: {
      category: String,

      _baselines: {
        type: Array,
        value: function() { return {}; },
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
    ],

    get performanceTiming() {
      return window.performance.timing;
    },

    now: function() {
      return Math.round(10 * window.performance.now()) / 10;
    },

    reporter: function() {
      var report = (Gerrit._arePluginsLoaded() && !pending.length) ?
        this.defaultReporter : this.cachingReporter;
      report.apply(this, arguments);
    },

    defaultReporter: function(type, category, eventName, eventValue) {
      var detail = {
        type: type,
        category: category,
        name: eventName,
        value: eventValue,
      };
      document.dispatchEvent(new CustomEvent(type, {detail: detail}));
      if (type === ERROR.TYPE) {
        console.error(eventValue.error || eventName);
      } else {
        console.log(eventName + (eventValue !== undefined ?
            (': ' + eventValue) : ''));
      }
    },

    cachingReporter: function(type, category, eventName, eventValue) {
      if (type === ERROR.TYPE) {
        console.error(eventValue.error || eventName);
      }
      if (Gerrit._arePluginsLoaded()) {
        if (pending.length) {
          pending.splice(0).forEach(function(args) {
            this.reporter.apply(this, args);
          }, this);
        }
        this.reporter(type, category, eventName, eventValue);
      } else {
        pending.push([type, category, eventName, eventValue]);
      }
    },

    /**
     * User-perceived app start time, should be reported when the app is ready.
     */
    appStarted: function() {
      var startTime =
          new Date().getTime() - this.performanceTiming.navigationStart;
      this.reporter(
          TIMING.TYPE, TIMING.CATEGORY, TIMING.APP_STARTED, startTime);
    },

    /**
     * Page load time, should be reported at any time after navigation.
     */
    pageLoaded: function() {
      if (this.performanceTiming.loadEventEnd === 0) {
        console.error('pageLoaded should be called after window.onload');
        this.async(this.pageLoaded, 100);
      } else {
        var loadTime = this.performanceTiming.loadEventEnd -
            this.performanceTiming.navigationStart;
        this.reporter(
          TIMING.TYPE, TIMING.CATEGORY, TIMING.PAGE_LOADED, loadTime);
      }
    },

    locationChanged: function() {
      var page = '';
      var pathname = this._getPathname();
      if (pathname.indexOf('/q/') === 0) {
        page = this.getBaseUrl() + '/q/';
      } else if (pathname.match(CHANGE_VIEW_REGEX)) { // change view
        page = this.getBaseUrl() + '/c/';
      } else if (pathname.match(DIFF_VIEW_REGEX)) { // diff view
        page = this.getBaseUrl() + '/c//COMMIT_MSG';
      } else {
        // Ignore other page changes.
        return;
      }
      this.reporter(
          NAVIGATION.TYPE, NAVIGATION.CATEGORY, NAVIGATION.PAGE, page);
    },

    pluginsLoaded: function() {
      this.timeEnd('PluginsLoaded');
    },

    _getPathname: function() {
      return '/' + window.location.pathname.substring(this.getBaseUrl().length);
    },

    /**
     * Reset named timer.
     */
    time: function(name) {
      this._baselines[name] = this.now();
    },

    /**
     * Finish named timer and report it to server.
     */
    timeEnd: function(name) {
      var baseTime = this._baselines[name] || 0;
      var time = this.now() - baseTime;
      this.reporter(TIMING.TYPE, TIMING.CATEGORY, name, time);
      delete this._baselines[name];
    },

    reportInteraction: function(eventName, opt_msg) {
      this.reporter(INTERACTION_TYPE, this.category, eventName, opt_msg);
    },
  });

  window.GrReporting = GrReporting;
  // Expose onerror installation so it would be accessible from tests.
  window.GrReporting._catchErrors = catchErrors;

})();
