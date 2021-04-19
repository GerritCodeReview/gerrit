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
  ChecksApiConfig,
  LinkIcon,
  RunStatus,
} from '../../api/checks';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {PatchSetNumber} from '../../types/common';
import {AttemptDetail, createAttemptMap} from './checks-util';
import {assertIsDefined} from '../../utils/common-util';

export interface CheckResult extends CheckResultApi {
  /**
   * Internally we want to uniquely identify a run with an id, for example when
   * efficiently re-rendering lists of runs in the UI.
   */
  internalResultId: string;
}

export interface CheckRun extends CheckRunApi {
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
  /** Presence of errorMessage implicitly means that the provider is in ERROR state. */
  errorMessage?: string;
  /** Presence of loginCallback implicitly means that the provider is in NOT_LOGGED_IN state. */
  loginCallback?: () => void;
  config?: ChecksApiConfig;
  runs: CheckRun[];
  actions: Action[];
}

interface ChecksState {
  patchsetNumber?: PatchSetNumber;
  providerNameToState: {
    [name: string]: ChecksProviderState;
  };
}

const initialState: ChecksState = {
  providerNameToState: {},
};

const privateState$ = new BehaviorSubject(initialState);

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const checksState$: Observable<ChecksState> = privateState$;

export const checksPatchsetNumber$ = checksState$.pipe(
  map(state => state.patchsetNumber),
  distinctUntilChanged()
);

export const checksProviderState$ = checksState$.pipe(
  map(state => state.providerNameToState),
  distinctUntilChanged()
);

export const aPluginHasRegistered$ = checksProviderState$.pipe(
  map(state => Object.keys(state).length > 0),
  distinctUntilChanged()
);

export const someProvidersAreLoading$ = checksProviderState$.pipe(
  map(state => {
    return Object.values(state).some(providerState => providerState.loading);
  }),
  distinctUntilChanged()
);

export const errorMessage$ = checksProviderState$.pipe(
  map(
    state =>
      Object.values(state).find(
        providerState => providerState.errorMessage !== undefined
      )?.errorMessage
  ),
  distinctUntilChanged()
);

export const loginCallback$ = checksProviderState$.pipe(
  map(
    state =>
      Object.values(state).find(
        providerState => providerState.loginCallback !== undefined
      )?.loginCallback
  ),
  distinctUntilChanged()
);

export const allActions$ = checksProviderState$.pipe(
  map(state => {
    return Object.values(state).reduce(
      (allActions: Action[], providerState: ChecksProviderState) => [
        ...allActions,
        ...providerState.actions,
      ],
      []
    );
  })
);

export const allRuns$ = checksProviderState$.pipe(
  map(state => {
    return Object.values(state).reduce(
      (allRuns: CheckRun[], providerState: ChecksProviderState) => [
        ...allRuns,
        ...providerState.runs,
      ],
      []
    );
  })
);

export const allRunsLatest$ = allRuns$.pipe(
  map(runs => runs.filter(run => run.isLatestAttempt))
);

export const checkToPluginMap$ = checksProviderState$.pipe(
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

export const allResults$ = checksProviderState$.pipe(
  map(state => {
    return Object.values(state)
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
      .filter(r => r !== undefined);
  })
);

// Must only be used by the checks service or whatever is in control of this
// model.
export function updateStateSetProvider(
  pluginName: string,
  config?: ChecksApiConfig
) {
  const nextState = {...privateState$.getValue()};
  nextState.providerNameToState = {...nextState.providerNameToState};
  nextState.providerNameToState[pluginName] = {
    pluginName,
    loading: false,
    config,
    runs: [],
    actions: [],
  };
  privateState$.next(nextState);
}

// TODO(brohlfs): Remove all fake runs by end of April. They are just making
// it easier to develop the UI and always see all the different types/states of
// runs and results.

export const fakeRun0: CheckRun = {
  internalRunId: 'f0',
  checkName: 'FAKE Error Finder',
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
      actions: [
        {
          name: 'Ignore',
          tooltip: 'Ignore this result',
          primary: true,
          callback: () => {
            return undefined;
          },
        },
        {
          name: 'Flag',
          tooltip: 'Flag this result as not useful',
          primary: true,
          callback: () => {
            return undefined;
          },
        },
        {
          name: 'Upload',
          tooltip: 'Upload the result to the super cloud.',
          primary: false,
          callback: () => {
            return undefined;
          },
        },
      ],
      tags: [{name: 'INTERRUPTED'}, {name: 'WINDOWS'}],
      links: [
        {primary: true, url: 'https://google.com', icon: LinkIcon.EXTERNAL},
        {primary: true, url: 'https://google.com', icon: LinkIcon.DOWNLOAD},
        {primary: true, url: 'https://google.com', icon: LinkIcon.REPORT_BUG},
        {primary: true, url: 'https://google.com', icon: LinkIcon.HELP_PAGE},
        {primary: true, url: 'https://google.com', icon: LinkIcon.HISTORY},
      ],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const fakeRun1: CheckRun = {
  internalRunId: 'f1',
  checkName: 'FAKE Super Check',
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
      tags: [{name: 'INTERRUPTED'}, {name: 'WINDOWS'}],
      links: [
        {primary: true, url: 'https://google.com', icon: LinkIcon.EXTERNAL},
        {primary: true, url: 'https://google.com', icon: LinkIcon.DOWNLOAD},
        {
          primary: true,
          url: 'https://google.com',
          icon: LinkIcon.DOWNLOAD_MOBILE,
        },
        {primary: true, url: 'https://google.com', icon: LinkIcon.IMAGE},
      ],
    },
  ],
  status: RunStatus.RUNNING,
};

