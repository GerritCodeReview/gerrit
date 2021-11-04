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

import {PatchSetNum} from '../../types/common';
import {BehaviorSubject, combineLatest, Observable} from 'rxjs';
import {
  map,
  filter,
  withLatestFrom,
  distinctUntilChanged,
} from 'rxjs/operators';
import {routerPatchNum$, routerState$} from '../router/router-model';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
} from '../../utils/patch-set-util';
import {ParsedChangeInfo} from '../../types/types';

interface ChangeState {
  change?: ParsedChangeInfo;
}

// TODO: Figure out how to best enforce immutability of all states. Use Immer?
// Use DeepReadOnly?
const initialState: ChangeState = {};

const privateState$ = new BehaviorSubject(initialState);

export function _testOnly_resetState() {
  // We cannot assign a new subject to privateState$, because all the selectors
  // have already subscribed to the original subject. So we have to emit the
  // initial state on the existing subject.
  privateState$.next({...initialState});
}

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const changeState$: Observable<ChangeState> = privateState$;

// Must only be used by the change service or whatever is in control of this
// model.
export function updateState(change?: ParsedChangeInfo) {
  const current = privateState$.getValue();
  // We want to make it easy for subscribers to react to change changes, so we
  // are explicitly emitting an additional `undefined` when the change number
  // changes. So if you are subscribed to the latestPatchsetNumber for example,
  // then you can rely on emissions even if the old and the new change have the
  // same latestPatchsetNumber.
  if (change !== undefined && current.change !== undefined) {
    if (change._number !== current.change._number) {
      privateState$.next({...current, change: undefined});
    }
  }
  privateState$.next({...current, change});
}

/**
 * If you depend on both, router and change state, then you want to filter out
 * inconsistent state, e.g. router changeNum already updated, change not yet
 * reset to undefined.
 */
export const changeAndRouterConsistent$ = combineLatest([
  routerState$,
  changeState$,
]).pipe(
  filter(([routerState, changeState]) => {
    const changeNum = changeState.change?._number;
    const routerChangeNum = routerState.changeNum;
    return changeNum === undefined || changeNum === routerChangeNum;
  }),
  distinctUntilChanged()
);

export const change$ = changeState$.pipe(
  map(changeState => changeState.change),
  distinctUntilChanged()
);

export const changeNum$ = change$.pipe(
  map(change => change?._number),
  distinctUntilChanged()
);

export const repo$ = change$.pipe(
  map(change => change?.project),
  distinctUntilChanged()
);

export const labels$ = change$.pipe(
  map(change => change?.labels),
  distinctUntilChanged()
);

export const latestPatchNum$ = change$.pipe(
  map(change => computeLatestPatchNum(computeAllPatchSets(change))),
  distinctUntilChanged()
);

/**
 * Emits the current patchset number. If the route does not define the current
 * patchset num, then this selector waits for the change to be defined and
 * returns the number of the latest patchset.
 *
 * Note that this selector can emit a patchNum without the change being
 * available!
 */
export const currentPatchNum$: Observable<PatchSetNum | undefined> =
  changeAndRouterConsistent$.pipe(
    withLatestFrom(routerPatchNum$, latestPatchNum$),
    map(
      ([_, routerPatchNum, latestPatchNum]) => routerPatchNum || latestPatchNum
    ),
    distinctUntilChanged()
  );
