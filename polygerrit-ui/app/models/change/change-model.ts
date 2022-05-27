/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  EditInfo,
  EditPatchSetNum,
  NumericChangeId,
  PatchSetNum,
  RevisionPatchSetNum,
} from '../../types/common';
import {
  combineLatest,
  from,
  fromEvent,
  Observable,
  Subscription,
  forkJoin,
  of,
} from 'rxjs';
import {
  map,
  filter,
  withLatestFrom,
  distinctUntilChanged,
  startWith,
  switchMap,
} from 'rxjs/operators';
import {RouterModel} from '../../services/router/router-model';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
} from '../../utils/patch-set-util';
import {ParsedChangeInfo} from '../../types/types';
import {fireAlert} from '../../utils/event-util';

import {ChangeInfo} from '../../types/common';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {Finalizable} from '../../services/registry';
import {select} from '../../utils/observable-util';
import {assertIsDefined} from '../../utils/common-util';
import {Model} from '../model';
import {UserModel} from '../user/user-model';
import {define} from '../dependency';

export enum LoadingStatus {
  NOT_LOADED = 'NOT_LOADED',
  LOADING = 'LOADING',
  RELOADING = 'RELOADING',
  LOADED = 'LOADED',
}

const ERR_REVIEW_STATUS = 'Couldn’t change file review status.';

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
  reviewedFiles?: string[];
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
  // If the change was loaded without a specific patchset, then this normally
  // means that the *latest* patchset should be loaded. But if there is an
  // active edit, then automatically switch to that edit as the current
  // patchset.
  // TODO: This goes together with `_patchRange.patchNum' being set to `edit`,
  // which is still done in change-view. `_patchRange.patchNum` should
  // eventually also be model managed, so we can reconcile these two code
  // snippets into one location.
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

export const changeModelToken = define<ChangeModel>('change-model');

export class ChangeModel extends Model<ChangeState> implements Finalizable {
  private change?: ParsedChangeInfo;

  private currentPatchNum?: PatchSetNum;

  public readonly change$ = select(
    this.state$,
    changeState => changeState.change
  );

  public readonly changeLoadingStatus$ = select(
    this.state$,
    changeState => changeState.loadingStatus
  );

  public readonly diffPath$ = select(
    this.state$,
    changeState => changeState?.diffPath
  );

  public readonly reviewedFiles$ = select(
    this.state$,
    changeState => changeState?.reviewedFiles
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
  public readonly currentPatchNum$: Observable<
    RevisionPatchSetNum | undefined
  > =
    /**
     * If you depend on both, router and change state, then you want to filter
     * out inconsistent state, e.g. router changeNum already updated, change not
     * yet reset to undefined.
     */
    combineLatest([this.routerModel.state$, this.state$])
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
    readonly restApiService: RestApiService,
    readonly userModel: UserModel
  ) {
    super(initialState);
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
          map(([[change, edit], patchNum]) =>
            updateChangeWithEdit(change, edit, patchNum)
          )
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
      this.change$.subscribe(change => (this.change = change)),
      this.currentPatchNum$.subscribe(
        currentPatchNum => (this.currentPatchNum = currentPatchNum)
      ),
      combineLatest([
        this.currentPatchNum$,
        this.changeNum$,
        this.userModel.loggedIn$,
      ])
        .pipe(
          switchMap(([currentPatchNum, changeNum, loggedIn]) => {
            if (!changeNum || !currentPatchNum || !loggedIn) {
              this.updateStateReviewedFiles([]);
              return of(undefined);
            }
            return from(this.fetchReviewedFiles(currentPatchNum, changeNum));
          })
        )
        .subscribe(),
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
    const current = this.subject$.getValue();
    this.setState({...current, diffPath});
  }

  updateStateReviewedFiles(reviewedFiles: string[]) {
    const current = this.subject$.getValue();
    this.setState({...current, reviewedFiles});
  }

  updateStateFileReviewed(file: string, reviewed: boolean) {
    const current = this.subject$.getValue();
    if (current.reviewedFiles === undefined) {
      // Reviewed files haven't loaded yet.
      // TODO(dhruvsri): disable updating status if reviewed files are not loaded.
      fireAlert(
        document,
        'Updating status failed. Reviewed files not loaded yet.'
      );
      return;
    }
    const reviewedFiles = [...current.reviewedFiles];

    // File is already reviewed and is being marked reviewed
    if (reviewedFiles.includes(file) && reviewed) return;
    // File is not reviewed and is being marked not reviewed
    if (!reviewedFiles.includes(file) && !reviewed) return;

    if (reviewed) reviewedFiles.push(file);
    else reviewedFiles.splice(reviewedFiles.indexOf(file), 1);
    this.setState({...current, reviewedFiles});
  }

  fetchReviewedFiles(currentPatchNum: PatchSetNum, changeNum: NumericChangeId) {
    return this.restApiService
      .getReviewedFiles(changeNum, currentPatchNum)
      .then(files => {
        if (
          changeNum !== this.change?._number ||
          currentPatchNum !== this.currentPatchNum
        )
          return;
        this.updateStateReviewedFiles(files ?? []);
      });
  }

  setReviewedFilesStatus(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    file: string,
    reviewed: boolean
  ) {
    return this.restApiService
      .saveFileReviewed(changeNum, patchNum, file, reviewed)
      .then(() => {
        if (
          changeNum !== this.change?._number ||
          patchNum !== this.currentPatchNum
        )
          return;
        this.updateStateFileReviewed(file, reviewed);
      })
      .catch(() => {
        fireAlert(document, ERR_REVIEW_STATUS);
      });
  }

  /**
   * Typically you would just subscribe to change$ yourself to get updates. But
   * sometimes it is nice to also be able to get the current ChangeInfo on
   * demand. So here it is for your convenience.
   */
  getChange() {
    return this.subject$.getValue().change;
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
    const current = this.subject$.getValue();
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
    const current = this.subject$.getValue();
    this.setState({
      ...current,
      change,
      loadingStatus:
        change === undefined ? LoadingStatus.NOT_LOADED : LoadingStatus.LOADED,
    });
  }

  // Private but used in tests
  setState(state: ChangeState) {
    this.subject$.next(state);
  }
}
