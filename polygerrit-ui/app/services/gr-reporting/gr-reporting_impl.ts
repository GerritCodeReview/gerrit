/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {FlagsService, KnownExperimentId} from '../flags/flags';
import {EventValue, ReportingService, Timer} from './gr-reporting';
import {hasOwnProperty} from '../../utils/common-util';
import {NumericChangeId} from '../../types/common';
import {Deduping, EventDetails, ReportingOptions} from '../../api/reporting';
import {PluginApi} from '../../api/plugin';
import {
  Execution,
  Interaction,
  LifeCycle,
  Timing,
} from '../../constants/reporting';
import {
  MetricWithAttribution,
  onCLS,
  onINP,
  onLCP,
} from 'web-vitals/attribution';
import {getEventPath, isElementTarget} from '../../utils/dom-util';
import {Finalizable} from '../../types/types';

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

const PLUGIN = {
  TYPE: 'plugin-log',
  CATEGORY: {
    LIFECYCLE: 'lifecycle',
    INTERACTION: 'interaction',
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
  [Timing.STARTUP_FILE_LIST_DISPLAYED]: 0,
  [Timing.APP_STARTED]: 0,
  // WebComponentsReady timer is triggered from gr-router.
  [Timing.WEB_COMPONENTS_READY]: 0,
};

// List of timers that should NOT be reset before a location change.
const LOCATION_CHANGE_OK_TIMERS: (string | Timing)[] = [Timing.SEND_REPLY];

const SLOW_RPC_THRESHOLD = 500;

export function initErrorReporter(reportingService: ReportingService) {
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
    reportingService.error('onError', normalizeError(error), {
      line,
      column,
      url,
      msg,
    });
    return true;
  };
  // TODO(dmfilippov): TS-fix-any unclear what is context
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const catchErrors = function (context?: any) {
    context = context || window;
    const oldOnError = context.onerror;
    context.onerror = (
      event: Event | string,
      source?: string,
      lineno?: number,
      colno?: number,
      error?: Error
    ) => onError(oldOnError, event, source, lineno, colno, error);
    context.addEventListener(
      'unhandledrejection',
      (e: PromiseRejectionEvent) => {
        reportingService.error('unhandledrejection', normalizeError(e.reason));
      }
    );
  };

  catchErrors();

  // for testing
  return {catchErrors};
}

