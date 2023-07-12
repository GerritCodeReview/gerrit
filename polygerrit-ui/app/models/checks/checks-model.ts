/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AttemptChoice,
  AttemptDetail,
  createAttemptMap,
  LATEST_ATTEMPT,
  sortAttemptDetails,
  worstCategory,
} from './checks-util';
import {assertIsDefined} from '../../utils/common-util';
import {select} from '../../utils/observable-util';
import {
  BehaviorSubject,
  combineLatest,
  from,
  Observable,
  of,
  Subject,
  timer,
} from 'rxjs';
import {
  catchError,
  filter,
  switchMap,
  take,
  takeUntil,
  takeWhile,
  timeout,
  throttleTime,
  withLatestFrom,
} from 'rxjs/operators';
import {
  Action,
  CheckResult as CheckResultApi,
  CheckRun as CheckRunApi,
  Link,
  ChangeData,
  ChecksApiConfig,
  ChecksProvider,
  FetchResponse,
  ResponseCode,
  Category,
  RunStatus,
} from '../../api/checks';
import {ChangeModel} from '../change/change-model';
import {ChangeInfo, NumericChangeId, PatchSetNumber} from '../../types/common';
import {getCurrentRevision} from '../../utils/change-util';
import {getShaByPatchNum} from '../../utils/patch-set-util';
import {ReportingService} from '../../services/gr-reporting/gr-reporting';
import {Execution, Interaction, Timing} from '../../constants/reporting';
import {fireAlert, fire} from '../../utils/event-util';
import {Model} from '../model';
import {define} from '../dependency';
import {
  ChecksPlugin,
  ChecksUpdate,
  PluginsModel,
} from '../plugins/plugins-model';
import {ChangeViewModel} from '../views/change';

/**
 * The checks model maintains the state of checks for two patchsets: the latest
 * and (if different) also for the one selected in the checks tab. So we need
 * the distinction in a lot of places for checks about whether the code affects
 * the checks data of the LATEST or the SELECTED patchset.
 */
export enum ChecksPatchset {
  LATEST = 'LATEST',
  SELECTED = 'SELECTED',
}

export interface CheckResult extends CheckResultApi {
  /**
   * Internally we want to uniquely identify a run with an id, for example when
   * efficiently re-rendering lists of runs in the UI.
   */
  internalResultId: string;
}

export interface CheckRun extends CheckRunApi {
  /**
   * For convenience we attach the name of the plugin to each run.
   */
  pluginName: string;
  /**
   * Internally we want to uniquely identify a result with an id, for example
   * when efficiently re-rendering lists of results in the UI.
   */
  internalRunId: string;
  /**
   * Is this run attempt the latest attempt for the check, i.e. does it have
   * the highest attempt number among all checks with the same name?
   */
  isLatestAttempt: boolean;
  /**
   * Is this the only attempt for the check, i.e. we don't have data for other
   * attempts?
   */
  isSingleAttempt: boolean;
  /**
   * List of all attempts for the same check, ordered by attempt number.
   */
  attemptDetails: AttemptDetail[];

  /**
   * The category of the worst check result in the run.
   */
  worstCategory?: Category;

  results?: CheckResult[];
}

// This is a convenience type for working with results, because when working
// with a bunch of results you will typically also want to know about some run
// properties.
// Note that you don't want to just spread the entire run object, because you
// definitely don't want the `results` property in the RunResult object.
// Use the `runResult()` function below for creating `RunResult` objects.
export type RunResult = CheckResult &
  Pick<CheckRun, 'pluginName'> &
  Pick<CheckRun, 'attempt'> &
  Pick<CheckRun, 'patchset'> &
  Pick<CheckRun, 'isLatestAttempt'> &
  Pick<CheckRun, 'checkName'> &
  Pick<CheckRun, 'labelName'> &
  Pick<CheckRun, 'status'> &
  Pick<CheckRun, 'statusLink'> &
  Pick<CheckRun, 'statusDescription'> &
  Pick<CheckRun, 'startedTimestamp'> &
  Pick<CheckRun, 'scheduledTimestamp'> &
  Pick<CheckRun, 'finishedTimestamp'> &
  Pick<CheckRun, 'checkLink'> &
  Pick<CheckRun, 'checkDescription'> &
  Pick<CheckRun, 'actions'> &
  Pick<CheckRun, 'attemptDetails'> &
  Pick<CheckRun, 'worstCategory'> & {results?: never};

