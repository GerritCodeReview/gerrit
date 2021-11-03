/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {BehaviorSubject, Observable} from 'rxjs';
import {
  Action,
  Category,
  CheckResult as CheckResultApi,
  CheckRun as CheckRunApi,
  Link,
  LinkIcon,
  RunStatus,
  TagColor,
} from '../../api/checks';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {PatchSetNumber} from '../../types/common';
import {AttemptDetail, createAttemptMap} from './checks-util';
import {assertIsDefined} from '../../utils/common-util';
import {deepEqualStringDict, equalArray} from '../../utils/compare-util';

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
  results?: CheckResult[];
}

// This is a convenience type for working with results, because when working
// with a bunch of results you will typically also want to know about the run
// properties. So you can just combine them with {...run, ...result}.
export type RunResult = CheckRun & CheckResult;

interface ChecksProviderState {
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
  runs: CheckRun[];
  actions: Action[];
  links: Link[];
}

interface ChecksState {
  /**
   * This is the patchset number selected by the user. The *latest* patchset
   * can be picked up from the change model.
   */
  patchsetNumberSelected?: PatchSetNumber;
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

const initialState: ChecksState = {
  pluginStateLatest: {},
  pluginStateSelected: {},
};

const privateState$ = new BehaviorSubject(initialState);

export function _testOnly_resetState() {
  // We cannot assign a new subject to privateState$, because all the selectors
  // have already subscribed to the original subject. So we have to emit the
  // initial state on the existing subject.
  privateState$.next({...initialState});
}

export function _testOnly_setState(state: ChecksState) {
  privateState$.next(state);
}

export function _testOnly_getState() {
  return privateState$.getValue();
}

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const checksState$: Observable<ChecksState> = privateState$;

export const checksSelectedPatchsetNumber$ = checksState$.pipe(
  map(state => state.patchsetNumberSelected),
  distinctUntilChanged()
);

export const checksLatest$ = checksState$.pipe(
  map(state => state.pluginStateLatest),
  distinctUntilChanged()
);

export const checksSelected$ = checksState$.pipe(
  map(state =>
    state.patchsetNumberSelected
      ? state.pluginStateSelected
      : state.pluginStateLatest
  ),
  distinctUntilChanged()
);

export const aPluginHasRegistered$ = checksLatest$.pipe(
  map(state => Object.keys(state).length > 0),
  distinctUntilChanged()
);

export const someProvidersAreLoadingFirstTime$ = checksLatest$.pipe(
  map(state =>
    Object.values(state).some(
      provider => provider.loading && provider.firstTimeLoad
    )
  ),
  distinctUntilChanged()
);

export const someProvidersAreLoadingLatest$ = checksLatest$.pipe(
  map(state =>
    Object.values(state).some(providerState => providerState.loading)
  ),
  distinctUntilChanged()
);

export const someProvidersAreLoadingSelected$ = checksSelected$.pipe(
  map(state =>
    Object.values(state).some(providerState => providerState.loading)
  ),
  distinctUntilChanged()
);

export const errorMessageLatest$ = checksLatest$.pipe(
  map(
    state =>
      Object.values(state).find(
        providerState => providerState.errorMessage !== undefined
      )?.errorMessage
  ),
  distinctUntilChanged()
);

export interface ErrorMessages {
  /* Maps plugin name to error message. */
  [name: string]: string;
}

export const errorMessagesLatest$ = checksLatest$.pipe(
  map(state => {
    const errorMessages: ErrorMessages = {};
    for (const providerState of Object.values(state)) {
      if (providerState.errorMessage === undefined) continue;
      errorMessages[providerState.pluginName] = providerState.errorMessage;
    }
    return errorMessages;
  }),
  distinctUntilChanged(deepEqualStringDict)
);

export const loginCallbackLatest$ = checksLatest$.pipe(
  map(
    state =>
      Object.values(state).find(
        providerState => providerState.loginCallback !== undefined
      )?.loginCallback
  ),
  distinctUntilChanged()
);

export const topLevelActionsLatest$ = checksLatest$.pipe(
  map(state =>
    Object.values(state).reduce(
      (allActions: Action[], providerState: ChecksProviderState) => [
        ...allActions,
        ...providerState.actions,
      ],
      []
    )
  ),
  distinctUntilChanged<Action[]>(equalArray)
);

export const topLevelActionsSelected$ = checksSelected$.pipe(
  map(state =>
    Object.values(state).reduce(
      (allActions: Action[], providerState: ChecksProviderState) => [
        ...allActions,
        ...providerState.actions,
      ],
      []
    )
  ),
  distinctUntilChanged<Action[]>(equalArray)
);

export const topLevelLinksSelected$ = checksSelected$.pipe(
  map(state =>
    Object.values(state).reduce(
      (allLinks: Link[], providerState: ChecksProviderState) => [
        ...allLinks,
        ...providerState.links,
      ],
      []
    )
  ),
  distinctUntilChanged<Link[]>(equalArray)
);

export const allRunsLatestPatchset$ = checksLatest$.pipe(
  map(state =>
    Object.values(state).reduce(
      (allRuns: CheckRun[], providerState: ChecksProviderState) => [
        ...allRuns,
        ...providerState.runs,
      ],
      []
    )
  ),
  distinctUntilChanged<CheckRun[]>(equalArray)
);

export const allRunsSelectedPatchset$ = checksSelected$.pipe(
  map(state =>
    Object.values(state).reduce(
      (allRuns: CheckRun[], providerState: ChecksProviderState) => [
        ...allRuns,
        ...providerState.runs,
      ],
      []
    )
  ),
  distinctUntilChanged<CheckRun[]>(equalArray)
);

export const allRunsLatestPatchsetLatestAttempt$ = allRunsLatestPatchset$.pipe(
  map(runs => runs.filter(run => run.isLatestAttempt))
);

export const checkToPluginMap$ = checksLatest$.pipe(
  map(state => {
    const map = new Map<string, string>();
    for (const [pluginName, providerState] of Object.entries(state)) {
      for (const run of providerState.runs) {
        map.set(run.checkName, pluginName);
      }
    }
    return map;
  })
);

export const allResultsSelected$ = checksSelected$.pipe(
  map(state =>
    Object.values(state)
      .reduce(
        (allResults: CheckResult[], providerState: ChecksProviderState) => [
          ...allResults,
          ...providerState.runs.reduce(
            (results: CheckResult[], run: CheckRun) =>
              results.concat(run.results ?? []),
            []
          ),
        ],
        []
      )
      .filter(r => r !== undefined)
  )
);

// Must only be used by the checks service or whatever is in control of this
// model.
export function updateStateSetProvider(
  pluginName: string,
  patchset: ChecksPatchset
) {
  const nextState = {...privateState$.getValue()};
  const pluginState = getPluginState(nextState, patchset);
  pluginState[pluginName] = {
    pluginName,
    loading: false,
    firstTimeLoad: true,
    runs: [],
    actions: [],
    links: [],
  };
  privateState$.next(nextState);
}

// TODO(brohlfs): Remove all fake runs once the Checks UI is fully launched.
//  They are just making it easier to develop the UI and always see all the
//  different types/states of runs and results.

export const fakeRun0: CheckRun = {
  pluginName: 'f0',
  internalRunId: 'f0',
  checkName: 'FAKE Error Finder Finder Finder Finder Finder Finder Finder',
  labelName: 'Presubmit',
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f0r0',
      category: Category.ERROR,
      summary: 'I would like to point out this error: 1 is not equal to 2!',
      links: [
        {primary: true, url: 'https://www.google.com', icon: LinkIcon.EXTERNAL},
      ],
      tags: [{name: 'OBSOLETE'}, {name: 'E2E'}],
    },
    {
      internalResultId: 'f0r1',
      category: Category.ERROR,
      summary: 'Running the mighty test has failed by crashing.',
      message: 'Btw, 1 is also not equal to 3. Did you know?',
      actions: [
        {
          name: 'Ignore',
          tooltip: 'Ignore this result',
          primary: true,
          callback: () => Promise.resolve({message: 'fake "ignore" triggered'}),
        },
        {
          name: 'Flag',
          tooltip: 'Flag this result as totally absolutely really not useful',
          primary: true,
          disabled: true,
          callback: () => Promise.resolve({message: 'flag "flag" triggered'}),
        },
        {
          name: 'Upload',
          tooltip: 'Upload the result to the super cloud.',
          primary: false,
          callback: () => Promise.resolve({message: 'fake "upload" triggered'}),
        },
      ],
      tags: [{name: 'INTERRUPTED', color: TagColor.BROWN}, {name: 'WINDOWS'}],
      links: [
        {primary: false, url: 'https://google.com', icon: LinkIcon.EXTERNAL},
        {primary: true, url: 'https://google.com', icon: LinkIcon.DOWNLOAD},
        {
          primary: true,
          url: 'https://google.com',
          icon: LinkIcon.DOWNLOAD_MOBILE,
        },
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {primary: false, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.REPORT_BUG},
        {primary: true, url: 'https://google.com', icon: LinkIcon.HELP_PAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.HISTORY},
      ],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const fakeRun1: CheckRun = {
  pluginName: 'f1',
  internalRunId: 'f1',
  checkName: 'FAKE Super Check',
  statusLink: 'https://www.google.com/',
  patchset: 1,
  labelName: 'Verified',
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f1r0',
      category: Category.WARNING,
      summary: 'We think that you could improve this.',
      message: `There is a lot to be said. A lot. I say, a lot.\n
                So please keep reading.`,
      tags: [{name: 'INTERRUPTED', color: TagColor.PURPLE}, {name: 'WINDOWS'}],
      codePointers: [
        {
          path: '/COMMIT_MSG',
          range: {
            start_line: 10,
            start_character: 0,
            end_line: 10,
            end_character: 0,
          },
        },
        {
          path: 'polygerrit-ui/app/api/checks.ts',
          range: {
            start_line: 5,
            start_character: 0,
            end_line: 7,
            end_character: 0,
          },
        },
      ],
      links: [
        {primary: true, url: 'https://google.com', icon: LinkIcon.EXTERNAL},
        {primary: true, url: 'https://google.com', icon: LinkIcon.DOWNLOAD},
        {
          primary: true,
          url: 'https://google.com',
          icon: LinkIcon.DOWNLOAD_MOBILE,
        },
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
        {
          primary: false,
          url: 'https://google.com',
          tooltip: 'look at this',
          icon: LinkIcon.IMAGE,
        },
        {
          primary: false,
          url: 'https://google.com',
          tooltip: 'not at this',
          icon: LinkIcon.IMAGE,
        },
      ],
    },
  ],
  status: RunStatus.RUNNING,
};

