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

// Latency reporting constants.

const TIMING = {
  TYPE: 'timing-report',
  CATEGORY: {
    UI_LATENCY: 'UI Latency',
    RPC: 'RPC Timing',
  },
  EVENT: {
    APP_STARTED: 'App Started',
  },
};

const LIFECYCLE = {
  TYPE: 'lifecycle',
  CATEGORY: {
    DEFAULT: 'Default',
    EXTENSION_DETECTED: 'Extension detected',
    PLUGINS_INSTALLED: 'Plugins installed',
  },
};

const INTERACTION = {
  TYPE: 'interaction',
  CATEGORY: {
    DEFAULT: 'Default',
    VISIBILITY: 'Visibility',
  },
};

const NAVIGATION = {
  TYPE: 'nav-report',
  CATEGORY: {
    LOCATION_CHANGED: 'Location Changed',
  },
  EVENT: {
    PAGE: 'Page',
  },
};

const ERROR = {
  TYPE: 'error',
  CATEGORY: {
    EXCEPTION: 'exception',
    ERROR_DIALOG: 'Error Dialog',
  },
};

const TIMER = {
  CHANGE_DISPLAYED: 'ChangeDisplayed',
  CHANGE_LOAD_FULL: 'ChangeFullyLoaded',
  DASHBOARD_DISPLAYED: 'DashboardDisplayed',
  DIFF_VIEW_CONTENT_DISPLAYED: 'DiffViewOnlyContent',
  DIFF_VIEW_DISPLAYED: 'DiffViewDisplayed',
  DIFF_VIEW_LOAD_FULL: 'DiffViewFullyLoaded',
  FILE_LIST_DISPLAYED: 'FileListDisplayed',
  PLUGINS_LOADED: 'PluginsLoaded',
  STARTUP_CHANGE_DISPLAYED: 'StartupChangeDisplayed',
  STARTUP_CHANGE_LOAD_FULL: 'StartupChangeFullyLoaded',
  STARTUP_DASHBOARD_DISPLAYED: 'StartupDashboardDisplayed',
  STARTUP_DIFF_VIEW_CONTENT_DISPLAYED: 'StartupDiffViewOnlyContent',
  STARTUP_DIFF_VIEW_DISPLAYED: 'StartupDiffViewDisplayed',
  STARTUP_DIFF_VIEW_LOAD_FULL: 'StartupDiffViewFullyLoaded',
  STARTUP_FILE_LIST_DISPLAYED: 'StartupFileListDisplayed',
  WEB_COMPONENTS_READY: 'WebComponentsReady',
  METRICS_PLUGIN_LOADED: 'MetricsPluginLoaded',
};

const STARTUP_TIMERS = {};
STARTUP_TIMERS[TIMER.PLUGINS_LOADED] = 0;
STARTUP_TIMERS[TIMER.METRICS_PLUGIN_LOADED] = 0;
STARTUP_TIMERS[TIMER.STARTUP_CHANGE_DISPLAYED] = 0;
STARTUP_TIMERS[TIMER.STARTUP_CHANGE_LOAD_FULL] = 0;
STARTUP_TIMERS[TIMER.STARTUP_DASHBOARD_DISPLAYED] = 0;
STARTUP_TIMERS[TIMER.STARTUP_DIFF_VIEW_CONTENT_DISPLAYED] = 0;
STARTUP_TIMERS[TIMER.STARTUP_DIFF_VIEW_DISPLAYED] = 0;
STARTUP_TIMERS[TIMER.STARTUP_DIFF_VIEW_LOAD_FULL] = 0;
STARTUP_TIMERS[TIMER.STARTUP_FILE_LIST_DISPLAYED] = 0;
STARTUP_TIMERS[TIMING.EVENT.APP_STARTED] = 0;
// WebComponentsReady timer is triggered from gr-router.
STARTUP_TIMERS[TIMER.WEB_COMPONENTS_READY] = 0;

const DRAFT_ACTION_TIMER = 'TimeBetweenDraftActions';
const DRAFT_ACTION_TIMER_MAX = 2 * 60 * 1000; // 2 minutes.
const SLOW_RPC_THRESHOLD = 500;