export function runResult(run: CheckRun, result: CheckResult): RunResult {
  return {
    pluginName: run.pluginName,
    attempt: run.attempt,
    patchset: run.patchset,
    isLatestAttempt: run.isLatestAttempt,
    checkName: run.checkName,
    labelName: run.labelName,
    status: run.status,
    statusLink: run.statusLink,
    statusDescription: run.statusDescription,
    startedTimestamp: run.startedTimestamp,
    scheduledTimestamp: run.scheduledTimestamp,
    finishedTimestamp: run.finishedTimestamp,
    checkLink: run.checkLink,
    checkDescription: run.checkDescription,
    actions: run.actions,
    attemptDetails: run.attemptDetails,
    worstCategory: run.worstCategory,
    ...result,
  };
}

export const checksModelToken = define<ChecksModel>('checks-model');

export interface ChecksProviderState {
  pluginName: string;
  loading: boolean;
  /**
   * Allows to distinguish whether loading:true is the *first* time of loading
   * something for this provider. Or just a subsequent background update.
   * Note that this is initially true even before loading is being set to true,
   * so you may want to check loading && firstTimeLoad.
   */
  firstTimeLoad: boolean;
  /** Presence of errorMessage implicitly means that the provider is in ERROR state. */
  errorMessage?: string;
  /** Presence of loginCallback implicitly means that the provider is in NOT_LOGGED_IN state. */
  loginCallback?: () => void;
  summaryMessage?: string;
  runs: CheckRun[];
  actions: Action[];
  links: Link[];
}

interface ChecksState {
  /** Checks data for the latest patchset. */
  pluginStateLatest: {
    [name: string]: ChecksProviderState;
  };
  /**
   * Checks data for the selected patchset. Note that `checksSelected$` below
   * falls back to the data for the latest patchset, if no patchset is selected.
   */
  pluginStateSelected: {
    [name: string]: ChecksProviderState;
  };
}

/**
 * Android's Checks Plugin has a 15s timeout internally. So we are using
 * something slightly larger, so that we get a proper error from the plugin,
 * if they run into timeout issues.
 */
const FETCH_RESULT_TIMEOUT_MS = 16000;

/**
 * Can be used in `reduce()` to collect all results from all runs from all
 * providers into one array.
 */
export function collectRunResults(
  allResults: RunResult[],
  providerState: ChecksProviderState
): RunResult[] {
  return [
    ...allResults,
    ...providerState.runs.reduce((results: RunResult[], run: CheckRun) => {
      const runResults: RunResult[] =
        run.results?.map(r => runResult(run, r)) ?? [];
      return results.concat(runResults ?? []);
    }, []),
  ];
}

export interface ErrorMessages {
  /* Maps plugin name to error message. */
  [name: string]: string;
}

export class ChecksModel extends Model<ChecksState> {
  private readonly providers: {[name: string]: ChecksProvider} = {};

  private readonly reloadSubjects: {[name: string]: Subject<void>} = {};

  private checkToPluginMap = new Map<string, string>();

  // visible for testing
  changeNum?: NumericChangeId;

  // visible for testing
  latestPatchNum?: PatchSetNumber;

  private readonly documentVisibilityChange$ = new BehaviorSubject(undefined);

  private readonly visibilityChangeListener: () => void;

  public checksSelectedPatchsetNumber$ = select(
    this.changeViewModel.checksPatchset$,
    ps => ps
  );

  public checksSelectedAttemptNumber$ = select(
    this.changeViewModel.attempt$,
    attempt => attempt ?? LATEST_ATTEMPT
  );

  public runFilterRegexp$ = select(
    this.changeViewModel.filter$,
    filter => filter ?? ''
  );

  public checksLatest$ = select(this.state$, state => state.pluginStateLatest);