export const fakeRun2: CheckRun = {
  pluginName: 'f2',
  internalRunId: 'f2',
  checkName: 'FAKE Mega Analysis',
  statusDescription: 'This run is nearly completed, but not quite.',
  statusLink: 'https://www.google.com/',
  checkDescription:
    'From what the title says you can tell that this check analyses.',
  checkLink: 'https://www.google.com/',
  scheduledTimestamp: new Date('2021-04-01T03:14:15'),
  startedTimestamp: new Date('2021-04-01T04:24:25'),
  finishedTimestamp: new Date('2021-04-01T04:44:44'),
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
  actions: [
    {
      name: 'Re-Run',
      tooltip: 'More powerful run than before',
      primary: true,
      callback: () => Promise.resolve({message: 'fake "re-run" triggered'}),
    },
    {
      name: 'Monetize',
      primary: true,
      disabled: true,
      callback: () => Promise.resolve({message: 'fake "monetize" triggered'}),
    },
    {
      name: 'Delete',
      primary: true,
      callback: () => Promise.resolve({message: 'fake "delete" triggered'}),
    },
  ],
  results: [
    {
      internalResultId: 'f2r0',
      category: Category.INFO,
      summary: 'This is looking a bit too large.',
      message: `We are still looking into how large exactly. Stay tuned.
And have a look at https://www.google.com!

Or have a look at change 30000.
Example code:
  const constable = '';
  var variable = '';`,
      tags: [{name: 'FLAKY'}, {name: 'MAC-OS'}],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const fakeRun3: CheckRun = {
  pluginName: 'f3',
  internalRunId: 'f3',
  checkName: 'FAKE Critical Observations',
  status: RunStatus.RUNNABLE,
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
};

export const fakeRun4_1: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  status: RunStatus.RUNNABLE,
  attempt: 1,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
};

export const fakeRun4_2: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  status: RunStatus.COMPLETED,
  attempt: 2,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f42r0',
      category: Category.INFO,
      summary: 'Please eliminate all the TODOs!',
    },
  ],
};

