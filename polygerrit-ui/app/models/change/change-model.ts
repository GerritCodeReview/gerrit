/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  ChangeInfo,
  EditInfo,
  EDIT,
  PARENT,
  NumericChangeId,
  PatchSetNum,
  PreferencesInfo,
  RevisionPatchSetNum,
  PatchSetNumber,
  CommitId,
} from '../../types/common';
import {ChangeStatus, DefaultBase} from '../../constants/constants';
import {combineLatest, from, Observable, forkJoin, of} from 'rxjs';
import {map, filter, withLatestFrom, switchMap} from 'rxjs/operators';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  computeLatestPatchNumWithEdit,
  findEdit,
  sortRevisions,
} from '../../utils/patch-set-util';
import {isDefined, ParsedChangeInfo} from '../../types/types';
import {fireAlert, fireTitleChange} from '../../utils/event-util';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {select} from '../../utils/observable-util';
import {assertIsDefined} from '../../utils/common-util';
import {Model} from '../model';
import {UserModel} from '../user/user-model';
import {define} from '../dependency';
import {isOwner} from '../../utils/change-util';
import {
  ChangeChildView,
  ChangeViewModel,
  createChangeUrl,
  createDiffUrl,
  createEditUrl,
} from '../views/change';
import {NavigationService} from '../../elements/core/gr-navigation/gr-navigation';
import {getRevertCreatedChangeIds} from '../../utils/message-util';
import {computeTruncatedPath} from '../../utils/path-list-util';
import {PluginLoader} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {ReportingService} from '../../services/gr-reporting/gr-reporting';
import {Timing} from '../../constants/reporting';

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
   * The list of reviewed files, kept in the model because we want changes made
   * in one view to reflect on other views without re-rendering the other views.
   * Undefined means it's still loading and empty set means no files reviewed.
   */
  reviewedFiles?: string[];
  /**
   * Either filled from `change.mergeable`, or from a dedicated REST API call.
   * Is initially `undefined`, such that you can identify whether this
   * information has already been loaded once for this change or not. Will never
   * go back to `undefined` after being set for a change.
   */
  mergeable?: boolean;
}

/**
 * `change.revisions` is a dictionary mapping the revision sha to RevisionInfo,
 * but the info object itself does not contain the sha, which is a problem when
 * working with just the info objects.
 *
 * So we are iterating over the map here and are assigning the sha map key to
 * the property `revision.commit.commit`.
 *
 * As usual we are treating data objects as immutable, so we are doind a lot of
 * cloning here.
 */