  public checksSelected$ = select(
    combineLatest([this.state$, this.changeViewModel.checksPatchset$]),
    ([state, ps]) => {
      const checksPs = ps ? ChecksPatchset.SELECTED : ChecksPatchset.LATEST;
      return this.getPluginState(state, checksPs);
    }
  );

  public aPluginHasRegistered$ = select(
    this.checksLatest$,
    state => Object.keys(state).length > 0
  );

  private firstLoadCompleted$ = select(this.checksLatest$, state => {
    const providers = Object.values(state);
    if (providers.length === 0) return false;
    if (providers.some(p => p.loading || p.firstTimeLoad)) return false;
    return true;
  });

  public someProvidersAreLoadingFirstTime$ = select(this.checksLatest$, state =>
    Object.values(state).some(
      provider => provider.loading && provider.firstTimeLoad
    )
  );

  public someProvidersAreLoadingLatest$ = select(this.checksLatest$, state =>
    Object.values(state).some(providerState => providerState.loading)
  );

  public someProvidersAreLoadingSelected$ = select(
    this.checksSelected$,
    state => Object.values(state).some(providerState => providerState.loading)
  );

  public errorMessageLatest$ = select(
    this.checksLatest$,

    state =>
      Object.values(state).find(
        providerState => providerState.errorMessage !== undefined
      )?.errorMessage
  );

  public errorMessagesLatest$ = select(this.checksLatest$, state => {
    const errorMessages: ErrorMessages = {};
    for (const providerState of Object.values(state)) {
      if (providerState.errorMessage === undefined) continue;
      errorMessages[providerState.pluginName] = providerState.errorMessage;
    }
    return errorMessages;
  });

  public loginCallbackLatest$ = select(
    this.checksLatest$,
    state =>
      Object.values(state).find(
        providerState => providerState.loginCallback !== undefined
      )?.loginCallback
  );

  public topLevelActionsLatest$ = select(this.checksLatest$, state =>
    Object.values(state).reduce(
      (allActions: Action[], providerState: ChecksProviderState) => [
        ...allActions,
        ...providerState.actions,
      ],
      []
    )
  );

  public topLevelMessagesLatest$ = select(this.checksLatest$, state => {
    const messages = Object.values(state).map(
      providerState => providerState.summaryMessage
    );
    return messages.filter(m => !!m) as string[];
  });

  public topLevelActionsSelected$ = select(this.checksSelected$, state =>
    Object.values(state).reduce(
      (allActions: Action[], providerState: ChecksProviderState) => [
        ...allActions,
        ...providerState.actions,
      ],
      []
    )
  );

  public topLevelLinksSelected$ = select(this.checksSelected$, state =>
    Object.values(state).reduce(
      (allLinks: Link[], providerState: ChecksProviderState) => [
        ...allLinks,
        ...providerState.links,
      ],
      []
    )
  );

  public allRunsLatestPatchset$ = select(this.checksLatest$, state =>
    Object.values(state).reduce(
      (allRuns: CheckRun[], providerState: ChecksProviderState) => [
        ...allRuns,
        ...providerState.runs,
      ],
      []
    )
  );

  public allRunsSelectedPatchset$ = select(this.checksSelected$, state =>
    Object.values(state).reduce(
      (allRuns: CheckRun[], providerState: ChecksProviderState) => [
        ...allRuns,
        ...providerState.runs,
      ],
      []
    )
  );

  public allRunsLatestPatchsetLatestAttempt$ = select(
    this.allRunsLatestPatchset$,
    runs => runs.filter(run => run.isLatestAttempt)
  );

  public checkToPluginMap$ = select(this.checksLatest$, state => {
    const map = new Map<string, string>();
    for (const [pluginName, providerState] of Object.entries(state)) {
      for (const run of providerState.runs) {
        map.set(run.checkName, pluginName);
      }
    }
    return map;
  });

  public allResultsSelected$ = select(this.checksSelected$, state =>
    Object.values(state)
      .reduce(collectRunResults, [])
      .filter(r => r !== undefined)
  );

  public allResultsLatest$ = select(this.checksLatest$, state =>
    Object.values(state)
      .reduce(collectRunResults, [])
      .filter(r => r !== undefined)
  );

