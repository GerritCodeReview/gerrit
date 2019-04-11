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
    CATEGORY_UI_LATENCY: 'UI Latency',
    CATEGORY_RPC: 'RPC Timing',
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

  const ERROR_DIALOG = {
    TYPE: 'error',
    CATEGORY: 'Error Dialog',
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

  const DRAFT_ACTION_TIMER = 'TimeBetweenDraftActions';
  const DRAFT_ACTION_TIMER_MAX = 2 * 60 * 1000; // 2 minutes.

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

  // The Polymer pass of JSCompiler requires this to be reassignable
  // eslint-disable-next-line prefer-const
  let GrReporting = Polymer({
    is: 'gr-reporting',
    _legacyUndefinedCheck: true,

    properties: {
      category: String,

      _baselines: {
        type: Object,
        value: STARTUP_TIMERS, // Shared across all instances.
      },

      _timers: {
        type: Object,
        value: {timeBetweenDraftActions: null}, // Shared across all instances.
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

    /**
     * The default reporter reports events immediately.
     * @param {string} type
     * @param {string} category
     * @param {string} eventName
     * @param {string|number} eventValue
     * @param {boolean|undefined} opt_noLog If true, the event will not be
     *     logged to the JS console.
     */
    defaultReporter(type, category, eventName, eventValue, opt_noLog) {
      const detail = {
        type,
        category,
        name: eventName,
        value: eventValue,
      };
      document.dispatchEvent(new CustomEvent(type, {detail}));
      if (opt_noLog) { return; }
      if (type === ERROR.TYPE && category === ERROR.CATEGORY) {
        console.error(eventValue.error || eventName);
      } else {
        console.log(eventName + (eventValue !== undefined ?
            (': ' + eventValue) : ''));
      }
    },

    /**
     * The caching reporter will queue reports until plugins have loaded, and
     * log events immediately if they're reported after plugins have loaded.
     * @param {string} type
     * @param {string} category
     * @param {string} eventName
     * @param {string|number} eventValue
     * @param {boolean|undefined} opt_noLog If true, the event will not be
     *     logged to the JS console.
     */
    cachingReporter(type, category, eventName, eventValue, opt_noLog) {
      if (type === ERROR.TYPE && category === ERROR.CATEGORY) {
        console.error(eventValue.error || eventName);
      }
      if (this._arePluginsLoaded()) {
        if (pending.length) {
          for (const args of pending.splice(0)) {
            this.reporter(...args);
          }
        }
        this.reporter(type, category, eventName, eventValue, opt_noLog);
      } else {
        pending.push([type, category, eventName, eventValue, opt_noLog]);
      }
    },

    /**
     * User-perceived app start time, should be reported when the app is ready.
     */
    appStarted(hidden) {
      this.reporter(TIMING.TYPE, TIMING.CATEGORY_UI_LATENCY,
          TIMING.APP_STARTED, this.now());
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
        this.reporter(TIMING.TYPE, TIMING.CATEGORY_UI_LATENCY,
            TIMING.PAGE_LOADED, loadTime);
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
      this._reportTiming(name, this.now() - baseTime);
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
      this._reportTiming(averageName, time / denominator);
    },

    /**
     * Send a timing report with an arbitrary time value.
     * @param {string} name Timing name.
     * @param {number} time The time to report as an integer of milliseconds.
     */
    _reportTiming(name, time) {
      this.reporter(TIMING.TYPE, TIMING.CATEGORY_UI_LATENCY, name,
          Math.round(time));
    },

    /**
     * Get a timer object to for reporing a user timing. The start time will be
     * the time that the object has been created, and the end time will be the
     * time that the "end" method is called on the object.
     * @param {string} name Timing name.
     * @returns {!Object} The timer object.
     */
    getTimer(name) {
      let called = false;
      let start;
      let max = null;

      const timer = {

        // Clear the timer and reset the start time.
        reset: () => {
          called = false;
          start = this.now();
          return timer;
        },

        // Stop the timer and report the intervening time.
        end: () => {
          if (called) {
            throw new Error(`Timer for "${name}" already ended.`);
          }
          called = true;
          const time = this.now() - start;

          // If a maximum is specified and the time exceeds it, do not report.
          if (max && time > max) { return timer; }

          this._reportTiming(name, time);
          return timer;
        },

        // Set a maximum reportable time. If a maximum is set and the timer is
        // ended after the specified amount of time, the value is not reported.
        withMaximum(maximum) {
          max = maximum;
          return timer;
        },
      };

      // The timer is initialized to its creation time.
      return timer.reset();
    },

    /**
     * Log timing information for an RPC.
     * @param {string} anonymizedUrl The URL of the RPC with tokens obfuscated.
     * @param {number} elapsed The time elapsed of the RPC.
     */
    reportRpcTiming(anonymizedUrl, elapsed) {
      this.reporter(TIMING.TYPE, TIMING.CATEGORY_RPC, 'RPC-' + anonymizedUrl,
          elapsed, true);
    },

    reportInteraction(eventName, opt_msg) {
      this.reporter(INTERACTION_TYPE, this.category, eventName, opt_msg);
    },

    /**
     * A draft interaction was started. Update the time-betweeen-draft-actions
     * timer.
     */
    recordDraftInteraction() {
      // If there is no timer defined, then this is the first interaction.
      // Set up the timer so that it's ready to record the intervening time when
      // called again.
      const timer = this._timers.timeBetweenDraftActions;
      if (!timer) {
        // Create a timer with a maximum length.
        this._timers.timeBetweenDraftActions = this.getTimer(DRAFT_ACTION_TIMER)
            .withMaximum(DRAFT_ACTION_TIMER_MAX);
        return;
      }

      // Mark the time and reinitialize the timer.
      timer.end().reset();
    },

    reportErrorDialog(message) {
      this.reporter(ERROR_DIALOG.TYPE, ERROR_DIALOG.CATEGORY,
          'ErrorDialog: ' + message);
    },
  });

  window.GrReporting = GrReporting;
  // Expose onerror installation so it would be accessible from tests.
  window.GrReporting._catchErrors = catchErrors;
  window.GrReporting.STARTUP_TIMERS = Object.assign({}, STARTUP_TIMERS);
})();
