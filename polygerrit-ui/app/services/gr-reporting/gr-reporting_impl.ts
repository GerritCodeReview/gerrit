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
import {AppContext} from '../app-context';
import {FlagsService} from '../flags/flags';
import {EventValue, ReportingService, Timer} from './gr-reporting';
import {hasOwnProperty} from '../../utils/common-util';
import {NumericChangeId} from '../../types/common';
import {EventDetails} from '../../api/reporting';
import {PluginApi} from '../../api/plugin';
import {Execution, LifeCycle, Timing} from '../../constants/reporting';

// Latency reporting constants.

const TIMING = {
  TYPE: 'timing-report',
  CATEGORY: {
    UI_LATENCY: 'UI Latency',
    RPC: 'RPC Timing',
  },
};

const LIFECYCLE = {
  TYPE: 'lifecycle',
  CATEGORY: {
    DEFAULT: 'Default',
    EXTENSION_DETECTED: 'Extension detected',
    PLUGINS_INSTALLED: 'Plugins installed',
    VISIBILITY: 'Visibility',
    EXECUTION: 'Execution',
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

const STARTUP_TIMERS: {[name: string]: number} = {
  [Timing.PLUGINS_LOADED]: 0,
  [Timing.METRICS_PLUGIN_LOADED]: 0,
  [Timing.STARTUP_CHANGE_DISPLAYED]: 0,
  [Timing.STARTUP_CHANGE_LOAD_FULL]: 0,
  [Timing.STARTUP_DASHBOARD_DISPLAYED]: 0,
  [Timing.STARTUP_DIFF_VIEW_CONTENT_DISPLAYED]: 0,
  [Timing.STARTUP_DIFF_VIEW_DISPLAYED]: 0,
  [Timing.STARTUP_DIFF_VIEW_LOAD_FULL]: 0,
  [Timing.STARTUP_FILE_LIST_DISPLAYED]: 0,
  [Timing.APP_STARTED]: 0,
  // WebComponentsReady timer is triggered from gr-router.
  [Timing.WEB_COMPONENTS_READY]: 0,
};

const DRAFT_ACTION_TIMER = 'TimeBetweenDraftActions';
const DRAFT_ACTION_TIMER_MAX = 2 * 60 * 1000; // 2 minutes.
const SLOW_RPC_THRESHOLD = 500;

export function initErrorReporter(appContext: AppContext) {
  const reportingService = appContext.reportingService;

  const normalizeError = (err: Error | unknown) => {
    if (err instanceof Error) {
      return err;
    }
    let msg = '';
    if (typeof err === 'string') {
      msg += err;
    } else {
      msg += JSON.stringify(err);
    }
    const error = new Error(msg);
    error.stack = 'unknown';
    return error;
  };
  // TODO(dmfilippov): TS-fix-any oldOnError - define correct type
  const onError = function (
    oldOnError: Function,
    msg: Event | string,
    url?: string,
    line?: number,
    column?: number,
    error?: Error
  ) {
    if (oldOnError) {
      oldOnError(msg, url, line, column, error);
    }
    if (error) {
      line = line ?? error.lineNumber;
      column = column ?? error.columnNumber;
    }
    reportingService.error(normalizeError(error), 'onError', {
      line,
      column,
      url,
      msg,
    });
    return true;
  };
  // TODO(dmfilippov): TS-fix-any unclear what is context
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const catchErrors = function (opt_context?: any) {
    const context = opt_context || window;
    const oldOnError = context.onerror;
    context.onerror = (
      event: Event | string,
      source?: string,
      lineno?: number,
      colno?: number,
      error?: Error
    ) => {
      return onError(oldOnError, event, source, lineno, colno, error);
    };
    context.addEventListener(
      'unhandledrejection',
      (e: PromiseRejectionEvent) => {
        reportingService.error(normalizeError(e.reason), 'unhandledrejection');
      }
    );
  };

  catchErrors();

  // for testing
  return {catchErrors};
}

export function initPerformanceReporter(appContext: AppContext) {
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
            reportingService.reporter(
              TIMING.TYPE,
              TIMING.CATEGORY.UI_LATENCY,
              `Task ${task.name}`,
              Math.round(task.duration),
              {},
              false
            );
          }
        }
      });
      catchLongJsTasks.observe({entryTypes: ['longtask']});
    }
  }
}

export function initVisibilityReporter(appContext: AppContext) {
  const reportingService = appContext.reportingService;
  document.addEventListener('visibilitychange', () => {
    reportingService.onVisibilityChange();
  });
}