  public allResults$ = select(
    combineLatest([
      this.checksSelectedPatchsetNumber$,
      this.allResultsSelected$,
      this.allResultsLatest$,
    ]),
    ([selectedPs, selected, latest]) =>
      selectedPs ? [...selected, ...latest] : latest
  );

  constructor(
    private readonly changeViewModel: ChangeViewModel,
    private readonly changeModel: ChangeModel,
    private readonly reporting: ReportingService,
    private readonly pluginsModel: PluginsModel
  ) {
    super({
      pluginStateLatest: {},
      pluginStateSelected: {},
    });
    this.reporting.time(Timing.CHECKS_LOAD);
    this.subscriptions = [
      this.changeModel.changeNum$.subscribe(x => (this.changeNum = x)),
      this.changeModel.latestPatchNum$.subscribe(
        x => (this.latestPatchNum = x)
      ),
      this.pluginsModel.checksPlugins$.subscribe(plugins => {
        for (const plugin of plugins) {
          this.register(plugin);
        }
      }),
      this.pluginsModel.checksAnnounce$.subscribe(a =>
        this.reload(a.pluginName)
      ),
      this.pluginsModel.checksUpdate$.subscribe(u => this.updateResult(u)),
      this.checkToPluginMap$.subscribe(map => {
        this.checkToPluginMap = map;
      }),
      this.firstLoadCompleted$
        .pipe(
          filter(completed => !!completed),
          take(1),
          withLatestFrom(this.checksLatest$)
        )
        .subscribe(([_, state]) => this.reportStats(state)),
    ];
    this.visibilityChangeListener = () => {
      this.documentVisibilityChange$.next(undefined);
    };
    document.addEventListener(
      'visibilitychange',
      this.visibilityChangeListener
    );
  }

  private reportStats(state: {[name: string]: ChecksProviderState}) {
    const stats = {
      providerCount: 0,
      providerErrorCount: 0,
      providerLoginCount: 0,
      providerActionCount: 0,
      providerLinkCount: 0,
      errorCount: 0,
      warningCount: 0,
      infoCount: 0,
      successCount: 0,
      runnableCount: 0,
      scheduledCount: 0,
      runningCount: 0,
      completedCount: 0,
    };
    const providers = Object.values(state);
    for (const provider of providers) {
      stats.providerCount++;
      if (provider.errorMessage) stats.providerErrorCount++;
      if (provider.loginCallback) stats.providerLoginCount++;
      if (provider.actions?.length) stats.providerActionCount++;
      if (provider.links?.length) stats.providerLinkCount++;
      for (const run of provider.runs) {
        if (run.status === RunStatus.RUNNABLE) stats.runnableCount++;
        if (run.status === RunStatus.SCHEDULED) stats.scheduledCount++;
        if (run.status === RunStatus.RUNNING) stats.runningCount++;
        if (run.status === RunStatus.COMPLETED) stats.completedCount++;
        for (const result of run.results ?? []) {
          if (result.category === Category.ERROR) stats.errorCount++;
          if (result.category === Category.WARNING) stats.warningCount++;
          if (result.category === Category.INFO) stats.infoCount++;
          if (result.category === Category.SUCCESS) stats.successCount++;
        }
      }
    }
    this.reporting.timeEnd(Timing.CHECKS_LOAD, stats);
    this.reporting.reportInteraction(Interaction.CHECKS_STATS, stats);
  }

  override finalize() {
    document.removeEventListener(
      'visibilitychange',
      this.visibilityChangeListener
    );
    super.finalize();
  }

  // Must only be used by the checks service or whatever is in control of this
  // model.
  updateStateSetProvider(pluginName: string, patchset: ChecksPatchset) {
    const nextState = {...this.getState()};
    const pluginState = this.getPluginState(nextState, patchset);
    pluginState[pluginName] = {
      pluginName,
      loading: false,
      firstTimeLoad: true,
      runs: [],
      actions: [],
      links: [],
    };
    this.setState(nextState);
  }

