/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  EditInfo,
  EDIT,
  PARENT,
  NumericChangeId,
  PatchSetNum,
  PreferencesInfo,
  RevisionPatchSetNum,
} from '../../types/common';
import {DefaultBase} from '../../constants/constants';
import {combineLatest, from, fromEvent, Observable, forkJoin, of} from 'rxjs';
import {
  map,
  filter,
  withLatestFrom,
  startWith,
  switchMap,
} from 'rxjs/operators';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
} from '../../utils/patch-set-util';
import {ParsedChangeInfo} from '../../types/types';
import {fireAlert} from '../../utils/event-util';

import {ChangeInfo} from '../../types/common';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {select} from '../../utils/observable-util';
import {assertIsDefined} from '../../utils/common-util';
import {Model} from '../model';
import {UserModel} from '../user/user-model';
import {define} from '../dependency';
import {isOwner} from '../../utils/change-util';
import {ChangeViewModel} from '../views/change';

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
  viewModelPatchNum?: PatchSetNum
): ParsedChangeInfo | undefined {
  if (!change || !edit) return change;
  assertIsDefined(edit.commit.commit, 'edit.commit.commit');
  if (!change.revisions) change.revisions = {};
  change.revisions[edit.commit.commit] = {
    _number: EDIT,
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
  if (viewModelPatchNum === undefined) {
    change.current_revision = edit.commit.commit;
  }
  return change;
}

/**
 * Derives the base patchset number from all the data that can potentially
 * influence it. Mostly just returns `viewModelBasePatchNum` or PARENT, but has
 * some special logic when looking at merge commits.
 *
 * NOTE: At the moment this returns just `viewModelBasePatchNum ?? PARENT`, see
 * TODO below.
 */
function computeBase(
  viewModelBasePatchNum: BasePatchSetNum | undefined,
  patchNum: RevisionPatchSetNum | undefined,
  change: ParsedChangeInfo | undefined,
  preferences: PreferencesInfo
): BasePatchSetNum {
  if (viewModelBasePatchNum && viewModelBasePatchNum !== PARENT) {
    return viewModelBasePatchNum;
  }
  if (!change || !patchNum) return PARENT;

  const preferFirst =
    preferences.default_base_for_merges === DefaultBase.FIRST_PARENT;
  if (!preferFirst) return PARENT;

  // TODO: Re-enable respecting the default_base_for_merges preference.
  // For the Polygerrit UI this was originally implemented in change 214432,
  // but we are not sure whether this was ever 100% working correctly. A
  // major challenge is being able to select PARENT explicitly even if your
  // preference for the default choice is FIRST_PARENT. <gr-file-list-header>
  // just uses `navigation.setUrl()` and the view model does not have any
  // way of forcing the basePatchSetNum to stick to PARENT without being
  // altered back to FIRST_PARENT here.
  // See also corresponding TODO in gr-settings-view.
  return PARENT;
  // const revisionInfo = new RevisionInfo(change);
  // const isMergeCommit = revisionInfo.isMergeCommit(patchNum);
  // return isMergeCommit ? (-1 as PatchSetNumber) : PARENT;
}

// TODO: Figure out how to best enforce immutability of all states. Use Immer?
// Use DeepReadOnly?
const initialState: ChangeState = {
  loadingStatus: LoadingStatus.NOT_LOADED,
};

export const changeModelToken = define<ChangeModel>('change-model');

export class ChangeModel extends Model<ChangeState> {
  private change?: ParsedChangeInfo;

  private patchNum?: PatchSetNum;

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
   * Note that this selector can emit without the change being available!
   */
  public readonly patchNum$: Observable<RevisionPatchSetNum | undefined> =
    select(
      combineLatest([
        this.viewModel.state$,
        this.state$,
        this.latestPatchNum$,
      ]).pipe(
        /**
         * If you depend on both, view model and change state, then you want to
         * filter out inconsistent state, e.g. view model changeNum already
         * updated, change not yet reset to undefined.
         */
        filter(([viewModelState, changeState, _latestPatchN]) => {
          const changeNum = changeState.change?._number;
          const viewModelChangeNum = viewModelState?.changeNum;
          return changeNum === undefined || changeNum === viewModelChangeNum;
        })
      ),
      ([viewModelState, _changeState, latestPatchN]) =>
        viewModelState?.patchNum || latestPatchN
    );

  /**
   * Emits the base patchset number. This is identical to the
   * `viewModel.basePatchNum$`, but has some special logic for merges.
   *
   * Note that this selector can emit without the change being available!
   */
  public readonly basePatchNum$: Observable<BasePatchSetNum> =
    /**
     * If you depend on both, view model and change state, then you want to
     * filter out inconsistent state, e.g. view model changeNum already
     * updated, change not yet reset to undefined.
     */
    select(
      combineLatest([
        this.viewModel.state$,
        this.state$,
        this.userModel.state$,
      ]).pipe(
        filter(([viewModelState, changeState, _]) => {
          const changeNum = changeState.change?._number;
          const viewModelChangeNum = viewModelState?.changeNum;
          return changeNum === undefined || changeNum === viewModelChangeNum;
        }),
        withLatestFrom(
          this.viewModel.basePatchNum$,
          this.patchNum$,
          this.change$,
          this.userModel.preferences$
        )
      ),
      ([_, viewModelBasePatchNum, patchNum, change, preferences]) =>
        computeBase(viewModelBasePatchNum, patchNum, change, preferences)
    );

  public readonly isOwner$: Observable<boolean> = select(
    combineLatest([this.change$, this.userModel.account$]),
    ([change, account]) => isOwner(change, account)
  );

  // For usage in `combineLatest` we need `startWith` such that reload$ has an
  // initial value.
  readonly reload$: Observable<unknown> = fromEvent(document, 'reload').pipe(
    startWith(undefined)
  );

  constructor(
    private readonly viewModel: ChangeViewModel,
    private readonly restApiService: RestApiService,
    private readonly userModel: UserModel
  ) {
    super(initialState);
    this.subscriptions = [
      combineLatest([this.viewModel.changeNum$, this.reload$])
        .pipe(
          map(([changeNum, _]) => changeNum),
          switchMap(changeNum => {
            if (changeNum !== undefined) this.updateStateLoading(changeNum);
            const change = from(this.restApiService.getChangeDetail(changeNum));
            const edit = from(this.restApiService.getChangeEdit(changeNum));
            return forkJoin([change, edit]);
          }),
          withLatestFrom(this.viewModel.patchNum$),
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
      this.patchNum$.subscribe(patchNum => (this.patchNum = patchNum)),
      combineLatest([this.patchNum$, this.changeNum$, this.userModel.loggedIn$])
        .pipe(
          switchMap(([patchNum, changeNum, loggedIn]) => {
            if (!changeNum || !patchNum || !loggedIn) {
              this.updateStateReviewedFiles([]);
              return of(undefined);
            }
            return from(this.fetchReviewedFiles(patchNum, changeNum));
          })
        )
        .subscribe(),
    ];
  }

  // Temporary workaround until path is derived in the model itself.
  updatePath(diffPath?: string) {
    this.updateState({diffPath});
  }

  updateStateReviewedFiles(reviewedFiles: string[]) {
    this.updateState({reviewedFiles});
  }

  updateStateFileReviewed(file: string, reviewed: boolean) {
    const current = this.getState();
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
    this.updateState({reviewedFiles});
  }

  fetchReviewedFiles(patchNum: PatchSetNum, changeNum: NumericChangeId) {
    return this.restApiService
      .getReviewedFiles(changeNum, patchNum)
      .then(files => {
        if (changeNum !== this.change?._number || patchNum !== this.patchNum)
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
        if (changeNum !== this.change?._number || patchNum !== this.patchNum)
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
    this.updateState({
      change: reloading ? current.change : undefined,
      loadingStatus: reloading
        ? LoadingStatus.RELOADING
        : LoadingStatus.LOADING,
    });
  }

  // Private but used in tests.
  updateStateChange(change?: ParsedChangeInfo) {
    this.updateState({
      change,
      loadingStatus:
        change === undefined ? LoadingStatus.NOT_LOADED : LoadingStatus.LOADED,
    });
  }
}
