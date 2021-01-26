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
} from '../../elements/plugins/gr-checks-api/gr-checks-api-types';
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

const fakeRun0: CheckRun = {
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
  ],
  status: RunStatus.COMPLETED,
};

const fakeRun1: CheckRun = {
  checkName: 'FAKE Super Check',
  labelName: 'Verified',
  results: [
    {
      category: Category.WARNING,
      summary: 'We think that you could improve this.',
      messageSafeHtml: `There is a <b style="font-weight: bold">lot</b>
                        to be said.<br/>A lot.
                        <span style="color: red">I say, a lot.</span><br/>
                        <span style="text-decoration: underline">
                        So please keep reading.</span><br/>
                        <span style="color: #444; background-color: lightblue; border-radius: 4px;">
                        &nbsp;Approved Opinion&nbsp;</span>`,
      tags: [{name: 'INTERRUPTED'}, {name: 'WINDOWS'}],
    },
  ],
  status: RunStatus.RUNNING,
};

const fakeRun2: CheckRun = {
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

const fakeRun3: CheckRun = {
  checkName: 'FAKE Critical Observations',
  status: RunStatus.RUNNABLE,
};

const fakeRun4: CheckRun = {
  checkName: 'FAKE TODO Elimination',
  status: RunStatus.COMPLETED,
};

export function updateStateSetResults(pluginName: string, runs: CheckRun[]) {
  const nextState = {...privateState$.getValue()};
  nextState[pluginName] = {
    ...nextState[pluginName],
    runs: [...runs, fakeRun0, fakeRun1, fakeRun2, fakeRun3, fakeRun4],
  };
  privateState$.next(nextState);
}