  getPluginState(
    state: ChecksState,
    patchset: ChecksPatchset = ChecksPatchset.LATEST
  ) {
    if (patchset === ChecksPatchset.LATEST) {
      state.pluginStateLatest = {...state.pluginStateLatest};
      return state.pluginStateLatest;
    } else {
      state.pluginStateSelected = {...state.pluginStateSelected};
      return state.pluginStateSelected;
    }
  }

  updateStateSetLoading(pluginName: string, patchset: ChecksPatchset) {
    const nextState = {...this.getState()};
    const pluginState = this.getPluginState(nextState, patchset);
    pluginState[pluginName] = {
      ...pluginState[pluginName],
      loading: true,
    };
    this.setState(nextState);
  }

  updateStateSetError(
    pluginName: string,
    errorMessage: string,
    patchset: ChecksPatchset
  ) {
    const nextState = {...this.getState()};
    const pluginState = this.getPluginState(nextState, patchset);
    pluginState[pluginName] = {
      ...pluginState[pluginName],
      loading: false,
      firstTimeLoad: false,
      errorMessage,
      loginCallback: undefined,
      runs: [],
      actions: [],
    };
    this.setState(nextState);
  }

  updateStateSetNotLoggedIn(
    pluginName: string,
    loginCallback: () => void,
    patchset: ChecksPatchset
  ) {
    const nextState = {...this.getState()};
    const pluginState = this.getPluginState(nextState, patchset);
    pluginState[pluginName] = {
      ...pluginState[pluginName],
      loading: false,
      firstTimeLoad: false,
      errorMessage: undefined,
      loginCallback,
      runs: [],
      actions: [],
    };
    this.setState(nextState);
  }

  updateStateSetResults(
    pluginName: string,
    runs: CheckRunApi[],
    actions: Action[] = [],
    links: Link[] = [],
    summaryMessage: string | undefined,
    patchset: ChecksPatchset
  ) {
    // Protect against plugins not respecting required fields.
    runs = runs.filter(run => !!run.checkName && !!run.status);
    const attemptMap = createAttemptMap(runs);
    for (const attemptInfo of attemptMap.values()) {
      attemptInfo.attempts.sort(sortAttemptDetails);
    }
    const nextState = {...this.getState()};
    const pluginState = this.getPluginState(nextState, patchset);
    const oldState = pluginState[pluginName];
    pluginState[pluginName] = {
      ...oldState,
      loading: false,
      firstTimeLoad: oldState.loading ? false : oldState.firstTimeLoad,
      errorMessage: undefined,
      loginCallback: undefined,
      runs: runs.map(run => {
        const runId = `${run.checkName}-${run.change}-${run.patchset}-${run.attempt}`;
        const attemptInfo = attemptMap.get(run.checkName);
        assertIsDefined(attemptInfo, 'attemptInfo');
        return {
          ...run,
          attempt: run.attempt ?? 0,
          pluginName,
          internalRunId: runId,
          isLatestAttempt: attemptInfo.latestAttempt === (run.attempt ?? 0),
          isSingleAttempt: attemptInfo.isSingleAttempt,
          attemptDetails: attemptInfo.attempts,
          worstCategory: worstCategory(run),
          results: (run.results ?? []).map((result, i) => {
            return {
              ...result,
              internalResultId: `${runId}-${i}`,
            };
          }),
        };
      }),
      actions: [...actions],
      links: [...links],
      summaryMessage,
    };
    this.setState(nextState);
  }

  updateStateUpdateResult(
    pluginName: string,
    updatedRun: CheckRunApi,
    updatedResult: CheckResultApi,
    patchset: ChecksPatchset
  ) {
    const nextState = {...this.getState()};
    const pluginState = this.getPluginState(nextState, patchset);
    let runUpdated = false;
    const runs: CheckRun[] = pluginState[pluginName].runs.map(run => {
      if (run.change !== updatedRun.change) return run;
      if (run.patchset !== updatedRun.patchset) return run;
      if (run.attempt !== updatedRun.attempt) return run;
      if (run.checkName !== updatedRun.checkName) return run;
      let resultUpdated = false;
      const results: CheckResult[] = (run.results ?? []).map(result => {
        if (
          result.externalId &&
          result.externalId === updatedResult.externalId
        ) {
          runUpdated = true;
          resultUpdated = true;
          return {
            ...updatedResult,
            internalResultId: result.internalResultId,
          };
        }
        return result;
      });
      if (resultUpdated) {
        run = {...run, results};
        run.worstCategory = worstCategory(run);
      }
      return run;
    });
    if (!runUpdated) return;
    pluginState[pluginName] = {
      ...pluginState[pluginName],
      runs,
    };
    this.setState(nextState);
  }