// Calculates the time of Gerrit being in a background tab. When Gerrit reports
// a pageLoad metric it’s attached to its details for latency analysis.
// It resets on locationChange.
class HiddenDurationTimer {
  public accHiddenDurationMs = 0;

  public lastVisibleTimestampMs: number | null = null;

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
    if (
      document.visibilityState === 'hidden' &&
      this.lastVisibleTimestampMs !== null
    ) {
      return this.accHiddenDurationMs + now() - this.lastVisibleTimestampMs;
    }
    return this.accHiddenDurationMs;
  }
}

export function now() {
  return Math.round(window.performance.now());
}

type PeformanceTimingEventName = keyof Omit<PerformanceTiming, 'toJSON'>;

interface EventInfo {
  type: string;
  category: string;
  name: string;
  value?: EventValue;
  eventStart: number;
  eventDetails?: string;
  repoName?: string;
  changeId?: string;
  inBackgroundTab?: boolean;
  enabledExperiments?: string;
}

interface PageLoadDetails {
  rpcList: SlowRpcCall[];
  hiddenDurationMs: number;
  screenSize?: {width: number; height: number};
  viewport?: {width: number; height: number};
  usedJSHeapSizeMb?: number;
}

interface SlowRpcCall {
  anonymizedUrl: string;
  elapsed: number;
}

type PendingReportInfo = [EventInfo, boolean | undefined];

export class GrReporting implements ReportingService {
  private readonly _flagsService: FlagsService;

  private readonly _baselines = STARTUP_TIMERS;

  private reportRepoName: string | undefined;

  private reportChangeId: NumericChangeId | undefined;

  private timers: {timeBetweenDraftActions: Timer | null} = {
    timeBetweenDraftActions: null,
  };

  private pending: PendingReportInfo[] = [];

  private slowRpcList: SlowRpcCall[] = [];

  /**
   * Keeps track of which ids were already reported to have been executed.
   * Execution ids should only be reported once per session.
   */
  private executionReported = new Set<string>();

  public readonly hiddenDurationTimer = new HiddenDurationTimer();

  constructor(flagsService: FlagsService) {
    this._flagsService = flagsService;
  }

  private get performanceTiming() {
    return window.performance.timing;
  }

  private get slowRpcSnapshot() {
    return (this.slowRpcList || []).slice();
  }

  private _arePluginsLoaded() {
    return (
      this._baselines && !hasOwnProperty(this._baselines, Timing.PLUGINS_LOADED)
    );
  }

  private _isMetricsPluginLoaded() {
    return (
      this._arePluginsLoaded() ||
      (this._baselines &&
        !hasOwnProperty(this._baselines, Timing.METRICS_PLUGIN_LOADED))
    );
  }

  /**
   * Reporter reports events. Events will be queued if metrics plugin is not
   * yet installed.
   *
   * @param noLog If true, the event will not be logged to the JS console.
   */
  reporter(
    type: string,
    category: string,
    eventName: string,
    eventValue?: EventValue,
    eventDetails?: EventDetails,
    noLog?: boolean
  ) {
    const eventInfo = this._createEventInfo(
      type,
      category,
      eventName,
      eventValue,
      eventDetails
    );
    if (type === ERROR.TYPE && category === ERROR.CATEGORY.EXCEPTION) {
      console.error(
        (typeof eventValue === 'object' && eventValue.error) || eventName
      );
    }

    // We report events immediately when metrics plugin is loaded
    if (this._isMetricsPluginLoaded() && !this.pending.length) {
      this._reportEvent(eventInfo, noLog);
    } else {
      // We cache until metrics plugin is loaded
      this.pending.push([eventInfo, noLog]);
      if (this._isMetricsPluginLoaded()) {
        this.pending.forEach(([eventInfo, opt_noLog]) => {
          this._reportEvent(eventInfo, opt_noLog);
        });
        this.pending = [];
      }
    }
  }

  private _reportEvent(eventInfo: EventInfo, opt_noLog?: boolean) {
    const {type, value, name} = eventInfo;
    document.dispatchEvent(new CustomEvent(type, {detail: eventInfo}));
    if (opt_noLog) {
      return;
    }
    if (type !== ERROR.TYPE) {
      if (value !== undefined) {
        console.info(`Reporting: ${name}: ${value}`);
      } else {
        console.info(`Reporting: ${name}`);
      }
    }
  }

