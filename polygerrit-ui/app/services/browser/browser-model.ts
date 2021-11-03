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

import {BehaviorSubject, Observable, combineLatest} from 'rxjs';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {preferenceDiffViewMode$} from '../user/user-model';
import {DiffViewMode} from '../../api/diff';

// This value is somewhat arbitrary and not based on research or calculations.
const MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX = 850;

interface BrowserState {
  /**
   * We maintain the screen width in the state so that the app can react to
   * changes in the width such as automatically changing to unified diff view
   */
  screenWidth?: number;
}

const initialState: BrowserState = {};

const privateState$ = new BehaviorSubject(initialState);

export function _testOnly_resetState() {
  // We cannot assign a new subject to privateState$, because all the selectors
  // have already subscribed to the original subject. So we have to emit the
  // initial state on the existing subject.
  privateState$.next(initialState);
}

export function _testOnly_setState(state: BrowserState) {
  privateState$.next(state);
}

export function _testOnly_getState() {
  return privateState$.getValue();
}

export const viewState$: Observable<BrowserState> = privateState$;

export function updateStateScreenWidth(screenWidth: number) {
  privateState$.next({...privateState$.getValue(), screenWidth});
}

export const isScreenTooSmall$ = viewState$.pipe(
  map(
    state =>
      !!state.screenWidth &&
      state.screenWidth < MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX
  ),
  distinctUntilChanged()
);

export const diffViewMode$: Observable<DiffViewMode> = combineLatest([
  isScreenTooSmall$,
  preferenceDiffViewMode$,
]).pipe(
  map(([isScreenTooSmall, preferenceDiffViewMode]) => {
    if (isScreenTooSmall) return DiffViewMode.UNIFIED;
    else return preferenceDiffViewMode;
  }, distinctUntilChanged())
);