  updateStateSetPatchset(num?: PatchSetNumber) {
    const newPatchset = num === this.latestPatchNum ? undefined : num;
    const oldPatchset = this.changeViewModel.getState()?.checksPatchset;
    // For `checksPatchset` itself we could just let updateState() do the
    // standard old===new comparison. But we have to make sure here that
    // the attempt reset only actually happens when a new patchset is chosen.
    if (newPatchset === oldPatchset) return;
    this.changeViewModel.updateState({
      checksPatchset: newPatchset,
      attempt: LATEST_ATTEMPT,
    });
  }

  updateStateSetAttempt(attemptNumberSelected: AttemptChoice) {
    this.changeViewModel.updateState({attempt: attemptNumberSelected});
  }

  updateStateSetRunFilter(runFilterRegexp: string) {
    this.changeViewModel.updateState({filter: runFilterRegexp});
  }

  reload(pluginName: string) {
    this.reloadSubjects[pluginName].next();
  }

  reloadAll() {
    for (const key of Object.keys(this.providers)) {
      this.reload(key);
    }
  }

  reloadForCheck(checkName?: string) {
    if (!checkName) return;
    const plugin = this.checkToPluginMap.get(checkName);
    if (plugin) this.reload(plugin);
  }

  updateResult(update: ChecksUpdate) {
    const {pluginName, run, result} = update;
    this.updateStateUpdateResult(
      pluginName,
      run,
      result,
      ChecksPatchset.LATEST
    );
    this.updateStateUpdateResult(
      pluginName,
      run,
      result,
      ChecksPatchset.SELECTED
    );
  }

  triggerAction(
    action: Action,
    run: CheckRun | RunResult | undefined,
    context: string
  ) {
    if (!action?.callback) return;
    if (!this.changeNum) return;
    const patchSet = run?.patchset ?? this.latestPatchNum;
    if (!patchSet) return;
    this.reporting.reportInteraction(Interaction.CHECKS_ACTION_TRIGGERED, {
      checkName: run?.checkName,
      actionName: action.name,
      context,
    });
    const promise = action.callback(
      this.changeNum,
      patchSet,
      run?.attempt,
      run?.externalId,
      run?.checkName,
      action.name
    );
    // If plugins return undefined or not a promise, then show no toast.
    if (!promise?.then) return;

    fireAlert(document, `Triggering action '${action.name}' ...`);
    from(promise)
      // If the action takes longer than 5 seconds, then most likely the
      // user is either not interested or the result not relevant anymore.
      .pipe(takeUntil(timer(5000)))
      .subscribe(result => {
        if (result.errorMessage || result.message) {
          fireAlert(document, `${result.message ?? result.errorMessage}`);
        } else {
          fire(document, 'hide-alert', {});
        }
        if (result.shouldReload) {
          this.reloadForCheck(run?.checkName);
        }
      });
  }

  register(checksPlugin: ChecksPlugin) {
    const {pluginName, provider, config} = checksPlugin;
    if (this.providers[pluginName]) {
      console.warn(
        `Plugin '${pluginName}' was trying to register twice as a Checks UI provider. Ignored.`
      );
      return;
    }
    this.providers[pluginName] = provider;
    this.reloadSubjects[pluginName] = new BehaviorSubject<void>(undefined);
    this.updateStateSetProvider(pluginName, ChecksPatchset.LATEST);
    this.updateStateSetProvider(pluginName, ChecksPatchset.SELECTED);
    this.initFetchingOfData(pluginName, config, ChecksPatchset.LATEST);
    this.initFetchingOfData(pluginName, config, ChecksPatchset.SELECTED);
  }