export function initErrorReporter(appContext) {
  const reportingService = appContext.reportingService;
  const onError = function(oldOnError, msg, url, line, column, error) {
    if (oldOnError) {
      oldOnError(msg, url, line, column, error);
    }
    if (error) {
      line = line || error.lineNumber;
      column = column || error.columnNumber;
      let shortenedErrorStack = msg;
      if (error.stack) {
        const errorStackLines = error.stack.split('\n');
        shortenedErrorStack = errorStackLines.slice(0,
            Math.min(3, errorStackLines.length)).join('\n');
      }
      msg = shortenedErrorStack || error.toString();
    }
    const payload = {
      url,
      line,
      column,
      error,
    };
    reportingService.reporter(ERROR.TYPE, ERROR.CATEGORY.EXCEPTION,
        msg, payload);
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
      reportingService.reporter(ERROR.TYPE,
          ERROR.CATEGORY.EXCEPTION, msg, payload);
    });
  };

  catchErrors();

  // for testing
  return {catchErrors};
}

export function initPerformanceReporter(appContext) {
  const reportingService = appContext.reportingService;
  // PerformanceObserver interface is a browser API.
  if (window.PerformanceObserver) {
    const supportedEntryTypes = PerformanceObserver.supportedEntryTypes || [];
    // Safari doesn't support longtask yet
    if (supportedEntryTypes.includes('longtask')) {
      const catchLongJsTasks = new PerformanceObserver(list => {
        for (const task of list.getEntries()) {
          // We are interested in longtask longer than 200 ms (default is 50 ms)
          if (task.duration > 200) {
            reportingService.reporter(TIMING.TYPE,
                TIMING.CATEGORY.UI_LATENCY, `Task ${task.name}`,
                Math.round(task.duration), {}, false);
          }
        }
      });
      catchLongJsTasks.observe({entryTypes: ['longtask']});
    }
  }
}

export function initVisibilityReporter(appContext) {
  const reportingService = appContext.reportingService;
  document.addEventListener('visibilitychange', () => {
    reportingService.onVisibilityChange();
  });
}

// Calculates the time of Gerrit being in a background tab. When Gerrit reports
// a pageLoad metric it’s attached to its details for latency analysis.
// It resets on locationChange.
class HiddenDurationTimer {
  constructor() {
    this.reset();
  }

  reset() {
    this.accHiddenDurationMs = 0;
    this.lastVisibleTimestampMs = 0;
  }

  onVisibilityChange() {
    if (document.visibilityState === 'hidden') {
      this.lastVisibleTimestampMs = now();
    } else if (document.visibilityState === 'visible') {
      if (this.lastVisibleTimestampMs !== null) {
        this.accHiddenDurationMs += now() - this.lastVisibleTimestampMs;
        // Set to null for guarding against two 'visible' events in a row.
        this.lastVisibleTimestampMs = null;
      }
    }
  }

  get hiddenDurationMs() {
    if (document.visibilityState === 'hidden'
      && this.lastVisibleTimestampMs !== null) {
      return this.accHiddenDurationMs + now() - this.lastVisibleTimestampMs;
    }
    return this.accHiddenDurationMs;
  }
}

export function now() {
  return Math.round(window.performance.now());
}

export class GrReporting {
  constructor(flagsService) {
    this._flagsService = flagsService;
    this._baselines = STARTUP_TIMERS;
    this._timers = {
      timeBetweenDraftActions: null,
    };
    this._reportRepoName = undefined;
    this._pending = [];
    this._slowRpcList = [];
    this.hiddenDurationTimer = new HiddenDurationTimer();
  }

  get performanceTiming() {
    return window.performance.timing;
  }

  get slowRpcSnapshot() {
    return (this._slowRpcList || []).slice();
  }

  _arePluginsLoaded() {
    return this._baselines &&
      !this._baselines.hasOwnProperty(TIMER.PLUGINS_LOADED);
  }

  _isMetricsPluginLoaded() {
    return this._arePluginsLoaded() || this._baselines &&
      !this._baselines.hasOwnProperty(TIMER.METRICS_PLUGIN_LOADED);
  }

