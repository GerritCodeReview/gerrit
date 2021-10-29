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

import {NumericChangeId, PatchSetNum} from '../../types/common';
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
import {fireAlert} from '../../utils/event-util';

export enum LoadingStatus {
  NOT_LOADED = 'NOT_LOADED',
  LOADING = 'LOADING',
  RELOADING = 'RELOADING',
  LOADED = 'LOADED',
}

export interface ChangeState {
  /**
   * If `change` is undefined, this must be either NOT_LOADED or LOADING.
   * If `change` is defined, this must be either LOADED or RELOADING.
   */
  loadingStatus: LoadingStatus;
  change?: ParsedChangeInfo;
  /**
   * The name of the file user is viewing in the diff view mode. File path is
   * specified in the url or derived from the commentId.
   * Does not apply to change-view or edit-view.
   */
  diffPath?: string;
  /**
   * The list of reviewed files, kept in the model because we want changes made
   * in one view to reflect on other views without re-rendering the other views.
   * Undefined means it's still loading and empty set means no files reviewed.
   */
  reviewedFiles?: Set<string>;
}

// TODO: Figure out how to best enforce immutability of all states. Use Immer?
// Use DeepReadOnly?
const initialState: ChangeState = {
  loadingStatus: LoadingStatus.NOT_LOADED,
};

const privateState$ = new BehaviorSubject(initialState);

export function _testOnly_resetState() {
  // We cannot assign a new subject to privateState$, because all the selectors
  // have already subscribed to the original subject. So we have to emit the
  // initial state on the existing subject.
  privateState$.next({...initialState});
}

export function _testOnly_setState(state: ChangeState) {
  privateState$.next(state);
}

export function _testOnly_getState() {
  return privateState$.getValue();
}

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const changeState$: Observable<ChangeState> = privateState$;

export function updateStateChange(change?: ParsedChangeInfo) {
  const current = privateState$.getValue();
  privateState$.next({
    ...current,
    change,
    loadingStatus:
      change === undefined ? LoadingStatus.NOT_LOADED : LoadingStatus.LOADED,
  });
}

/**
 * Called when change detail loading is initiated.
 *
 * If the change number matches the current change in the state, then
 * this is a reload. If not, then we not just want to set the state to
 * LOADING instead of RELOADING, but we also want to set the change to
 * undefined right away. Otherwise components could see inconsistent state:
 * a new change number, but an old change.
 */
export function updateStateLoading(changeNum: NumericChangeId) {
  const current = privateState$.getValue();
  const reloading = current.change?._number === changeNum;
  privateState$.next({
    ...current,
    change: reloading ? current.change : undefined,
    loadingStatus: reloading ? LoadingStatus.RELOADING : LoadingStatus.LOADING,
  });
}

export function updateStatePath(diffPath?: string) {
  const current = privateState$.getValue();
  privateState$.next({...current, diffPath});
}

export function updateStateReviewedFiles(reviewedFiles: Set<string>) {
  const current = privateState$.getValue();
  privateState$.next({...current, reviewedFiles});
}

export function updateStateFileReviewed(file: string, reviewed: boolean) {
  const current = privateState$.getValue();
  if (current.reviewedFiles === undefined) {
    // Reviewed files haven't loaded yet.
    // TODO(dhruvsri): disable updating status if reviewed files are not loaded.
    fireAlert(
      document,
      'Updating status failed. Reviewed files not loaded yet.'
    );
    return;
  }
  const reviewedFiles = current.reviewedFiles
    ? new Set(current.reviewedFiles)
    : new Set<string>();

  // File is already reviewed and is being marked reviewed
  if (reviewedFiles.has(file) && reviewed) return;
  // File is not reviewed and is being marked not reviewed
  if (!reviewedFiles.has(file) && !reviewed) return;

  if (reviewed) reviewedFiles.add(file);
  else reviewedFiles.delete(file);
  privateState$.next({...current, reviewedFiles});
}

export const reviewedFiles$ = changeState$.pipe(
  map(changeState => changeState.reviewedFiles && Array.from(changeState.reviewedFiles)),
  distinctUntilChanged()
);

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

export const changeLoadingStatus$ = changeState$.pipe(
  map(changeState => changeState.loadingStatus),
  distinctUntilChanged()
);

export const diffPath$ = changeState$.pipe(
  map(changeState => changeState?.diffPath),
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
