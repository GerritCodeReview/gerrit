/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  // Latency reporting constants.
  const TIMING = {
    TYPE: 'timing-report',
    CATEGORY: 'UI Latency',
    // Reported events - alphabetize below.
    APP_STARTED: 'App Started',
    PAGE_LOADED: 'Page Loaded',
  };

  // Navigation reporting constants.
  const NAVIGATION = {
    TYPE: 'nav-report',
    CATEGORY: 'Location Changed',
    PAGE: 'Page',
  };

  const ERROR = {
    TYPE: 'error',
    CATEGORY: 'exception',
  };

  const INTERACTION_TYPE = 'interaction';

  const pending = [];

  const onError = function(oldOnError, msg, url, line, column, error) {
    if (oldOnError) {
      oldOnError(msg, url, line, column, error);
    }
    if (error) {
      line = line || error.lineNumber;
      column = column || error.columnNumber;
      msg = msg || error.toString();
    }
    const payload = {
      url,
      line,
      column,
      error,
    };
    GrReporting.prototype.reporter(ERROR.TYPE, ERROR.CATEGORY, msg, payload);
    return true;
  };

  const catchErrors = function(opt_context) {
    const context = opt_context || window;
    context.onerror = onError.bind(null, context.onerror);
    context.addEventListener('unhandledrejection', e => {
      const msg = e.reason.message;
      const payload = {
        error: e.reason,
      };
      GrReporting.prototype.reporter(ERROR.TYPE, ERROR.CATEGORY, msg, payload);
    });
  };
  catchErrors();

  const GrReporting = Polymer({
    is: 'gr-reporting',

    properties: {
      category: String,

      _baselines: {
        type: Array,
        value() { return {}; },
      },
    },

    get performanceTiming() {
      return window.performance.timing;
    },

    now() {
      return Math.round(10 * window.performance.now()) / 10;
    },

    reporter(...args) {
      const report = (Gerrit._arePluginsLoaded() && !pending.length) ?
        this.defaultReporter : this.cachingReporter;
      report.apply(this, args);
    },

    defaultReporter(type, category, eventName, eventValue) {
      const detail = {
        type,
        category,
        name: eventName,
        value: eventValue,
      };
      document.dispatchEvent(new CustomEvent(type, {detail}));
      if (type === ERROR.TYPE) {
        console.error(eventValue.error || eventName);
      } else {
        console.log(eventName + (eventValue !== undefined ?
            (': ' + eventValue) : ''));
      }
    },

    cachingReporter(type, category, eventName, eventValue) {
      if (type === ERROR.TYPE) {
        console.error(eventValue.error || eventName);
      }
      if (Gerrit._arePluginsLoaded()) {
        if (pending.length) {
          for (const args of pending.splice(0)) {
            this.reporter(...args);
          }
        }
        this.reporter(type, category, eventName, eventValue);
      } else {
        pending.push([type, category, eventName, eventValue]);
      }
    },

    /**
     * User-perceived app start time, should be reported when the app is ready.
     */
    appStarted() {
      const startTime =
          new Date().getTime() - this.performanceTiming.navigationStart;
      this.reporter(
          TIMING.TYPE, TIMING.CATEGORY, TIMING.APP_STARTED, startTime);
    },

    /**
     * Page load time, should be reported at any time after navigation.
     */
    pageLoaded() {
      if (this.performanceTiming.loadEventEnd === 0) {
        console.error('pageLoaded should be called after window.onload');
        this.async(this.pageLoaded, 100);
      } else {
        const loadTime = this.performanceTiming.loadEventEnd -
            this.performanceTiming.navigationStart;
        this.reporter(
            TIMING.TYPE, TIMING.CATEGORY, TIMING.PAGE_LOADED, loadTime);
      }
    },

    locationChanged(page) {
      this.reporter(
          NAVIGATION.TYPE, NAVIGATION.CATEGORY, NAVIGATION.PAGE, page);
    },

    pluginsLoaded() {
      this.timeEnd('PluginsLoaded');
    },

    /**
     * Reset named timer.
     */
    time(name) {
      this._baselines[name] = this.now();
    },

    /**
     * Finish named timer and report it to server.
     */
    timeEnd(name) {
      const baseTime = this._baselines[name] || 0;
      const time = Math.round(this.now() - baseTime) + 'ms';
      this.reporter(TIMING.TYPE, TIMING.CATEGORY, name, time);
      delete this._baselines[name];
    },

    reportInteraction(eventName, opt_msg) {
      this.reporter(INTERACTION_TYPE, this.category, eventName, opt_msg);
    },
  });

  window.GrReporting = GrReporting;
  // Expose onerror installation so it would be accessible from tests.
  window.GrReporting._catchErrors = catchErrors;
})();
