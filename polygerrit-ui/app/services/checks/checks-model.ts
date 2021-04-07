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

export interface CheckResult extends CheckResultApi {
  /**
   * Internally we want to uniquely identify a run with an id, for example when
   * efficiently re-rendering lists of runs in the UI.
   */
  internalId: string;
}

export interface CheckRun extends CheckRunApi {
  /**
   * Internally we want to uniquely identify a result with an id, for example
   * when efficiently re-rendering lists of results in the UI.
   */
  internalId: string;
  results?: CheckResult[];
}

// This is a convenience type for working with results, because when working
// with a bunch of results you will typically also want to know about the run
// properties. So you can just combine them with {...run, ...result}.
export type RunResult = CheckRun & CheckResult;

interface ChecksProviderState {
  pluginName: string;
  loading: boolean;
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
  map(state =>
    Object.values(state).some(providerState => providerState.loading)
  ),
  distinctUntilChanged()
);

export const allActions$ = checksProviderState$.pipe(
  map(state =>
    Object.values(state).reduce(
      (allActions: Action[], providerState: ChecksProviderState) => [
        ...allActions,
        ...providerState.actions,
      ],
      []
    )
  )
);

export const allRuns$ = checksProviderState$.pipe(
  map(state =>
    Object.values(state).reduce(
      (allRuns: CheckRun[], providerState: ChecksProviderState) => [
        ...allRuns,
        ...providerState.runs,
      ],
      []
    )
  )
);

/** Array of check names that have at least 2 entries in allRuns$. */
export const checksWithMultipleAttempts$ = allRuns$.pipe(
  map(runs => {
    const attemptsPerCheck = new Map<string, number>();
    for (const run of runs) {
      const check = run.checkName;
      const attempts = attemptsPerCheck.get(check) ?? 0;
      attemptsPerCheck.set(check, attempts + 1);
    }
    return [...attemptsPerCheck.keys()].filter(
      check => (attemptsPerCheck.get(check) ?? 0) > 1
    );
  })
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

// TODO(brohlfs): Remove all fake runs by end of January. They are just making
// it easier to develop the UI and always see all the different types/states of
// runs and results.

export const fakeRun0: CheckRun = {
  internalId: 'f0',
  checkName: 'FAKE Error Finder',
  results: [
    {
      internalId: 'f0r0',
      category: Category.ERROR,
      summary: 'I would like to point out this error: 1 is not equal to 2!',
      links: [
        {primary: true, url: 'https://www.google.com', icon: LinkIcon.EXTERNAL},
      ],
      tags: [{name: 'OBSOLETE'}, {name: 'E2E'}],
    },
    {
      internalId: 'f0r1',
      category: Category.ERROR,
      summary: 'Running the mighty test has failed by crashing.',
<<<<<<< HEAD   (c4c6e6 Remove new-change-summary feature flag from gr-change-metada)
=======
      actions: [
        {
          name: 'Ignore',
          tooltip: 'Ignore this result',
          primary: true,
          callback: () => undefined,
        },
        {
          name: 'Flag',
          tooltip: 'Flag this result as not useful',
          primary: true,
          callback: () => undefined,
        },
        {
          name: 'Upload',
          tooltip: 'Upload the result to the super cloud.',
          primary: false,
          callback: () => undefined,
        },
      ],
      tags: [{name: 'INTERRUPTED'}, {name: 'WINDOWS'}],
>>>>>>> CHANGE (6988bd Upgrade package.json dependencies)
      links: [
        {primary: true, url: 'https://www.google.com', icon: LinkIcon.EXTERNAL},
      ],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const fakeRun1: CheckRun = {
  internalId: 'f1',
  checkName: 'FAKE Super Check',
  labelName: 'Verified',
  results: [
    {
      internalId: 'f1r0',
      category: Category.WARNING,
      summary: 'We think that you could improve this.',
      message: `There is a lot to be said. A lot. I say, a lot.\n
                So please keep reading.`,
      tags: [{name: 'INTERRUPTED'}, {name: 'WINDOWS'}],
    },
  ],
  status: RunStatus.RUNNING,
};

export const fakeRun2: CheckRun = {
  internalId: 'f2',
  checkName: 'FAKE Mega Analysis',
  results: [
    {
      internalId: 'f2r0',
      category: Category.INFO,
      summary: 'This is looking a bit too large.',
      message: 'We are still looking into how large exactly. Stay tuned.',
      tags: [{name: 'FLAKY'}, {name: 'MAC-OS'}],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const fakeRun3: CheckRun = {
  internalId: 'f3',
  checkName: 'FAKE Critical Observations',
  status: RunStatus.RUNNABLE,
};

export const fakeRun4: CheckRun = {
  internalId: 'f4',
  checkName: 'FAKE TODO Elimination',
  status: RunStatus.COMPLETED,
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

export function updateStateSetResults(
  pluginName: string,
  runs: CheckRunApi[],
  actions: Action[] = []
) {
  const nextState = {...privateState$.getValue()};
  nextState.providerNameToState = {...nextState.providerNameToState};
  nextState.providerNameToState[pluginName] = {
    ...nextState.providerNameToState[pluginName],
    loading: false,
    runs: runs.map(run => {
      const runId = `${run.checkName}-${run.change}-${run.patchset}-${run.attempt}`;
      return {
        ...run,
        internalId: runId,
        results: (run.results ?? []).map((result, i) => {
          return {
            ...result,
            internalId: `${runId}-${i}`,
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