export const fakeRun2: CheckRun = {
  internalRunId: 'f2',
  checkName: 'FAKE Mega Analysis',
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
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
  internalRunId: 'f3',
  checkName: 'FAKE Critical Observations',
  status: RunStatus.RUNNABLE,
  isSingleAttempt: true,
  isLatestAttempt: true,
  attemptDetails: [],
};

export const fakeRun4_1: CheckRun = {
  internalRunId: 'f4',
  checkName: 'FAKE Elimination',
  status: RunStatus.COMPLETED,
  attempt: 1,
  isSingleAttempt: false,
  isLatestAttempt: false,
  attemptDetails: [],
};

export const fakeRun4_2: CheckRun = {
  internalRunId: 'f4',
  checkName: 'FAKE Elimination',
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
  internalRunId: 'f4',
  checkName: 'FAKE Elimination',
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
  internalRunId: 'f4',
  checkName: 'FAKE Elimination',
  status: RunStatus.RUNNING,
  attempt: 4,
  isSingleAttempt: false,
  isLatestAttempt: true,
  attemptDetails: [],
  results: [
    {
      internalResultId: 'f44r0',
      category: Category.INFO,
      summary: 'Dont be afraid. All TODOs will be eliminated.',
    },
  ],
};

export const fakeActions: Action[] = [
  {
    name: 'Fake Action 1',
    primary: false,
    tooltip: 'Tooltip for Fake Action 1',
    callback: () => {
      console.warn('fake action 1 triggered');
      return undefined;
    },
  },
  {
    name: 'Fake Action 2',
    primary: false,
    tooltip: 'Tooltip for Fake Action 2',
    callback: () => {
      console.warn('fake action 2 triggered');
      return undefined;
    },
  },
  {
    name: 'Fake Action 3',
    primary: false,
    tooltip: 'Tooltip for Fake Action 3',
    callback: () => {
      console.warn('fake action 3 triggered');
      return undefined;
    },
  },
];

export function updateStateSetLoading(pluginName: string) {
  const nextState = {...privateState$.getValue()};
  nextState.providerNameToState = {...nextState.providerNameToState};
  nextState.providerNameToState[pluginName] = {
    ...nextState.providerNameToState[pluginName],
    loading: true,
  };
  privateState$.next(nextState);
}

export function updateStateSetError(pluginName: string, errorMessage: string) {
  const nextState = {...privateState$.getValue()};
  nextState.providerNameToState = {...nextState.providerNameToState};
  nextState.providerNameToState[pluginName] = {
    ...nextState.providerNameToState[pluginName],
    loading: false,
    errorMessage,
    loginCallback: undefined,
    runs: [],
    actions: [],
  };
  privateState$.next(nextState);
}

export function updateStateSetNotLoggedIn(
  pluginName: string,
  loginCallback: () => void
) {
  const nextState = {...privateState$.getValue()};
  nextState.providerNameToState = {...nextState.providerNameToState};
  nextState.providerNameToState[pluginName] = {
    ...nextState.providerNameToState[pluginName],
    loading: false,
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
  actions: Action[] = []
) {
  const attemptMap = createAttemptMap(runs);
  for (const attemptInfo of attemptMap.values()) {
    // Per run only one attempt can be undefined, so the '?? -1' is not really
    // relevant for sorting.
    attemptInfo.attempts.sort((a, b) => (b.attempt ?? -1) - (a.attempt ?? -1));
  }
  const nextState = {...privateState$.getValue()};
  nextState.providerNameToState = {...nextState.providerNameToState};
  nextState.providerNameToState[pluginName] = {
    ...nextState.providerNameToState[pluginName],
    loading: false,
    errorMessage: undefined,
    loginCallback: undefined,
    runs: runs.map(run => {
      const runId = `${run.checkName}-${run.change}-${run.patchset}-${run.attempt}`;
      const attemptInfo = attemptMap.get(run.checkName);
      assertIsDefined(attemptInfo, 'attemptInfo');
      return {
        ...run,
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
  };
  privateState$.next(nextState);
}

export function updateStateSetPatchset(patchsetNumber?: PatchSetNumber) {
  const nextState = {...privateState$.getValue()};
  nextState.patchsetNumber = patchsetNumber;
  privateState$.next(nextState);
}