export function initPerformanceReporter(reportingService: ReportingService) {
  // PerformanceObserver interface is a browser API.
  if (window.PerformanceObserver) {
    const supportedEntryTypes = PerformanceObserver.supportedEntryTypes || [];
    // Safari doesn't support longtask yet
    if (supportedEntryTypes.includes('longtask')) {
      const catchLongJsTasks = new PerformanceObserver(list => {
        for (const task of list.getEntries()) {
          // We are interested in longtask longer than 400 ms (default is 50 ms)
          if (task.duration > 400) {
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

export function initVisibilityReporter(reportingService: ReportingService) {
  document.addEventListener('visibilitychange', () => {
    reportingService.onVisibilityChange();
  });
  window.addEventListener('blur', () => {
    reportingService.onFocusChange();
  });
  window.addEventListener('focus', () => {
    reportingService.onFocusChange();
  });
}

export function initClickReporter(reportingService: ReportingService) {
  document.addEventListener('click', (e: MouseEvent) => {
    const anchorEl = e
      .composedPath()
      .find(el => isElementTarget(el) && el.tagName.toUpperCase() === 'A') as
      | HTMLAnchorElement
      | undefined;
    if (!anchorEl) return;
    reportingService.reportInteraction(Interaction.LINK_CLICK, {
      path: getEventPath(e),
      link: anchorEl.href,
      text: anchorEl.innerText,
    });
  });
}

/**
 * Reports generic user interaction every x seconds to detect, if the user is
 * present and is using the application somehow. If you just look at
 * `document.visibilityState`, then the user may have left the browser open
 * without locking the screen. So it helps to know whether some interaction is
 * actually happening.
 */
export class InteractionReporter implements Finalizable {
  /** Accumulates event names until the next round of interaction reporting. */
  private interactionEvents = new Set<string>();

  /** Allows clearing the interval timer. Mostly useful for tests. */
  private intervalId?: number;

  constructor(
    private readonly reportingService: ReportingService,
    private readonly reportingIntervalMs = 10 * 1000
  ) {
    const events = ['mousemove', 'scroll', 'wheel', 'keydown', 'pointerdown'];
    for (const eventName of events) {
      document.addEventListener(eventName, () =>
        this.interactionEvents.add(eventName)
      );
    }

    this.intervalId = window.setInterval(
      () => this.report(),
      this.reportingIntervalMs
    );
  }

  finalize() {
    window.clearInterval(this.intervalId);
  }

  private report() {
    const active = this.interactionEvents.size > 0;
    if (active) {
      this.reportingService.reportInteraction(Interaction.USER_ACTIVE, {
        events: [...this.interactionEvents],
      });
    } else if (document.visibilityState === 'visible') {
      this.reportingService.reportInteraction(Interaction.USER_PASSIVE, {});
    }
    this.interactionEvents.clear();
  }
}

let interactionReporter: InteractionReporter;

export function initInteractionReporter(reportingService: ReportingService) {
  if (!interactionReporter) {
    interactionReporter = new InteractionReporter(reportingService);
  }
}

export function initWebVitals(reportingService: ReportingService) {
  function reportWebVitalMetric(name: Timing, metric: MetricWithAttribution) {
    let score = metric.value;
    // CLS good score is 0.1 and poor score is 0.25. Logging system
    // prefers integers, so we multiple by 100;
    if (name === Timing.CLS) {
      score *= 100;
    }
    reportingService.reporter(
      TIMING.TYPE,
      TIMING.CATEGORY.UI_LATENCY,
      name,
      score,
      {
        navigationType: metric.navigationType,
        rating: metric.rating,
        entries: metric.entries,
        attribution: metric.attribution,
      }
    );
  }

  onCLS(metric => reportWebVitalMetric(Timing.CLS, metric));
  onLCP(metric => reportWebVitalMetric(Timing.LCP, metric));
  onINP(metric => reportWebVitalMetric(Timing.INP, metric));
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
  parallelRequestsEnabled?: boolean;
}

interface SlowRpcCall {
  anonymizedUrl: string;
  elapsed: number;
}

type PendingReportInfo = [EventInfo, boolean | undefined];

export class GrReporting implements ReportingService, Finalizable {
  private readonly _flagsService: FlagsService;

  private readonly _baselines = STARTUP_TIMERS;

  private reportRepoName: string | undefined;

  private reportChangeId: NumericChangeId | undefined;

  private pending: PendingReportInfo[] = [];

  private slowRpcList: SlowRpcCall[] = [];

  /**
   * Keeps track of which ids were already reported for events that should only
   * be reported once per session.
   */
  private reportedIds = new Set<string>();

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

  finalize() {}

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
        this.pending.forEach(([eventInfo, noLog]) => {
          this._reportEvent(eventInfo, noLog);
        });
        this.pending = [];
      }
    }
  }

  private _reportEvent(eventInfo: EventInfo, noLog?: boolean) {
    const {type, value, name, eventDetails} = eventInfo;
    document.dispatchEvent(new CustomEvent(type, {detail: eventInfo}));
    if (noLog) {
      return;
    }
    if (type !== ERROR.TYPE) {
      if (value !== undefined) {
        console.debug(
          `Reporting(${new Date().toISOString()}): ${name}: ${value}`
        );
      } else if (eventDetails !== undefined) {
        console.debug(
          `Reporting(${new Date().toISOString()}): ${name}: ${eventDetails}`
        );
      } else {
        console.debug(`Reporting(${new Date().toISOString()}): ${name}`);
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

    if (
      name === Timing.APP_STARTED &&
      this._flagsService.enabledExperiments.length
    ) {
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

  onFocusChange() {
    this.reporter(
      LIFECYCLE.TYPE,
      LIFECYCLE.CATEGORY.VISIBILITY,
      LifeCycle.FOCUS,
      undefined,
      {
        isVisible: document.visibilityState === 'visible',
        hasFocus: document.hasFocus(),
      },
      false
    );
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
          isVisible: document.visibilityState === 'visible',
          hasFocus: document.hasFocus(),
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
      if (LOCATION_CHANGE_OK_TIMERS.includes(prop)) continue;
      delete this._baselines[prop];
    }
    this.time(Timing.CHANGE_DISPLAYED);
    this.time(Timing.CHANGE_LOAD_FULL);
    this.time(Timing.DASHBOARD_DISPLAYED);
    this.time(Timing.DIFF_VIEW_CONTENT_DISPLAYED);
    this.time(Timing.DIFF_VIEW_DISPLAYED);
    this.time(Timing.FILE_LIST_DISPLAYED);

    this.setRepoName(undefined);
    this.setChangeId(undefined);
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

  changeDisplayed(eventDetails?: EventDetails) {
    eventDetails = {...eventDetails, ...this._pageLoadDetails()};
    if (hasOwnProperty(this._baselines, Timing.STARTUP_CHANGE_DISPLAYED)) {
      this.timeEnd(Timing.STARTUP_CHANGE_DISPLAYED, eventDetails);
    } else {
      this.timeEnd(Timing.CHANGE_DISPLAYED, eventDetails);
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
      parallelRequestsEnabled: this._flagsService.isEnabled(
        KnownExperimentId.PARALLEL_DASHBOARD_REQUESTS
      ),
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
      false
    );
  }

  pluginsFailed(pluginsList?: string[]) {
    if (!pluginsList || pluginsList.length === 0) return;
    this.reporter(
      LIFECYCLE.TYPE,
      LIFECYCLE.CATEGORY.PLUGINS_INSTALLED,
      LifeCycle.PLUGINS_FAILED,
      undefined,
      {pluginsList: pluginsList || []},
      false
    );
  }

  /**
   * Reset named Timing.
   */
  time(name: Timing) {
    this._baselines[name] = now();
    window.performance.mark(`${name}-start`);
    // When time(Timing.DASHBOARD_DISPLAYED) is called gr-dashboard-view
    // we need to clean-up slowRpcList, otherwise it can accumulate to big size
    if (name === Timing.DASHBOARD_DISPLAYED) {
      this.slowRpcList = [];
      this.hiddenDurationTimer.reset();
    }
  }

  /**
   * Finish named timer and report it to server.
   */
  timeEnd(name: Timing, eventDetails?: EventDetails): number {
    if (!hasOwnProperty(this._baselines, name)) {
      return 0;
    }
    const begin = this._baselines[name];
    delete this._baselines[name];
    const end = now();
    const elapsed = end - begin;
    this._reportTiming(name, elapsed, eventDetails);

    // Finalize the interval. Either from a registered start mark or
    // the navigation start time (if baseTime is 0).
    if (begin !== 0) {
      window.performance.measure(name, `${name}-start`);
    } else {
      // Microsoft Edge does not handle the 2nd param correctly
      // (if undefined).
      window.performance.measure(name);
    }
    return elapsed;
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
      end: (eventDetails?: EventDetails) => {
        if (called) {
          throw new Error(`Timer for "${name}" already ended.`);
        }
        called = true;
        const time = now() - start;

        // If a maximum is specified and the time exceeds it, do not report.
        if (max && time > max) {
          return timer;
        }

        this._reportTiming(name, time, eventDetails);
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
      false
    );
  }

  reportPluginLifeCycleLog(eventName: string, details: EventDetails) {
    this.reporter(
      PLUGIN.TYPE,
      PLUGIN.CATEGORY.LIFECYCLE,
      eventName,
      undefined,
      details,
      false
    );
  }

  reportPluginInteractionLog(eventName: string, details: EventDetails) {
    this.reporter(
      PLUGIN.TYPE,
      PLUGIN.CATEGORY.INTERACTION,
      eventName,
      undefined,
      details,
      true
    );
  }

  /**
   * Returns true when the event was deduped and thus should not be reported.
   */
  _dedup(
    eventName: string | Interaction,
    details: EventDetails,
    deduping?: Deduping
  ): boolean {
    if (!deduping) return false;
    let id = '';
    switch (deduping) {
      case Deduping.DETAILS_ONCE_PER_CHANGE:
        id = `${eventName}-${this.reportChangeId}-${JSON.stringify(details)}`;
        break;
      case Deduping.DETAILS_ONCE_PER_SESSION:
        id = `${eventName}-${JSON.stringify(details)}`;
        break;
      case Deduping.EVENT_ONCE_PER_CHANGE:
        id = `${eventName}-${this.reportChangeId}`;
        break;
      case Deduping.EVENT_ONCE_PER_SESSION:
        id = `${eventName}`;
        break;
      default:
        throw new Error(`Invalid 'deduping' option '${deduping}'.`);
    }
    if (this.reportedIds.has(id)) return true;
    this.reportedIds.add(id);
    return false;
  }

  reportInteraction(
    eventName: string | Interaction,
    details: EventDetails,
    options?: ReportingOptions
  ) {
    if (this._dedup(eventName, details, options?.deduping)) return;
    this.reporter(
      INTERACTION.TYPE,
      INTERACTION.CATEGORY.DEFAULT,
      eventName,
      undefined,
      details,
      false
    );
  }

  reportExecution(name: Execution, details?: EventDetails) {
    if (this._dedup(name, details, Deduping.DETAILS_ONCE_PER_SESSION)) return;
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

  error(errorSource: string, error: Error, details?: EventDetails) {
    const message = `${errorSource}: ${error.message}`;
    const eventDetails = {
      errorMessage: message,
      ...details,
      stack: error.stack,
    };

    this.reporter(
      ERROR.TYPE,
      ERROR.CATEGORY.EXCEPTION,
      errorSource,
      {error},
      eventDetails
    );
  }

  reportErrorDialog(message: string) {
    this.reporter(
      ERROR.TYPE,
      ERROR.CATEGORY.ERROR_DIALOG,
      'ErrorDialog',
      {error: new Error(message)},
      {errorMessage: message}
    );
  }

  setRepoName(repoName?: string) {
    this.reportRepoName = repoName;
  }

  setChangeId(changeId?: NumericChangeId) {
    const originalChangeId = this.reportChangeId;
    this.reportChangeId = changeId;

    if (!!changeId && changeId !== originalChangeId) {
      this.reportInteraction(Interaction.CHANGE_ID_CHANGED, {changeId});
    }
  }
}

export const DEFAULT_STARTUP_TIMERS = {...STARTUP_TIMERS};
