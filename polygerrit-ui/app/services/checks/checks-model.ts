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
  Category,
  CheckResult,
  CheckRun,
  ChecksApiConfig,
  LinkIcon,
  RunStatus,
} from '../../api/checks';
import {map} from 'rxjs/operators';

// This is a convenience type for working with results, because when working
// with a bunch of results you will typically also want to know about the run
// properties. So you can just combine them with {...run, ...result}.
export type RunResult = CheckRun & CheckResult;

interface ChecksProviderState {
  pluginName: string;
  config?: ChecksApiConfig;
  runs: CheckRun[];
}

interface ChecksState {
  [name: string]: ChecksProviderState;
}

const initialState: ChecksState = {};

const privateState$ = new BehaviorSubject(initialState);

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const checksState$: Observable<ChecksState> = privateState$;

export const allRuns$ = checksState$.pipe(
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

export const allResults$ = checksState$.pipe(
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
  nextState[pluginName] = {
    pluginName,
    config,
    runs: [],
  };
  privateState$.next(nextState);
}

// TODO(brohlfs): Remove all fake runs by end of January. They are just making
// it easier to develop the UI and always see all the different types/states of
// runs and results.

export const fakeRun0: CheckRun = {
  checkName: 'FAKE Error Finder',
  results: [
    {
      category: Category.ERROR,
      summary: 'I would like to point out this error: 1 is not equal to 2!',
      links: [
        {primary: true, url: 'https://www.google.com', icon: LinkIcon.EXTERNAL},
      ],
      tags: [{name: 'OBSOLETE'}, {name: 'E2E'}],
    },
    {
      category: Category.ERROR,
      summary: 'Running the mighty test has failed by crashing.',
      links: [
        {primary: true, url: 'https://www.google.com', icon: LinkIcon.EXTERNAL},
      ],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const fakeRun1: CheckRun = {
  checkName: 'FAKE Super Check',
  labelName: 'Verified',
  results: [
    {
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
  checkName: 'FAKE Mega Analysis',
  results: [
    {
      category: Category.INFO,
      summary: 'This is looking a bit too large.',
      message: 'We are still looking into how large exactly. Stay tuned.',
      tags: [{name: 'FLAKY'}, {name: 'MAC-OS'}],
    },
  ],
  status: RunStatus.COMPLETED,
};

export const fakeRun3: CheckRun = {
  checkName: 'FAKE Critical Observations',
  status: RunStatus.RUNNABLE,
};

export const fakeRun4: CheckRun = {
  checkName: 'FAKE TODO Elimination',
  status: RunStatus.COMPLETED,
};

export function updateStateSetResults(pluginName: string, runs: CheckRun[]) {
  const nextState = {...privateState$.getValue()};
  nextState[pluginName] = {
    ...nextState[pluginName],
    runs: [...runs],
  };
  privateState$.next(nextState);
}