  initFetchingOfData(
    pluginName: string,
    config: ChecksApiConfig,
    patchset: ChecksPatchset
  ) {
    const pollIntervalMs = (config?.fetchPollingIntervalSeconds ?? 60) * 1000;
    // Various events should trigger fetching checks from the provider:
    // 1. Change number and patchset number changes.
    // 2. Specific reload requests.
    // 3. Regular polling starting with an initial fetch right now.
    // 4. A hidden Gerrit tab becoming visible.
    this.subscriptions.push(
      combineLatest([
        this.changeModel.change$,
        patchset === ChecksPatchset.LATEST
          ? this.changeModel.latestPatchNum$
          : this.checksSelectedPatchsetNumber$,
        this.reloadSubjects[pluginName],
        pollIntervalMs === 0 ? from([0]) : timer(0, pollIntervalMs),
        this.documentVisibilityChange$,
      ])
        .pipe(
          takeWhile(_ => !!this.providers[pluginName]),
          filter(_ => document.visibilityState !== 'hidden'),
          throttleTime(500, undefined, {leading: true, trailing: true}),
          switchMap(([change, patchNum]): Observable<FetchResponse> => {
            if (!change || !patchNum) return of(this.empty());
            if (typeof patchNum !== 'number') return of(this.empty());
            assertIsDefined(change.revisions, 'change.revisions');
            const patchsetSha = getShaByPatchNum(change.revisions, patchNum);
            // Sometimes patchNum is updated earlier than change, so change
            // revisions don't have patchNum yet
            if (!patchsetSha) return of(this.empty());
            const data: ChangeData = {
              changeNumber: change?._number,
              patchsetNumber: patchNum,
              patchsetSha,
              repo: change.project,
              commitMessage: getCurrentRevision(change)?.commit?.message,
              changeInfo: change as ChangeInfo,
            };
            return this.fetchResults(pluginName, data, patchset);
          }),
          catchError(e => {
            // This should not happen and is really severe, because it means that
            // the Observable has terminated and we won't recover from that. No
            // further attempts to fetch results for this plugin will be made.
            this.reporting.error(`checks-model crash for ${pluginName}`, e);
            return of(this.createErrorResponse(pluginName, e));
          })
        )
        .subscribe(response => {
          switch (response.responseCode) {
            case ResponseCode.ERROR: {
              const message = response.errorMessage ?? '-';
              this.reporting.reportExecution(Execution.CHECKS_API_ERROR, {
                plugin: pluginName,
                message,
              });
              this.updateStateSetError(pluginName, message, patchset);
              break;
            }
            case ResponseCode.NOT_LOGGED_IN: {
              assertIsDefined(response.loginCallback, 'loginCallback');
              this.reporting.reportExecution(
                Execution.CHECKS_API_NOT_LOGGED_IN,
                {
                  plugin: pluginName,
                }
              );
              this.updateStateSetNotLoggedIn(
                pluginName,
                response.loginCallback,
                patchset
              );
              break;
            }
            case ResponseCode.OK: {
              this.updateStateSetResults(
                pluginName,
                response.runs ?? [],
                response.actions ?? [],
                response.links ?? [],
                response.summaryMessage,
                patchset
              );
              break;
            }
          }
        })
    );
  }

  private empty(): FetchResponse {
    return {
      responseCode: ResponseCode.OK,
      runs: [],
    };
  }

  private createErrorResponse(pluginName: string, error: Error): FetchResponse {
    return {
      responseCode: ResponseCode.ERROR,
      errorMessage:
        `Error message from plugin '${pluginName}':` +
        ` ${JSON.stringify(error)}`,
    };
  }

  private fetchResults(
    pluginName: string,
    data: ChangeData,
    patchset: ChecksPatchset
  ): Observable<FetchResponse> {
    this.updateStateSetLoading(pluginName, patchset);
    const timer = this.reporting.getTimer('ChecksPluginFetch');
    const fetchPromise = this.providers[pluginName]
      .fetch(data)
      .then(response => {
        timer.end({pluginName});
        return response;
      });

    return from(fetchPromise)
      .pipe(timeout(FETCH_RESULT_TIMEOUT_MS))
      .pipe(catchError(e => of(this.createErrorResponse(pluginName, e))));
  }
}