export const fakeRun4_3: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  status: RunStatus.COMPLETED,
  attempt: 3,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f43r0',
      category: Category.ERROR,
      summary: 'Without eliminating all the TODOs your change will break!',
    },
  ],
};

export const fakeRun4_4: CheckRun = {
  pluginName: 'f4',
  internalRunId: 'f4',
  checkName: 'FAKE Elimination Long Long Long Long Long',
  checkDescription: 'Shows you the possible eliminations.',
  checkLink: 'https://www.google.com',
  status: RunStatus.COMPLETED,
  statusDescription: 'Everything was eliminated already.',
  statusLink: 'https://www.google.com',
  attempt: 40,
  scheduledTimestamp: new Date('2021-04-02T03:14:15'),
  startedTimestamp: new Date('2021-04-02T04:24:25'),
  finishedTimestamp: new Date('2021-04-02T04:25:44'),
  isSingleAttempt: false,
  isLatestAttempt: true,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f44r0',
      category: Category.INFO,
      summary: 'Dont be afraid. All TODOs will be eliminated.',
      actions: [
        {
          name: 'Re-Run',
          tooltip: 'More powerful run than before with a long tooltip, really.',
          primary: true,
          callback: () => Promise.resolve({message: 'fake "re-run" triggered'}),
        },
      ],
    },
  ],
  actions: [
    {
      name: 'Re-Run',
      tooltip: 'small',
      primary: true,
      callback: () => Promise.resolve({message: 'fake "re-run" triggered'}),
    },
  ],
};

