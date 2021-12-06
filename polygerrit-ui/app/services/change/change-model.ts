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

import {
  EditInfo,
  EditPatchSetNum,
  NumericChangeId,
  PatchSetNum,
} from '../../types/common';
import {
  combineLatest,
  from,
  fromEvent,
  BehaviorSubject,
  Observable,
  Subscription,
  forkJoin,
} from 'rxjs';
import {
  map,
  filter,
  withLatestFrom,
  distinctUntilChanged,
  startWith,
  switchMap,
} from 'rxjs/operators';
import {RouterModel} from '../router/router-model';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
} from '../../utils/patch-set-util';
import {ParsedChangeInfo} from '../../types/types';

import {ChangeInfo} from '../../types/common';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {Finalizable} from '../registry';
import {select} from '../../utils/observable-util';
import {assertIsDefined} from '../../utils/common-util';

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
}

/**
 * Updates the change object with information from the saved `edit` patchset.
 */
// visible for testing
export function updateChangeWithEdit(
  change?: ParsedChangeInfo,
  edit?: EditInfo,
  routerPatchNum?: PatchSetNum
): ParsedChangeInfo | undefined {
  if (!change || !edit) return change;
  assertIsDefined(edit.commit.commit, 'edit.commit.commit');
  if (!change.revisions) change.revisions = {};
  change.revisions[edit.commit.commit] = {
    _number: EditPatchSetNum,
    basePatchNum: edit.base_patch_set_number,
    commit: edit.commit,
    fetch: edit.fetch,
  };
  if (routerPatchNum === undefined) {
    change.current_revision = edit.commit.commit;
  }
  return change;
}

// TODO: Figure out how to best enforce immutability of all states. Use Immer?
// Use DeepReadOnly?
const initialState: ChangeState = {
  loadingStatus: LoadingStatus.NOT_LOADED,
};

export class ChangeModel implements Finalizable {
  private readonly privateState$ = new BehaviorSubject(initialState);

  public readonly changeState$: Observable<ChangeState> =
    this.privateState$.asObservable();

  public readonly change$ = select(
    this.privateState$,
    changeState => changeState.change
  );

  public readonly changeLoadingStatus$ = select(
    this.privateState$,
    changeState => changeState.loadingStatus
  );

  public readonly diffPath$ = select(
    this.privateState$,
    changeState => changeState?.diffPath
  );

  public readonly changeNum$ = select(this.change$, change => change?._number);

  public readonly repo$ = select(this.change$, change => change?.project);

  public readonly labels$ = select(this.change$, change => change?.labels);

  public readonly latestPatchNum$ = select(this.change$, change =>
    computeLatestPatchNum(computeAllPatchSets(change))
  );

  /**
   * Emits the current patchset number. If the route does not define the current
   * patchset num, then this selector waits for the change to be defined and
   * returns the number of the latest patchset.
   *
   * Note that this selector can emit a patchNum without the change being
   * available!
   */
  public readonly currentPatchNum$: Observable<PatchSetNum | undefined> =
    /**
     * If you depend on both, router and change state, then you want to filter
     * out inconsistent state, e.g. router changeNum already updated, change not
     * yet reset to undefined.
     */
    combineLatest([this.routerModel.routerState$, this.changeState$])
      .pipe(
        filter(([routerState, changeState]) => {
          const changeNum = changeState.change?._number;
          const routerChangeNum = routerState.changeNum;
          return changeNum === undefined || changeNum === routerChangeNum;
        }),
        distinctUntilChanged()
      )
      .pipe(
        withLatestFrom(this.routerModel.routerPatchNum$, this.latestPatchNum$),
        map(([_, routerPatchN, latestPatchN]) => routerPatchN || latestPatchN),
        distinctUntilChanged()
      );

  private subscriptions: Subscription[] = [];

  // For usage in `combineLatest` we need `startWith` such that reload$ has an
  // initial value.
  private readonly reload$: Observable<unknown> = fromEvent(
    document,
    'reload'
  ).pipe(startWith(undefined));

  constructor(
    readonly routerModel: RouterModel,
    readonly restApiService: RestApiService
  ) {
    this.subscriptions = [
      combineLatest([this.routerModel.routerChangeNum$, this.reload$])
        .pipe(
          map(([changeNum, _]) => changeNum),
          switchMap(changeNum => {
            if (changeNum !== undefined) this.updateStateLoading(changeNum);
            const change = from(this.restApiService.getChangeDetail(changeNum));
            const edit = from(this.restApiService.getChangeEdit(changeNum));
            return forkJoin([change, edit]);
          }),
          withLatestFrom(this.routerModel.routerPatchNum$),
          map(([[c, e], p]) => updateChangeWithEdit(c, e, p))
        )
        .subscribe(change => {
          // The change service is currently a singleton, so we have to be
          // careful to avoid situations where the application state is
          // partially set for the old change where the user is coming from,
          // and partially for the new change where the user is navigating to.
          // So setting the change explicitly to undefined when the user
          // moves away from diff and change pages (changeNum === undefined)
          // helps with that.
          this.updateStateChange(change ?? undefined);
        }),
    ];
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }

  // Temporary workaround until path is derived in the model itself.
  updatePath(diffPath?: string) {
    const current = this.getState();
    this.setState({...current, diffPath});
  }

  /**
   * Typically you would just subscribe to change$ yourself to get updates. But
   * sometimes it is nice to also be able to get the current ChangeInfo on
   * demand. So here it is for your convenience.
   */
  getChange() {
    return this.getState().change;
  }

  /**
   * Check whether there is no newer patch than the latest patch that was
   * available when this change was loaded.
   *
   * @return A promise that yields true if the latest patch
   *     has been loaded, and false if a newer patch has been uploaded in the
   *     meantime. The promise is rejected on network error.
   */
  fetchChangeUpdates(change: ChangeInfo | ParsedChangeInfo) {
    const knownLatest = computeLatestPatchNum(computeAllPatchSets(change));
    return this.restApiService.getChangeDetail(change._number).then(detail => {
      if (!detail) {
        const error = new Error('Change detail not found.');
        return Promise.reject(error);
      }
      const actualLatest = computeLatestPatchNum(computeAllPatchSets(detail));
      if (!actualLatest || !knownLatest) {
        const error = new Error('Unable to check for latest patchset.');
        return Promise.reject(error);
      }
      return {
        isLatest: actualLatest <= knownLatest,
        newStatus: change.status !== detail.status ? detail.status : null,
        newMessages:
          (change.messages || []).length < (detail.messages || []).length
            ? detail.messages![detail.messages!.length - 1]
            : undefined,
      };
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
  private updateStateLoading(changeNum: NumericChangeId) {
    const current = this.getState();
    const reloading = current.change?._number === changeNum;
    this.setState({
      ...current,
      change: reloading ? current.change : undefined,
      loadingStatus: reloading
        ? LoadingStatus.RELOADING
        : LoadingStatus.LOADING,
    });
  }

  // Private but used in tests.
  updateStateChange(change?: ParsedChangeInfo) {
    const current = this.getState();
    this.setState({
      ...current,
      change,
      loadingStatus:
        change === undefined ? LoadingStatus.NOT_LOADED : LoadingStatus.LOADED,
    });
  }

  getState(): ChangeState {
    return this.privateState$.getValue();
  }

  // Private but used in tests
  setState(state: ChangeState) {
    this.privateState$.next(state);
  }
}