  /**
   * Reporter reports events. Events will be queued if metrics plugin is not
   * yet installed.
   *
   * @param {string} type
   * @param {string} category
   * @param {string} eventName
   * @param {string|number} eventValue
   * @param {Object} eventDetails
   * @param {boolean|undefined} opt_noLog If true, the event will not be
   *     logged to the JS console.
   */
  reporter(type, category, eventName, eventValue, eventDetails, opt_noLog) {
    const eventInfo = this._createEventInfo(type, category,
        eventName, eventValue, eventDetails);
    if (type === ERROR.TYPE && category === ERROR.CATEGORY.EXCEPTION) {
      console.error(eventValue && eventValue.error || eventName);
    }

    // We report events immediately when metrics plugin is loaded
    if (this._isMetricsPluginLoaded() && !this._pending.length) {
      this._reportEvent(eventInfo, opt_noLog);
    } else {
      // We cache until metrics plugin is loaded
      this._pending.push([eventInfo, opt_noLog]);
      if (this._isMetricsPluginLoaded()) {
        this._pending.forEach(([eventInfo, opt_noLog]) => {
          this._reportEvent(eventInfo, opt_noLog);
        });
        this._pending = [];
      }
    }
  }

  _reportEvent(eventInfo, opt_noLog) {
    const {type, value, name} = eventInfo;
    document.dispatchEvent(new CustomEvent(type, {detail: eventInfo}));
    if (opt_noLog) { return; }
    if (type !== ERROR.TYPE) {
      if (value !== undefined) {
        console.log(`Reporting: ${name}: ${value}`);
      } else {
        console.log(`Reporting: ${name}`);
      }
    }
  }

  _createEventInfo(type, category, name, value, eventDetails) {
    const eventInfo = {
      type,
      category,
      name,
      value,
      eventStart: now(),
    };

    if (typeof(eventDetails) === 'object' &&
      Object.entries(eventDetails).length !== 0) {
      eventInfo.eventDetails = JSON.stringify(eventDetails);
    }

    if (this._reportRepoName) {
      eventInfo.repoName = this._reportRepoName;
    }

    const isInBackgroundTab = document.visibilityState === 'hidden';
    if (isInBackgroundTab !== undefined) {
      eventInfo.inBackgroundTab = isInBackgroundTab;
    }

    if (this._flagsService.enabledExperiments.length) {
      eventInfo.enabledExperiments =
        JSON.stringify(this._flagsService.enabledExperiments);
    }

    return eventInfo;
  }

  /**
   * User-perceived app start time, should be reported when the app is ready.
   */
  appStarted() {
    this.timeEnd(TIMING.EVENT.APP_STARTED);
    this._reportNavResTimes();
  }

  onVisibilityChange() {
    this.hiddenDurationTimer.onVisibilityChange();
    const eventName = `Visibility changed to ${document.visibilityState}`;
    this.reporter(LIFECYCLE.TYPE, LIFECYCLE.CATEGORY.VISIBILITY,
        eventName, undefined, {
          hiddenDurationMs: this.hiddenDurationTimer.hiddenDurationMs,
        }, true);
  }

  /**
   * Browser's navigation and resource timings
   */
  _reportNavResTimes() {
    const perfEvents = Object.keys(this.performanceTiming.toJSON());
    perfEvents.forEach(
        eventName => this._reportPerformanceTiming(eventName)
    );
  }

  _reportPerformanceTiming(eventName, eventDetails) {
    const eventTiming = this.performanceTiming[eventName];
    if (eventTiming > 0) {
      const elapsedTime = eventTiming -
          this.performanceTiming.navigationStart;
      // NavResTime - Navigation and resource timings.
      this.reporter(TIMING.TYPE, TIMING.CATEGORY.UI_LATENCY,
          `NavResTime - ${eventName}`, elapsedTime, eventDetails, true);
    }
  }