export function fakeRun4CreateAttempts(from: number, to: number): CheckRun[] {
  const runs: CheckRun[] = [];
  for (let i = from; i < to; i++) {
    runs.push(fakeRun4CreateAttempt(i));
  }
  return runs;
}

export function fakeRun4CreateAttempt(attempt: number): CheckRun {
  return {
    pluginName: 'f4',
    internalRunId: 'f4',
    checkName: 'FAKE Elimination Long Long Long Long Long',
    status: RunStatus.COMPLETED,
    attempt,
    isSingleAttempt: false,
    isLatestAttempt: false,
    attemptDetails: [],
    results:
      attempt % 2 === 0
        ? [
            {
              internalResultId: 'f43r0',
              category: Category.ERROR,
              summary:
                'Without eliminating all the TODOs your change will break!',
            },
          ]
        : [],
  };
}

export const fakeRun4Att = [
  fakeRun4_1,
  fakeRun4_2,
  fakeRun4_3,
  ...fakeRun4CreateAttempts(5, 40),
  fakeRun4_4,
];

export const fakeActions: Action[] = [
  {
    name: 'Fake Action 1',
    primary: true,
    disabled: true,
    tooltip: 'Tooltip for Fake Action 1',
    callback: () => Promise.resolve({message: 'fake action 1 triggered'}),
  },
  {
    name: 'Fake Action 2',
    primary: false,
    disabled: true,
    tooltip: 'Tooltip for Fake Action 2',
    callback: () => Promise.resolve({message: 'fake action 2 triggered'}),
  },
  {
    name: 'Fake Action 3',
    summary: true,
    primary: false,
    tooltip: 'Tooltip for Fake Action 3',
    callback: () => Promise.resolve({message: 'fake action 3 triggered'}),
  },
];

export const fakeLinks: Link[] = [
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Bug Report 1',
    icon: LinkIcon.REPORT_BUG,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Bug Report 2',
    icon: LinkIcon.REPORT_BUG,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Link 1',
    icon: LinkIcon.EXTERNAL,
  },
  {
    url: 'https://www.google.com',
    primary: false,
    tooltip: 'Fake Link 2',
    icon: LinkIcon.EXTERNAL,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Code Link',
    icon: LinkIcon.CODE,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Image Link',
    icon: LinkIcon.IMAGE,
  },
  {
    url: 'https://www.google.com',
    primary: true,
    tooltip: 'Fake Help Link',
    icon: LinkIcon.HELP_PAGE,
  },
];

