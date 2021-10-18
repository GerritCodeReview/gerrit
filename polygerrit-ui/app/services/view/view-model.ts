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

import {BehaviorSubject, Observable} from 'rxjs';
import {distinctUntilChanged, map} from 'rxjs/operators';

interface ViewState {
  // If the user screen width is too low then we want to set the diffMode to
  // Unified
  isScreenTooSmall: boolean;
}

const initialState: ViewState = {
  isScreenTooSmall: false,
};

// Mutable for testing
let privateState$ = new BehaviorSubject(initialState);

export function _testOnly_resetState() {
  privateState$ = new BehaviorSubject(initialState);
}

export function _testOnly_setState(state: ViewState) {
  privateState$.next(state);
}

export function _testOnly_getState() {
  return privateState$.getValue();
}

export const viewState$: Observable<ViewState> = privateState$;

export function updateState(isScreenTooSmall: boolean) {
  privateState$.next({...privateState$.getValue(), isScreenTooSmall});
}

export const isScreenTooSmall$ = viewState$.pipe(
  map(state => state.isScreenTooSmall),
  distinctUntilChanged()
);