  beforeLocationChanged() {
    for (const prop of Object.keys(this._baselines)) {
      delete this._baselines[prop];
    }
    this.time(TIMER.CHANGE_DISPLAYED);
    this.time(TIMER.CHANGE_LOAD_FULL);
    this.time(TIMER.DASHBOARD_DISPLAYED);
    this.time(TIMER.DIFF_VIEW_CONTENT_DISPLAYED);
    this.time(TIMER.DIFF_VIEW_DISPLAYED);
    this.time(TIMER.DIFF_VIEW_LOAD_FULL);
    this.time(TIMER.FILE_LIST_DISPLAYED);
    this._reportRepoName = undefined;
    // reset slow rpc list since here start page loads which report these rpcs
    this._slowRpcList = [];
    this.hiddenDurationTimer.reset();
  }

  locationChanged(page) {
    this.reporter(NAVIGATION.TYPE, NAVIGATION.CATEGORY.LOCATION_CHANGED,
        NAVIGATION.EVENT.PAGE, page);
  }

  dashboardDisplayed() {
    if (this._baselines.hasOwnProperty(TIMER.STARTUP_DASHBOARD_DISPLAYED)) {
      this.timeEnd(TIMER.STARTUP_DASHBOARD_DISPLAYED, this._pageLoadDetails());
    } else {
      this.timeEnd(TIMER.DASHBOARD_DISPLAYED, this._pageLoadDetails());
    }
  }

  changeDisplayed() {
    if (this._baselines.hasOwnProperty(TIMER.STARTUP_CHANGE_DISPLAYED)) {
      this.timeEnd(TIMER.STARTUP_CHANGE_DISPLAYED, this._pageLoadDetails());
    } else {
      this.timeEnd(TIMER.CHANGE_DISPLAYED, this._pageLoadDetails());
    }
  }

  changeFullyLoaded() {
    if (this._baselines.hasOwnProperty(TIMER.STARTUP_CHANGE_LOAD_FULL)) {
      this.timeEnd(TIMER.STARTUP_CHANGE_LOAD_FULL);
    } else {
      this.timeEnd(TIMER.CHANGE_LOAD_FULL);
    }
  }

  diffViewDisplayed() {
    if (this._baselines.hasOwnProperty(TIMER.STARTUP_DIFF_VIEW_DISPLAYED)) {
      this.timeEnd(TIMER.STARTUP_DIFF_VIEW_DISPLAYED, this._pageLoadDetails());
    } else {
      this.timeEnd(TIMER.DIFF_VIEW_DISPLAYED, this._pageLoadDetails());
    }
  }

  diffViewFullyLoaded() {
    if (this._baselines.hasOwnProperty(TIMER.STARTUP_DIFF_VIEW_LOAD_FULL)) {
      this.timeEnd(TIMER.STARTUP_DIFF_VIEW_LOAD_FULL);
    } else {
      this.timeEnd(TIMER.DIFF_VIEW_LOAD_FULL);
    }
  }

  diffViewContentDisplayed() {
    if (this._baselines.hasOwnProperty(
        TIMER.STARTUP_DIFF_VIEW_CONTENT_DISPLAYED)) {
      this.timeEnd(TIMER.STARTUP_DIFF_VIEW_CONTENT_DISPLAYED);
    } else {
      this.timeEnd(TIMER.DIFF_VIEW_CONTENT_DISPLAYED);
    }
  }

  fileListDisplayed() {
    if (this._baselines.hasOwnProperty(TIMER.STARTUP_FILE_LIST_DISPLAYED)) {
      this.timeEnd(TIMER.STARTUP_FILE_LIST_DISPLAYED);
    } else {
      this.timeEnd(TIMER.FILE_LIST_DISPLAYED);
    }
  }

  _pageLoadDetails() {
    const details = {
      rpcList: this.slowRpcSnapshot,
    };

    if (window.screen) {
      details.screenSize = {
        width: window.screen.width,
        height: window.screen.height,
      };
    }

    if (document && document.documentElement) {
      details.viewport = {
        width: document.documentElement.clientWidth,
        height: document.documentElement.clientHeight,
      };
    }

    if (window.performance && window.performance.memory) {
      const toMb = bytes => Math.round((bytes / (1024 * 1024)) * 100) / 100;
      details.usedJSHeapSizeMb =
        toMb(window.performance.memory.usedJSHeapSize);
    }

    details.hiddenDurationMs = this.hiddenDurationTimer.hiddenDurationMs;
    return details;
  }