export function getPluginState(
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

export function updateStateSetLoading(
  pluginName: string,
  patchset: ChecksPatchset
) {
  const nextState = {...privateState$.getValue()};
  const pluginState = getPluginState(nextState, patchset);
  pluginState[pluginName] = {
    ...pluginState[pluginName],
    loading: true,
  };
  privateState$.next(nextState);
}

export function updateStateSetError(
  pluginName: string,
  errorMessage: string,
  patchset: ChecksPatchset
) {
  const nextState = {...privateState$.getValue()};
  const pluginState = getPluginState(nextState, patchset);
  pluginState[pluginName] = {
    ...pluginState[pluginName],
    loading: false,
    firstTimeLoad: false,
    errorMessage,
    loginCallback: undefined,
    runs: [],
    actions: [],
  };
  privateState$.next(nextState);
}

export function updateStateSetNotLoggedIn(
  pluginName: string,
  loginCallback: () => void,
  patchset: ChecksPatchset
) {
  const nextState = {...privateState$.getValue()};
  const pluginState = getPluginState(nextState, patchset);
  pluginState[pluginName] = {
    ...pluginState[pluginName],
    loading: false,
    firstTimeLoad: false,
    errorMessage: undefined,
    loginCallback,
    runs: [],
    actions: [],
  };
  privateState$.next(nextState);
}

export function updateStateSetResults(
  pluginName: string,
  runs: CheckRunApi[],
  actions: Action[] = [],
  links: Link[] = [],
  patchset: ChecksPatchset
) {
  const attemptMap = createAttemptMap(runs);
  for (const attemptInfo of attemptMap.values()) {
    // Per run only one attempt can be undefined, so the '?? -1' is not really
    // relevant for sorting.
    attemptInfo.attempts.sort((a, b) => (a.attempt ?? -1) - (b.attempt ?? -1));
  }
  const nextState = {...privateState$.getValue()};
  const pluginState = getPluginState(nextState, patchset);
  pluginState[pluginName] = {
    ...pluginState[pluginName],
    loading: false,
    firstTimeLoad: false,
    errorMessage: undefined,
    loginCallback: undefined,
    runs: runs.map(run => {
      const runId = `${run.checkName}-${run.change}-${run.patchset}-${run.attempt}`;
      const attemptInfo = attemptMap.get(run.checkName);
      assertIsDefined(attemptInfo, 'attemptInfo');
      return {
        ...run,
        pluginName,
        internalRunId: runId,
        isLatestAttempt: attemptInfo.latestAttempt === run.attempt,
        isSingleAttempt: attemptInfo.isSingleAttempt,
        attemptDetails: attemptInfo.attempts,
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
  };
  privateState$.next(nextState);
}

export function updateStateUpdateResult(
  pluginName: string,
  updatedRun: CheckRunApi,
  updatedResult: CheckResultApi,
  patchset: ChecksPatchset
) {
  const nextState = {...privateState$.getValue()};
  const pluginState = getPluginState(nextState, patchset);
  let runUpdated = false;
  const runs: CheckRun[] = pluginState[pluginName].runs.map(run => {
    if (run.change !== updatedRun.change) return run;
    if (run.patchset !== updatedRun.patchset) return run;
    if (run.attempt !== updatedRun.attempt) return run;
    if (run.checkName !== updatedRun.checkName) return run;
    let resultUpdated = false;
    const results: CheckResult[] = (run.results ?? []).map(result => {
      if (result.externalId && result.externalId === updatedResult.externalId) {
        runUpdated = true;
        resultUpdated = true;
        return {
          ...updatedResult,
          internalResultId: result.internalResultId,
        };
      }
      return result;
    });
    return resultUpdated ? {...run, results} : run;
  });
  if (!runUpdated) return;
  pluginState[pluginName] = {
    ...pluginState[pluginName],
    runs,
  };
  privateState$.next(nextState);
}

export function updateStateSetPatchset(patchsetNumber?: PatchSetNumber) {
  const nextState = {...privateState$.getValue()};
  nextState.patchsetNumberSelected = patchsetNumber;
  privateState$.next(nextState);
}
