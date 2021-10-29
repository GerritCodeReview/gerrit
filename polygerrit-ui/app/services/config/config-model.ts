/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {ConfigInfo, ServerInfo} from '../../types/common';
import {BehaviorSubject, Observable} from 'rxjs';
import {map, distinctUntilChanged} from 'rxjs/operators';

interface ConfigState {
  repoConfig?: ConfigInfo;
  serverConfig?: ServerInfo;
}

// TODO: Figure out how to best enforce immutability of all states. Use Immer?
// Use DeepReadOnly?
const initialState: ConfigState = {};

const privateState$ = new BehaviorSubject(initialState);

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const configState$: Observable<ConfigState> = privateState$;

export function updateRepoConfig(repoConfig?: ConfigInfo) {
  const current = privateState$.getValue();
  privateState$.next({...current, repoConfig});
}

export function updateServerConfig(serverConfig?: ServerInfo) {
  const current = privateState$.getValue();
  privateState$.next({...current, serverConfig});
}

export const repoConfig$ = configState$.pipe(
  map(configState => configState.repoConfig),
  distinctUntilChanged()
);

export const repoCommentLinks$ = repoConfig$.pipe(
  map(repoConfig => repoConfig?.commentlinks ?? {}),
  distinctUntilChanged()
);

export const serverConfig$ = configState$.pipe(
  map(configState => configState.serverConfig),
  distinctUntilChanged()
);