  reportExtension(name) {
    this.reporter(LIFECYCLE.TYPE, LIFECYCLE.CATEGORY.EXTENSION_DETECTED, name);
  }

  pluginLoaded(name) {
    if (name.startsWith('metrics-')) {
      this.timeEnd(TIMER.METRICS_PLUGIN_LOADED);
    }
  }

  pluginsLoaded(pluginsList) {
    this.timeEnd(TIMER.PLUGINS_LOADED);
    this.reporter(
        LIFECYCLE.TYPE, LIFECYCLE.CATEGORY.PLUGINS_INSTALLED,
        LIFECYCLE.CATEGORY.PLUGINS_INSTALLED, undefined,
        {pluginsList: pluginsList || []}, true);
  }

  /**
   * Reset named timer.
   */
  time(name) {
    this._baselines[name] = now();
    window.performance.mark(`${name}-start`);
  }

  /**
   * Finish named timer and report it to server.
   */
  timeEnd(name, eventDetails) {
    if (!this._baselines.hasOwnProperty(name)) { return; }
    const baseTime = this._baselines[name];
    delete this._baselines[name];
    this._reportTiming(name, now() - baseTime, eventDetails);

    // Finalize the interval. Either from a registered start mark or
    // the navigation start time (if baseTime is 0).
    if (baseTime !== 0) {
      window.performance.measure(name, `${name}-start`);
    } else {
      // Microsft Edge does not handle the 2nd param correctly
      // (if undefined).
      window.performance.measure(name);
    }
  }

  /**
   * Reports just line timeEnd, but additionally reports an average given a
   * denominator and a separate reporiting name for the average.
   *
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
    const time = now() - baseTime;
    this._reportTiming(averageName, time / denominator);
  }

  /**
   * Send a timing report with an arbitrary time value.
   *
   * @param {string} name Timing name.
   * @param {number} time The time to report as an integer of milliseconds.
   * @param {Object} eventDetails non sensitive details
   */
  _reportTiming(name, time, eventDetails) {
    this.reporter(TIMING.TYPE, TIMING.CATEGORY.UI_LATENCY, name, time,
        eventDetails);
  }

  /**
   * Get a timer object to for reporing a user timing. The start time will be
   * the time that the object has been created, and the end time will be the
   * time that the "end" method is called on the object.
   *
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
        start = now();
        return timer;
      },

      // Stop the timer and report the intervening time.
      end: () => {
        if (called) {
          throw new Error(`Timer for "${name}" already ended.`);
        }
        called = true;
        const time = now() - start;

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
  }

  /**
   * Log timing information for an RPC.
   *
   * @param {string} anonymizedUrl The URL of the RPC with tokens obfuscated.
   * @param {number} elapsed The time elapsed of the RPC.
   */
  reportRpcTiming(anonymizedUrl, elapsed) {
    this.reporter(TIMING.TYPE, TIMING.CATEGORY.RPC, 'RPC-' + anonymizedUrl,
        elapsed, {}, true);
    if (elapsed >= SLOW_RPC_THRESHOLD) {
      this._slowRpcList.push({anonymizedUrl, elapsed});
    }
  }

  reportLifeCycle(eventName, details) {
    this.reporter(LIFECYCLE.TYPE, LIFECYCLE.CATEGORY.DEFAULT, eventName,
        undefined, details, true);
  }

  reportInteraction(eventName, details) {
    this.reporter(INTERACTION.TYPE, INTERACTION.CATEGORY.DEFAULT, eventName,
        undefined, details, true);
  }

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
  }

  reportErrorDialog(message) {
    this.reporter(ERROR.TYPE, ERROR.CATEGORY.ERROR_DIALOG,
        'ErrorDialog: ' + message, {error: new Error(message)});
  }

  setRepoName(repoName) {
    this._reportRepoName = repoName;
  }
}

export const DEFAULT_STARTUP_TIMERS = {...STARTUP_TIMERS};