  private _createEventInfo(
    type: string,
    category: string,
    name: string,
    value?: EventValue,
    eventDetails?: EventDetails
  ): EventInfo {
    const eventInfo: EventInfo = {
      type,
      category,
      name,
      value,
      eventStart: now(),
    };

    if (
      typeof eventDetails === 'object' &&
      Object.entries(eventDetails).length !== 0
    ) {
      eventInfo.eventDetails = JSON.stringify(eventDetails);
    }

    if (this.reportRepoName) {
      eventInfo.repoName = this.reportRepoName;
    }
    if (this.reportChangeId) {
      eventInfo.changeId = `${this.reportChangeId}`;
    }

    const isInBackgroundTab = document.visibilityState === 'hidden';
    if (isInBackgroundTab !== undefined) {
      eventInfo.inBackgroundTab = isInBackgroundTab;
    }

    if (this._flagsService.enabledExperiments.length) {
      eventInfo.enabledExperiments = JSON.stringify(
        this._flagsService.enabledExperiments
      );
    }

    return eventInfo;
  }

  /**
   * User-perceived app start time, should be reported when the app is ready.
   */
  appStarted() {
    this.timeEnd(Timing.APP_STARTED);
    this._reportNavResTimes();
  }

  onVisibilityChange() {
    this.hiddenDurationTimer.onVisibilityChange();
    let eventName;
    if (document.visibilityState === 'hidden') {
      eventName = LifeCycle.VISIBILILITY_HIDDEN;
    } else if (document.visibilityState === 'visible') {
      eventName = LifeCycle.VISIBILILITY_VISIBLE;
    }
    if (eventName)
      this.reporter(
        LIFECYCLE.TYPE,
        LIFECYCLE.CATEGORY.VISIBILITY,
        eventName,
        undefined,
        {
          hiddenDurationMs: this.hiddenDurationTimer.hiddenDurationMs,
        },
        false
      );
  }

  /**
   * Browser's navigation and resource timings
   */
  private _reportNavResTimes() {
    const perfEvents = Object.keys(this.performanceTiming.toJSON());
    perfEvents.forEach(eventName =>
      this._reportPerformanceTiming(eventName as PeformanceTimingEventName)
    );
  }

  private _reportPerformanceTiming(
    eventName: PeformanceTimingEventName,
    eventDetails?: EventDetails
  ) {
    const eventTiming = this.performanceTiming[eventName];
    if (eventTiming > 0) {
      const elapsedTime = eventTiming - this.performanceTiming.navigationStart;
      // NavResTime - Navigation and resource timings.
      this.reporter(
        TIMING.TYPE,
        TIMING.CATEGORY.UI_LATENCY,
        `NavResTime - ${eventName}`,
        elapsedTime,
        eventDetails,
        true
      );
    }
  }

  beforeLocationChanged() {
    for (const prop of Object.keys(this._baselines)) {
      delete this._baselines[prop];
    }
    this.time(Timing.CHANGE_DISPLAYED);
    this.time(Timing.CHANGE_LOAD_FULL);
    this.time(Timing.DASHBOARD_DISPLAYED);
    this.time(Timing.DIFF_VIEW_CONTENT_DISPLAYED);
    this.time(Timing.DIFF_VIEW_DISPLAYED);
    this.time(Timing.DIFF_VIEW_LOAD_FULL);
    this.time(Timing.FILE_LIST_DISPLAYED);
    this.reportRepoName = undefined;
    this.reportChangeId = undefined;
    // reset slow rpc list since here start page loads which report these rpcs
    this.slowRpcList = [];
    this.hiddenDurationTimer.reset();
  }

  locationChanged(page: string) {
    this.reporter(
      NAVIGATION.TYPE,
      NAVIGATION.CATEGORY.LOCATION_CHANGED,
      NAVIGATION.EVENT.PAGE,
      page
    );
  }

  dashboardDisplayed() {
    if (hasOwnProperty(this._baselines, Timing.STARTUP_DASHBOARD_DISPLAYED)) {
      this.timeEnd(Timing.STARTUP_DASHBOARD_DISPLAYED, this._pageLoadDetails());
    } else {
      this.timeEnd(Timing.DASHBOARD_DISPLAYED, this._pageLoadDetails());
    }
  }

  changeDisplayed() {
    if (hasOwnProperty(this._baselines, Timing.STARTUP_CHANGE_DISPLAYED)) {
      this.timeEnd(Timing.STARTUP_CHANGE_DISPLAYED, this._pageLoadDetails());
    } else {
      this.timeEnd(Timing.CHANGE_DISPLAYED, this._pageLoadDetails());
    }
  }

