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
  CheckResult,
  CheckRun,
  ChecksApiConfig,
} from '../../elements/plugins/gr-checks-api/gr-checks-api-types';
import {map} from 'rxjs/operators';

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
              results.concat(run.results),
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

export function updateStateSetResults(pluginName: string, runs: CheckRun[]) {
  const nextState = {...privateState$.getValue()};
  nextState[pluginName] = {
    ...nextState[pluginName],
    runs,
  };
  privateState$.next(nextState);
}