export function updateRevisionsWithCommitShas(changeInput?: ParsedChangeInfo) {
  if (!changeInput?.revisions) return changeInput;
  const changeOutput = {...changeInput, revisions: {...changeInput.revisions}};
  for (const sha of Object.keys(changeOutput.revisions)) {
    const revision = changeOutput.revisions[sha];
    if (revision?.commit && !revision.commit.commit) {
      changeOutput.revisions[sha] = {
        ...revision,
        commit: {...revision.commit, commit: sha as CommitId},
      };
    }
  }
  return changeOutput;
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

  private patchNum?: RevisionPatchSetNum;

  private basePatchNum?: BasePatchSetNum;

  private latestPatchNum?: PatchSetNumber;

  public readonly change$ = select(
    this.state$,
    changeState => changeState.change
  );

  public readonly changeLoadingStatus$ = select(
    this.state$,
    changeState => changeState.loadingStatus
  );

  public readonly loading$ = select(
    this.changeLoadingStatus$,
    status =>
      status === LoadingStatus.LOADING || status === LoadingStatus.RELOADING
  );

  public readonly reviewedFiles$ = select(
    this.state$,
    changeState => changeState?.reviewedFiles
  );

  public readonly mergeable$ = select(
    this.state$,
    changeState => changeState.mergeable
  );

  public readonly branch$ = select(this.change$, change => change?.branch);

  public readonly changeNum$ = select(this.change$, change => change?._number);

  public readonly changeId$ = select(this.change$, change => change?.change_id);

  public readonly repo$ = select(this.change$, change => change?.project);

  public readonly topic$ = select(this.change$, change => change?.topic);

  public readonly status$ = select(this.change$, change => change?.status);

  public readonly labels$ = select(this.change$, change => change?.labels);

  public readonly revisions$ = select(this.change$, change =>
    sortRevisions(Object.values(change?.revisions || {}))
  );

  public readonly patchsets$ = select(this.change$, change =>
    computeAllPatchSets(change)
  );

  public readonly latestPatchNum$ = select(this.patchsets$, patchsets =>
    computeLatestPatchNum(patchsets)
  );

  public readonly latestPatchNumWithEdit$ = select(this.patchsets$, patchsets =>
    computeLatestPatchNumWithEdit(patchsets)
  );

  public readonly latestUploader$ = select(
    this.change$,
    change => change?.revisions[change.current_revision]?.uploader
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
        this.latestPatchNumWithEdit$,
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

  /** The user can enter edit mode without an `EDIT` patchset existing yet. */
  public readonly editMode$ = select(
    combineLatest([this.viewModel.edit$, this.patchNum$]),
    ([edit, patchNum]) => !!edit || patchNum === EDIT
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

  private selectRevision(
    revisionNum$: Observable<RevisionPatchSetNum | undefined>
  ) {
    return select(
      combineLatest([this.revisions$, revisionNum$]),
      ([revisions, patchNum]) => {
        if (!revisions || !patchNum) return undefined;
        return Object.values(revisions).find(
          revision => revision._number === patchNum
        );
      }
    );
  }

  public readonly revision$ = this.selectRevision(this.patchNum$);

  public readonly latestRevision$ = this.selectRevision(this.latestPatchNum$);

  public readonly isOwner$: Observable<boolean> = select(
    combineLatest([this.change$, this.userModel.account$]),
    ([change, account]) => isOwner(change, account)
  );

  public readonly messages$ = select(this.change$, change => change?.messages);

  public readonly revertingChangeIds$ = select(this.messages$, messages =>
    getRevertCreatedChangeIds(messages ?? [])
  );

  constructor(
    private readonly navigation: NavigationService,
    private readonly viewModel: ChangeViewModel,
    private readonly restApiService: RestApiService,
    private readonly userModel: UserModel,
    private readonly pluginLoader: PluginLoader,
    private readonly reporting: ReportingService
  ) {
    super(initialState);
    this.subscriptions = [
      this.loadChange(),
      this.loadMergeable(),
      this.loadReviewedFiles(),
      this.setOverviewTitle(),
      this.setDiffTitle(),
      this.setEditTitle(),
      this.reportChangeReload(),
      this.reportSendReply(),
      this.fireShowChange(),
      this.refuseEditForOpenChange(),
      this.refuseEditForClosedChange(),
      this.change$.subscribe(change => (this.change = change)),
      this.patchNum$.subscribe(patchNum => (this.patchNum = patchNum)),
      this.basePatchNum$.subscribe(
        basePatchNum => (this.basePatchNum = basePatchNum)
      ),
      this.latestPatchNum$.subscribe(
        latestPatchNum => (this.latestPatchNum = latestPatchNum)
      ),
    ];
  }

  private reportSendReply() {
    return this.changeLoadingStatus$.subscribe(loadingStatus => {
      // We are ending the timer on each change load, because ending a timer
      // that was not started is a no-op. :-)
      if (loadingStatus === LoadingStatus.LOADED) {
        this.reporting.timeEnd(Timing.SEND_REPLY);
      }
    });
  }

  private reportChangeReload() {
    return this.changeLoadingStatus$.subscribe(loadingStatus => {
      if (
        loadingStatus === LoadingStatus.LOADING ||
        loadingStatus === LoadingStatus.RELOADING
      ) {
        this.reporting.time(Timing.CHANGE_RELOAD);
      }
      if (
        loadingStatus === LoadingStatus.LOADED ||
        loadingStatus === LoadingStatus.NOT_LOADED
      ) {
        this.reporting.timeEnd(Timing.CHANGE_RELOAD);
      }
    });
  }

  private fireShowChange() {
    return combineLatest([
      this.change$,
      this.basePatchNum$,
      this.patchNum$,
      this.mergeable$,
    ])
      .pipe(
        filter(
          ([change, basePatchNum, patchNum, mergeable]) =>
            !!change && !!basePatchNum && !!patchNum && mergeable !== undefined
        )
      )
      .subscribe(([change, basePatchNum, patchNum, mergeable]) => {
        this.pluginLoader.jsApiService.handleShowChange({
          change,
          basePatchNum,
          patchNum,
          // `?? null` is for the TypeScript compiler only. We have a
          // `mergeable !== undefined` filter above, so this cannot happen.
          // It would be nice to change `ShowChangeDetail` to accept `undefined`
          // instaed of `null`, but that would be a Plugin API change ...
          info: {mergeable: mergeable ?? null},
        });
      });
  }

  private refuseEditForOpenChange() {
    return combineLatest([this.revisions$, this.patchNum$, this.status$])
      .pipe(
        filter(
          ([revisions, patchNum, status]) =>
            status === ChangeStatus.NEW &&
            revisions.length > 0 &&
            patchNum === EDIT
        )
      )
      .subscribe(([revisions]) => {
        const editRev = findEdit(revisions);
        if (!editRev) {
          const msg = 'Change edit not found. Please create a change edit.';
          fireAlert(document, msg);
          this.navigateToChangeResetReload();
        }
      });
  }

  private refuseEditForClosedChange() {
    return combineLatest([
      this.revisions$,
      this.viewModel.edit$,
      this.patchNum$,
      this.status$,
    ])
      .pipe(
        filter(
          ([revisions, edit, patchNum, status]) =>
            (status === ChangeStatus.ABANDONED ||
              status === ChangeStatus.MERGED) &&
            revisions.length > 0 &&
            (patchNum === EDIT || edit)
        )
      )
      .subscribe(([revisions]) => {
        const editRev = findEdit(revisions);
        if (!editRev) {
          const msg =
            'Change edits cannot be created if change is merged ' +
            'or abandoned. Redirecting to non edit mode.';
          fireAlert(document, msg);
          this.navigateToChangeResetReload();
        }
      });
  }

  private setOverviewTitle() {
    return combineLatest([this.viewModel.childView$, this.change$])
      .pipe(
        filter(([childView, _]) => childView === ChangeChildView.OVERVIEW),
        map(([_, change]) => change),
        filter(isDefined)
      )
      .subscribe(change => {
        const title = `${change.subject} (${change._number})`;
        fireTitleChange(title);
      });
  }

  private setDiffTitle() {
    return combineLatest([this.viewModel.childView$, this.viewModel.diffPath$])
      .pipe(
        filter(([childView, _]) => childView === ChangeChildView.DIFF),
        map(([_, diffPath]) => diffPath),
        filter(isDefined)
      )
      .subscribe(diffPath => {
        const title = computeTruncatedPath(diffPath);
        fireTitleChange(title);
      });
  }

  private setEditTitle() {
    return combineLatest([this.viewModel.childView$, this.viewModel.editPath$])
      .pipe(
        filter(([childView, _]) => childView === ChangeChildView.EDIT),
        map(([_, editPath]) => editPath),
        filter(isDefined)
      )
      .subscribe(editPath => {
        const title = `Editing ${computeTruncatedPath(editPath)}`;
        fireTitleChange(title);
      });
  }

  private loadReviewedFiles() {
    return combineLatest([
      this.patchNum$,
      this.changeNum$,
      this.userModel.loggedIn$,
    ])
      .pipe(
        switchMap(([patchNum, changeNum, loggedIn]) => {
          if (!changeNum || !patchNum || !loggedIn) {
            this.updateStateReviewedFiles([]);
            return of(undefined);
          }
          return from(this.fetchReviewedFiles(patchNum, changeNum));
        })
      )
      .subscribe();
  }

  private loadMergeable() {
    return this.change$
      .pipe(
        switchMap(change => {
          if (change?._number === undefined) return of(undefined);
          if (change.mergeable !== undefined) return of(change.mergeable);
          if (change.status === ChangeStatus.MERGED) return of(false);
          if (change.status === ChangeStatus.ABANDONED) return of(false);
          return from(
            this.restApiService
              .getMergeable(change._number)
              .then(mergableInfo => mergableInfo?.mergeable ?? false)
          );
        })
      )
      .subscribe(mergeable => this.updateState({mergeable}));
  }

  private loadChange() {
    return this.viewModel.changeNum$
      .pipe(
        switchMap(changeNum => {
          if (changeNum !== undefined) this.updateStateLoading(changeNum);
          const change = from(this.restApiService.getChangeDetail(changeNum));
          const edit = from(this.restApiService.getChangeEdit(changeNum));
          return forkJoin([change, edit]);
        }),
        withLatestFrom(this.viewModel.patchNum$),
        map(([[change, edit], patchNum]) =>
          updateChangeWithEdit(change, edit, patchNum)
        ),
        map(updateRevisionsWithCommitShas)
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
      });
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

  diffUrl(
    diffView: {path: string; lineNum?: number},
    patchNum = this.patchNum,
    basePatchNum = this.basePatchNum
  ) {
    if (!this.change) return;
    if (!this.patchNum) return;
    return createDiffUrl({
      change: this.change,
      patchNum,
      basePatchNum,
      diffView,
    });
  }

  navigateToDiff(
    diffView: {path: string; lineNum?: number},
    patchNum = this.patchNum,
    basePatchNum = this.basePatchNum
  ) {
    const url = this.diffUrl(diffView, patchNum, basePatchNum);
    if (!url) return;
    this.navigation.setUrl(url);
  }

  changeUrl(openReplyDialog = false) {
    if (!this.change) return;
    const isLatest = this.latestPatchNum === this.patchNum;
    return createChangeUrl({
      change: this.change,
      patchNum:
        isLatest && this.basePatchNum === PARENT ? undefined : this.patchNum,
      basePatchNum: this.basePatchNum,
      openReplyDialog,
    });
  }

  // Mainly used for navigating from DIFF to OVERVIEW.
  navigateToChange(openReplyDialog = false) {
    const url = this.changeUrl(openReplyDialog);
    if (!url) return;
    this.navigation.setUrl(url);
  }

  /**
   * Wipes all URL parameters and other view state and goes to the change
   * overview page, forcing a reload.
   *
   * This will also wipe the `patchNum`, so will always go to the latest
   * patchset.
   */
  navigateToChangeResetReload() {
    if (!this.change) return;
    const url = createChangeUrl({change: this.change, forceReload: true});
    if (!url) return;
    this.navigation.setUrl(url);
  }

  editUrl(editView: {path: string; lineNum?: number}) {
    if (!this.change) return;
    return createEditUrl({
      changeNum: this.change._number,
      repo: this.change.project,
      patchNum: this.patchNum,
      editView,
    });
  }

  navigateToEdit(editView: {path: string; lineNum?: number}) {
    const url = this.editUrl(editView);
    if (!url) return;
    this.navigation.setUrl(url);
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