  changeFullyLoaded() {
    if (hasOwnProperty(this._baselines, Timing.STARTUP_CHANGE_LOAD_FULL)) {
      this.timeEnd(Timing.STARTUP_CHANGE_LOAD_FULL);
    } else {
      this.timeEnd(Timing.CHANGE_LOAD_FULL);
    }
  }

  diffViewDisplayed() {
    if (hasOwnProperty(this._baselines, Timing.STARTUP_DIFF_VIEW_DISPLAYED)) {
      this.timeEnd(Timing.STARTUP_DIFF_VIEW_DISPLAYED, this._pageLoadDetails());
    } else {
      this.timeEnd(Timing.DIFF_VIEW_DISPLAYED, this._pageLoadDetails());
    }
  }

  diffViewFullyLoaded() {
    if (hasOwnProperty(this._baselines, Timing.STARTUP_DIFF_VIEW_LOAD_FULL)) {
      this.timeEnd(Timing.STARTUP_DIFF_VIEW_LOAD_FULL);
    } else {
      this.timeEnd(Timing.DIFF_VIEW_LOAD_FULL);
    }
  }

  diffViewContentDisplayed() {
    if (
      hasOwnProperty(
        this._baselines,
        Timing.STARTUP_DIFF_VIEW_CONTENT_DISPLAYED
      )
    ) {
      this.timeEnd(Timing.STARTUP_DIFF_VIEW_CONTENT_DISPLAYED);
    } else {
      this.timeEnd(Timing.DIFF_VIEW_CONTENT_DISPLAYED);
    }
  }

  fileListDisplayed() {
    if (hasOwnProperty(this._baselines, Timing.STARTUP_FILE_LIST_DISPLAYED)) {
      this.timeEnd(Timing.STARTUP_FILE_LIST_DISPLAYED);
    } else {
      this.timeEnd(Timing.FILE_LIST_DISPLAYED);
    }
  }

  private _pageLoadDetails(): PageLoadDetails {
    const details: PageLoadDetails = {
      rpcList: this.slowRpcSnapshot,
      hiddenDurationMs: this.hiddenDurationTimer.accHiddenDurationMs,
    };

    if (window.screen) {
      details.screenSize = {
        width: window.screen.width,
        height: window.screen.height,
      };
    }

    if (document?.documentElement) {
      details.viewport = {
        width: document.documentElement.clientWidth,
        height: document.documentElement.clientHeight,
      };
    }

    if (window.performance?.memory) {
      const toMb = (bytes: number) =>
        Math.round((bytes / (1024 * 1024)) * 100) / 100;
      details.usedJSHeapSizeMb = toMb(window.performance.memory.usedJSHeapSize);
    }

    details.hiddenDurationMs = this.hiddenDurationTimer.hiddenDurationMs;
    return details;
  }

  reportExtension(name: string) {
    this.reporter(
      LIFECYCLE.TYPE,
      LIFECYCLE.CATEGORY.EXTENSION_DETECTED,
      LifeCycle.EXTENSION_DETECTED,
      undefined,
      {name}
    );
  }

  pluginLoaded(name: string) {
    if (name.startsWith('metrics-')) {
      this.timeEnd(Timing.METRICS_PLUGIN_LOADED);
    }
  }

  pluginsLoaded(pluginsList?: string[]) {
    this.timeEnd(Timing.PLUGINS_LOADED);
    this.reporter(
      LIFECYCLE.TYPE,
      LIFECYCLE.CATEGORY.PLUGINS_INSTALLED,
      LifeCycle.PLUGINS_INSTALLED,
      undefined,
      {pluginsList: pluginsList || []},
      true
    );
  }

  /**
   * Reset named Timing.
   */
  time(name: Timing) {
    this._baselines[name] = now();
    window.performance.mark(`${name}-start`);
  }

  /**
   * Finish named timer and report it to server.
   */
  timeEnd(name: Timing, eventDetails?: EventDetails) {
    if (!hasOwnProperty(this._baselines, name)) {
      return;
    }
    const baseTime = this._baselines[name];
    delete this._baselines[name];
    this._reportTiming(name, now() - baseTime, eventDetails);

    // Finalize the interval. Either from a registered start mark or
    // the navigation start time (if baseTime is 0).
    if (baseTime !== 0) {
      window.performance.measure(name, `${name}-start`);
    } else {
      // Microsoft Edge does not handle the 2nd param correctly
      // (if undefined).
      window.performance.measure(name);
    }
  }

