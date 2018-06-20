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

  // Plugin-related reporting constants.
  const PLUGINS = {
    TYPE: 'lifecycle',
    // Reported events - alphabetize below.
    INSTALLED: 'Plugins installed',
  };

  // Chrome extension-related reporting constants.
  const EXTENSION = {
    TYPE: 'lifecycle',
    // Reported events - alphabetize below.
    DETECTED: 'Extension detected',
  };

  // Page visibility related constants.
  const PAGE_VISIBILITY = {
    TYPE: 'lifecycle',
    CATEGORY: 'Page Visibility',
    // Reported events - alphabetize below.
    STARTED_HIDDEN: 'hidden',
  };

  // Frame rate related constants.
  const JANK = {
    TYPE: 'lifecycle',
    CATEGORY: 'UI Latency',
    // Reported events - alphabetize below.
    COUNT: 'Jank count',
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

  const TIMER = {
    CHANGE_DISPLAYED: 'ChangeDisplayed',
    CHANGE_LOAD_FULL: 'ChangeFullyLoaded',
    DASHBOARD_DISPLAYED: 'DashboardDisplayed',
    DIFF_VIEW_DISPLAYED: 'DiffViewDisplayed',
    FILE_LIST_DISPLAYED: 'FileListDisplayed',
    PLUGINS_LOADED: 'PluginsLoaded',
    STARTUP_CHANGE_DISPLAYED: 'StartupChangeDisplayed',
    STARTUP_CHANGE_LOAD_FULL: 'StartupChangeFullyLoaded',
    STARTUP_DASHBOARD_DISPLAYED: 'StartupDashboardDisplayed',
    STARTUP_DIFF_VIEW_DISPLAYED: 'StartupDiffViewDisplayed',
    STARTUP_FILE_LIST_DISPLAYED: 'StartupFileListDisplayed',
    WEB_COMPONENTS_READY: 'WebComponentsReady',
  };

  const STARTUP_TIMERS = {};
  STARTUP_TIMERS[TIMER.PLUGINS_LOADED] = 0;
  STARTUP_TIMERS[TIMER.STARTUP_CHANGE_DISPLAYED] = 0;
  STARTUP_TIMERS[TIMER.STARTUP_CHANGE_LOAD_FULL] = 0;
  STARTUP_TIMERS[TIMER.STARTUP_DASHBOARD_DISPLAYED] = 0;
  STARTUP_TIMERS[TIMER.STARTUP_DIFF_VIEW_DISPLAYED] = 0;
  STARTUP_TIMERS[TIMER.STARTUP_FILE_LIST_DISPLAYED] = 0;
  // WebComponentsReady timer is triggered from gr-router.
  STARTUP_TIMERS[TIMER.WEB_COMPONENTS_READY] = 0;

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

  GrJankDetector.start();

  const GrReporting = Polymer({
    is: 'gr-reporting',

    properties: {
      category: String,

      _baselines: {
        type: Object,
        value: STARTUP_TIMERS, // Shared across all instances.
      },
    },

    get performanceTiming() {
      return window.performance.timing;
    },

    now() {
      return window.performance.now();
    },

    _arePluginsLoaded() {
      return this._baselines &&
        !this._baselines.hasOwnProperty(TIMER.PLUGINS_LOADED);
    },

    reporter(...args) {
      const report = (this._arePluginsLoaded() && !pending.length) ?
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
      if (this._arePluginsLoaded()) {
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
    appStarted(hidden) {
      const startTime =
          new Date().getTime() - this.performanceTiming.navigationStart;
      this.reporter(
          TIMING.TYPE, TIMING.CATEGORY, TIMING.APP_STARTED, startTime);
      if (hidden) {
        this.reporter(PAGE_VISIBILITY.TYPE, PAGE_VISIBILITY.CATEGORY,
            PAGE_VISIBILITY.STARTED_HIDDEN);
      }
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

    beforeLocationChanged() {
      if (GrJankDetector.jank > 0) {
        this.reporter(
            JANK.TYPE, JANK.CATEGORY, JANK.COUNT, GrJankDetector.jank);
        GrJankDetector.jank = 0;
      }
      for (const prop of Object.keys(this._baselines)) {
        delete this._baselines[prop];
      }
      this.time(TIMER.CHANGE_DISPLAYED);
      this.time(TIMER.CHANGE_LOAD_FULL);
      this.time(TIMER.DASHBOARD_DISPLAYED);
      this.time(TIMER.DIFF_VIEW_DISPLAYED);
      this.time(TIMER.FILE_LIST_DISPLAYED);
    },

    locationChanged(page) {
      this.reporter(
          NAVIGATION.TYPE, NAVIGATION.CATEGORY, NAVIGATION.PAGE, page);
    },

    dashboardDisplayed() {
      if (this._baselines.hasOwnProperty(TIMER.STARTUP_DASHBOARD_DISPLAYED)) {
        this.timeEnd(TIMER.STARTUP_DASHBOARD_DISPLAYED);
      } else {
        this.timeEnd(TIMER.DASHBOARD_DISPLAYED);
      }
    },

    changeDisplayed() {
      if (this._baselines.hasOwnProperty(TIMER.STARTUP_CHANGE_DISPLAYED)) {
        this.timeEnd(TIMER.STARTUP_CHANGE_DISPLAYED);
      } else {
        this.timeEnd(TIMER.CHANGE_DISPLAYED);
      }
    },

    changeFullyLoaded() {
      if (this._baselines.hasOwnProperty(TIMER.STARTUP_CHANGE_LOAD_FULL)) {
        this.timeEnd(TIMER.STARTUP_CHANGE_LOAD_FULL);
      } else {
        this.timeEnd(TIMER.CHANGE_LOAD_FULL);
      }
    },

    diffViewDisplayed() {
      if (this._baselines.hasOwnProperty(TIMER.STARTUP_DIFF_VIEW_DISPLAYED)) {
        this.timeEnd(TIMER.STARTUP_DIFF_VIEW_DISPLAYED);
      } else {
        this.timeEnd(TIMER.DIFF_VIEW_DISPLAYED);
      }
    },

    fileListDisplayed() {
      if (this._baselines.hasOwnProperty(TIMER.STARTUP_FILE_LIST_DISPLAYED)) {
        this.timeEnd(TIMER.STARTUP_FILE_LIST_DISPLAYED);
      } else {
        this.timeEnd(TIMER.FILE_LIST_DISPLAYED);
      }
    },

    reportExtension(name) {
      this.reporter(EXTENSION.TYPE, EXTENSION.DETECTED, name);
    },

    pluginsLoaded(pluginsList) {
      this.timeEnd(TIMER.PLUGINS_LOADED);
      this.reporter(
          PLUGINS.TYPE, PLUGINS.INSTALLED, (pluginsList || []).join(','));
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
      if (!this._baselines.hasOwnProperty(name)) { return; }
      const baseTime = this._baselines[name];
      const time = Math.round(this.now() - baseTime);
      this.reporter(TIMING.TYPE, TIMING.CATEGORY, name, time);
      delete this._baselines[name];
    },

    /**
     * Reports just line timeEnd, but additionally reports an average given a
     * denominator and a separate reporiting name for the average.
     * @param {string} name Timing name.
     * @param {string} averageName Average timing name.
     * @param {number} denominator Number by which to divide the total to
     *     compute the average.
     */
    timeEndWithAverage(name, averageName, denominator) {
      if (!this._baselines.hasOwnProperty(name)) { return; }
      const baseTime = this._baselines[name];
      this.timeEnd(name);

      // Guard against division by zero.
      if (!denominator) { return; }
      const time = Math.round(this.now() - baseTime);
      this.reporter(TIMING.TYPE, TIMING.CATEGORY, averageName,
          Math.round(time / denominator));
    },

    reportInteraction(eventName, opt_msg) {
      this.reporter(INTERACTION_TYPE, this.category, eventName, opt_msg);
    },
  });

  window.GrReporting = GrReporting;
  // Expose onerror installation so it would be accessible from tests.
  window.GrReporting._catchErrors = catchErrors;
  window.GrReporting.STARTUP_TIMERS = Object.assign({}, STARTUP_TIMERS);
})();