  /**
   * Reports just line timeEnd, but additionally reports an average given a
   * denominator and a separate reporting name for the average.
   *
   * @param name Timing name.
   * @param averageName Average timing name.
   * @param denominator Number by which to divide the total to
   *     compute the average.
   */
  timeEndWithAverage(name: Timing, averageName: Timing, denominator: number) {
    if (!hasOwnProperty(this._baselines, name)) {
      return;
    }
    const baseTime = this._baselines[name];
    this.timeEnd(name);

    // Guard against division by zero.
    if (!denominator) {
      return;
    }
    const time = now() - baseTime;
    this._reportTiming(averageName, time / denominator);
  }

  /**
   * Send a timing report with an arbitrary time value.
   *
   * @param name Timing name.
   * @param time The time to report as an integer of milliseconds.
   * @param eventDetails non sensitive details
   */
  private _reportTiming(
    name: string,
    time: number,
    eventDetails?: EventDetails
  ) {
    this.reporter(
      TIMING.TYPE,
      TIMING.CATEGORY.UI_LATENCY,
      name,
      time,
      eventDetails
    );
  }

  /**
   * Get a timer object to for reporting a user timing. The start time will be
   * the time that the object has been created, and the end time will be the
   * time that the "end" method is called on the object.
   */
  getTimer(name: string): Timer {
    let called = false;
    let start: number;
    let max: number | null = null;

    const timer: Timer = {
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
        if (max && time > max) {
          return timer;
        }

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
   * @param anonymizedUrl The URL of the RPC with tokens obfuscated.
   * @param elapsed The time elapsed of the RPC.
   */
  reportRpcTiming(anonymizedUrl: string, elapsed: number) {
    this.reporter(
      TIMING.TYPE,
      TIMING.CATEGORY.RPC,
      'RPC-' + anonymizedUrl,
      elapsed,
      {},
      true
    );
    if (elapsed >= SLOW_RPC_THRESHOLD) {
      this.slowRpcList.push({anonymizedUrl, elapsed});
    }
  }

  reportLifeCycle(eventName: LifeCycle, details: EventDetails) {
    this.reporter(
      LIFECYCLE.TYPE,
      LIFECYCLE.CATEGORY.DEFAULT,
      eventName,
      undefined,
      details,
      true
    );
  }

  reportInteraction(eventName: string, details: EventDetails) {
    this.reporter(
      INTERACTION.TYPE,
      INTERACTION.CATEGORY.DEFAULT,
      eventName,
      undefined,
      details,
      true
    );
  }

  reportExecution(name: Execution, details?: EventDetails) {
    const id = `${name}${JSON.stringify(details)}`;
    if (this.executionReported.has(id)) return;
    this.executionReported.add(id);
    this.reporter(
      LIFECYCLE.TYPE,
      LIFECYCLE.CATEGORY.EXECUTION,
      name,
      undefined,
      details,
      true // skip console log
    );
  }

  trackApi(
    pluginApi: Pick<PluginApi, 'getPluginName'>,
    object: string,
    method: string
  ) {
    const plugin = pluginApi?.getPluginName() ?? 'unknown';
    this.reportExecution(Execution.PLUGIN_API, {plugin, object, method});
  }

  /**
   * A draft interaction was started. Update the time-between-draft-actions
   * Timing.
   */
  recordDraftInteraction() {
    // If there is no timer defined, then this is the first interaction.
    // Set up the timer so that it's ready to record the intervening time when
    // called again.
    const timer = this.timers.timeBetweenDraftActions;
    if (!timer) {
      // Create a timer with a maximum length.
      this.timers.timeBetweenDraftActions = this.getTimer(
        DRAFT_ACTION_TIMER
      ).withMaximum(DRAFT_ACTION_TIMER_MAX);
      return;
    }

    // Mark the time and reinitialize the timer.
    timer.end().reset();
  }

  error(error: Error, errorSource?: string, details?: EventDetails) {
    const eventDetails = details ?? {};
    const message = `${errorSource ? errorSource + ': ' : ''}${error.message}`;

    this.reporter(
      ERROR.TYPE,
      ERROR.CATEGORY.EXCEPTION,
      message,
      {error},
      {...eventDetails, stack: error.stack}
    );
  }

  reportErrorDialog(message: string) {
    this.reporter(
      ERROR.TYPE,
      ERROR.CATEGORY.ERROR_DIALOG,
      'ErrorDialog: ' + message,
      {error: new Error(message)}
    );
  }

  setRepoName(repoName: string) {
    this.reportRepoName = repoName;
  }

  setChangeId(changeId: NumericChangeId) {
    this.reportChangeId = changeId;
  }
}

export const DEFAULT_STARTUP_TIMERS = {...STARTUP_TIMERS};
