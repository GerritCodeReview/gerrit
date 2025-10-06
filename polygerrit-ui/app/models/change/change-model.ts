/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  ChangeInfo,
  ChangeViewChangeInfo,
  CommitId,
  EDIT,
  EditInfo,
  FileInfo,
  ListChangesOption,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  PatchSetNumber,
  PreferencesInfo,
  RevisionInfo,
  RevisionPatchSetNum,
} from '../../types/common';
import {ChangeStatus, DefaultBase} from '../../constants/constants';
import {
  BehaviorSubject,
  combineLatest,
  forkJoin,
  from,
  Observable,
  of,
} from 'rxjs';
import {
  catchError,
  filter,
  map,
  switchMap,
  withLatestFrom,
} from 'rxjs/operators';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  computeLatestPatchNumWithEdit,
  findEdit,
  sortRevisions,
} from '../../utils/patch-set-util';
import {
  EditRevisionInfo,
  isDefined,
  LoadingStatus,
  ParsedChangeInfo,
} from '../../types/types';
import {fire, fireAlert, fireTitleChange} from '../../utils/event-util';
import {
  RestApiService,
  SubmittabilityInfo,
} from '../../services/gr-rest-api/gr-rest-api';
import {select} from '../../utils/observable-util';
import {assertIsDefined} from '../../utils/common-util';
import {Model} from '../base/model';
import {UserModel} from '../user/user-model';
import {define} from '../dependency';
import {FlagsService, KnownExperimentId} from '../../services/flags/flags';
import {
  isOwner,
  isUploader,
  listChangesOptionsToHex,
} from '../../utils/change-util';
import {
  ChangeChildView,
  ChangeViewModel,
  createChangeUrl,
} from '../views/change';
import {NavigationService} from '../../elements/core/gr-navigation/gr-navigation';
import {getRevertCreatedChangeIds} from '../../utils/message-util';
import {computeTruncatedPath} from '../../utils/path-list-util';
import {PluginLoader} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {ReportingService} from '../../services/gr-reporting/gr-reporting';
import {Timing} from '../../constants/reporting';
import {GrReviewerUpdatesParser} from '../../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {throttleWrap} from '../../utils/async-util';

const ERR_REVIEW_STATUS = 'Couldn’t change file review status.';

const ReloadToastMessage = {
  NEWER_REVISION: 'A newer patch set has been uploaded',
  RESTORED: 'This change has been restored',
  ABANDONED: 'This change has been abandoned',
  MERGED: 'This change has been merged',
  NEW_MESSAGE: 'There are new messages on this change',
};

export interface ChangeState {
  /**
   * If `change` is undefined, this must be either NOT_LOADED or LOADING.
   * If `change` is defined, this must be either LOADED.
   */
  loadingStatus: LoadingStatus;
  change?: ParsedChangeInfo;
  /**
   * Information about submittablity and evaluation of SRs
   *
   * Corresponding values in `change` are always kept in sync.
   */
  submittabilityInfo?: SubmittabilityInfo;
  submittabilityLoadingStatus: LoadingStatus;
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

export enum RevisionFileUpdateStatus {
  // Indicates request error.
  UNKNOWN = 'UNKNOWN',
  // File is identical to previous patchset
  SAME = 'SAME',
  // File has been changed in comparison to previous patchset.
  MODIFIED = 'MODIFIED',
}

export type RevisionUpdatedFiles = {
  [revisionId: string]: {[filename: string]: RevisionFileUpdateStatus};
};

/**
 * Calculates whether the file is modified in relation to the previous patchset.
 *
 * The comparison is done based on SHA-1 of file contents.
 */
function computeRevisionFileUpdateStatus(
  filename: string,
  info: FileInfo | undefined,
  prevRevId: string | undefined,
  fileInfos: {[revId: string]: {[filename: string]: FileInfo}}
) {
  // The file info is missing means it's not changed vs. patchset base.
  if (!info) {
    if (!prevRevId) {
      return RevisionFileUpdateStatus.SAME;
    } else {
      // Check if modified in previous patchset, but not in current.
      return filename in fileInfos[prevRevId]
        ? RevisionFileUpdateStatus.MODIFIED
        : RevisionFileUpdateStatus.SAME;
    }
  }

  if (!prevRevId || !(filename in fileInfos[prevRevId])) {
    return RevisionFileUpdateStatus.MODIFIED;
  }

  const prevSha = fileInfos[prevRevId][filename].new_sha;
  if (!!info.new_sha && !!prevSha) {
    return info.new_sha === prevSha
      ? RevisionFileUpdateStatus.SAME
      : RevisionFileUpdateStatus.MODIFIED;
  }
  return RevisionFileUpdateStatus.UNKNOWN;
}

/**
 * For every revision and every file calculates if that file is modified when
 * compared to the previous revision.
 *
 * The comparison is done based on SHA-1 of file contents.
 *
 * @param fileInfos for every revision contains the list FileInfo of files which
 *   are modified compared to the revision's parent.
 */
export function computeRevisionUpdatedFiles(
  change: ParsedChangeInfo | undefined,
  fileInfos: {[revId: string]: {[filename: string]: FileInfo}} | undefined
): RevisionUpdatedFiles | undefined {
  if (!change || !fileInfos) {
    // We set change to undefined when user navigates away from change
    // page. So we should reset state to undefined in this case.
    return undefined;
  }
  const patchsetToRevision: {[ps: number]: string} = {};
  const revisionToPatchset: {[revId: string]: number} = {};
  const allFiles = new Set<string>();
  for (const [revId, rev] of Object.entries<RevisionInfo | EditRevisionInfo>(
    change.revisions
  )) {
    revisionToPatchset[revId] = rev._number as number;
    patchsetToRevision[rev._number as number] = revId;
    Object.keys(fileInfos[revId] ?? {}).forEach(x => allFiles.add(x));
  }
  const revisionUpdatedFiles: RevisionUpdatedFiles = {};
  for (const [revId, files] of Object.entries(fileInfos)) {
    revisionUpdatedFiles[revId] = {};
    const ps = revisionToPatchset[revId];
    const prevRevId = ps === 1 ? undefined : patchsetToRevision[ps - 1];
    for (const filename of allFiles) {
      const info = files[filename];
      revisionUpdatedFiles[revId][filename] = computeRevisionFileUpdateStatus(
        filename,
        info,
        prevRevId,
        fileInfos
      );
    }
  }
  return revisionUpdatedFiles;
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
 * Returns new change object with the fields with submittability related fields
 * updated.
 *
 * - if change is undefined return undefined.
 * - if change number is different than the one in submittability info, no
 * updates made
 */
export function fillFromSubmittabilityInfo(
  change?: ParsedChangeInfo,
  submittabilityInfo?: SubmittabilityInfo
): ParsedChangeInfo | undefined {
  if (
    !change ||
    !submittabilityInfo ||
    submittabilityInfo.changeNum !== change._number
  ) {
    return change;
  }
  return {
    ...change,
    submittable: submittabilityInfo.submittable,
    submit_requirements: submittabilityInfo.submitRequirements,
  };
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
  submittabilityLoadingStatus: LoadingStatus.NOT_LOADED,
};

export const changeModelToken = define<ChangeModel>('change-model');
/**
 * Change model maintains information about the current change.
 *
 * The "current" change is defined by ChangeViewModel. This model tracks part of
 * the current view. As such it's a singleton global state. It's NOT meant to
 * keep the state of an arbitrary change.
 */
export class ChangeModel extends Model<ChangeState> {
  private change?: ParsedChangeInfo;

  private submittabilityInfo?: SubmittabilityInfo;

  private patchNum?: RevisionPatchSetNum;

  private basePatchNum?: BasePatchSetNum;

  private latestPatchNum?: PatchSetNumber;

  private readonly reloadSubmittabilityTrigger$ = new BehaviorSubject<void>(
    undefined
  );

  public readonly change$ = select(
    this.state$,
    changeState => changeState.change
  );

  public readonly submittabilityInfo$ = select(
    this.state$,
    changeState => changeState.submittabilityInfo
  );

  public readonly submittabilityLoadingStatus$ = select(
    this.state$,
    changeState => changeState.submittabilityLoadingStatus
  );

  public readonly submittable$ = select(
    this.state$,
    changeState => changeState.submittabilityInfo?.submittable
  );

  public readonly submitRequirements$ = select(
    this.state$,
    changeState => changeState.submittabilityInfo?.submitRequirements
  );

  public readonly changeLoadingStatus$ = select(
    this.state$,
    changeState => changeState.loadingStatus
  );

  public readonly loading$ = select(
    this.changeLoadingStatus$,
    status => status === LoadingStatus.LOADING
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

  public readonly attentionSet$ = select(
    this.change$,
    change => change?.attention_set
  );

  public readonly owner$ = select(this.change$, change => change?.owner);

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

  public readonly latestCommitter$ = select(
    this.change$,
    change => change?.revisions[change.current_revision]?.commit?.committer
  );

  /**
   * For every filename F and every revision R (corresponding to patchset P),
   * stores whether the file is modified in relation to patchset P - 1 (or base
   * if P = 1).
   */
  public readonly revisionUpdatedFiles$: Observable<
    RevisionUpdatedFiles | undefined
  > = select(
    this.change$.pipe(
      switchMap(change => {
        if (!change) {
          return of([change, undefined]);
        }
        return forkJoin([
          Promise.resolve(change),
          this.restApiService.getAllRevisionFiles(change._number),
        ]);
      })
    ),
    ([change, fileInfos]) => computeRevisionUpdatedFiles(change, fileInfos)
  );

  /**
   * Emits the current patchset number. If the route does not define the current
   * patchset num, then this selector waits for the change to be defined and
   * returns the number of the latest patchset.
   *
   * Note that this selector can emit without the change being available!
   */
  public readonly patchNum$: Observable<RevisionPatchSetNum | undefined>;

  /** The user can enter edit mode without an `EDIT` patchset existing yet. */
  public readonly editMode$;

  /**
   * Emits the base patchset number. This is identical to the
   * `viewModel.basePatchNum$`, but has some special logic for merges.
   *
   * Note that this selector can emit without the change being available!
   */
  public readonly basePatchNum$: Observable<BasePatchSetNum>;

  private selectRevision(
    revisionNum$: Observable<RevisionPatchSetNum | BasePatchSetNum | undefined>
  ) {
    return select(
      combineLatest([this.revisions$, revisionNum$]),
      ([revisions, patchNum]) => {
        if (!revisions || !patchNum || patchNum === PARENT) return undefined;
        return Object.values(revisions).find(
          revision => revision._number === patchNum
        );
      }
    );
  }

  public readonly revision$;

  public readonly baseRevision$;

  public readonly latestRevision$;

  public readonly latestRevisionWithEdit$;

  public readonly isOwner$: Observable<boolean>;

  public readonly isUploader$: Observable<boolean>;

  public readonly messages$;

  public readonly revertingChangeIds$;

  public throttledShowUpdateChangeNotification: () => void;

  private isViewCurrent: boolean = false;

  constructor(
    private readonly navigation: NavigationService,
    private readonly viewModel: ChangeViewModel,
    private readonly restApiService: RestApiService,
    private readonly userModel: UserModel,
    private readonly pluginLoader: PluginLoader,
    private readonly reporting: ReportingService,
    private readonly flagsService: FlagsService
  ) {
    super(initialState);
    this.patchNum$ = select(
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
    this.editMode$ = select(
      combineLatest([this.viewModel.edit$, this.patchNum$]),
      ([edit, patchNum]) => !!edit || patchNum === EDIT
    );
    this.basePatchNum$ = select(
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
    this.revision$ = this.selectRevision(this.patchNum$);
    this.baseRevision$ = this.selectRevision(this.basePatchNum$) as Observable<
      RevisionInfo | undefined
    >;
    this.latestRevision$ = this.selectRevision(this.latestPatchNum$);
    this.latestRevisionWithEdit$ = this.selectRevision(
      this.latestPatchNumWithEdit$
    );
    this.change$.subscribe(change => {
      const changeUpdatesPlugins =
        this.pluginLoader.pluginsModel.getChangeUpdatesPlugins();
      for (const plugin of changeUpdatesPlugins) {
        plugin.publisher.unsubscribe();
      }
      if (!change) return;
      for (const plugin of changeUpdatesPlugins) {
        plugin.publisher.subscribe(change.project, change._number, () =>
          this.throttledShowUpdateChangeNotification()
        );
      }
    });
    this.isOwner$ = select(
      combineLatest([this.change$, this.userModel.account$]),
      ([change, account]) => isOwner(change, account)
    );
    this.isUploader$ = select(
      combineLatest([this.change$, this.userModel.account$]),
      ([change, account]) => isUploader(change, account)
    );
    this.messages$ = select(this.change$, change => change?.messages);
    this.revertingChangeIds$ = select(this.messages$, messages =>
      getRevertCreatedChangeIds(messages ?? [])
    );
    this.subscriptions = [
      this.loadChange(),
      this.loadSubmittabilityInfo(),
      this.loadMergeable(),
      this.loadReviewedFiles(),
      this.setOverviewTitle(),
      this.setDiffTitle(),
      this.setEditTitle(),
      this.reportChangeReload(),
      this.fireShowChange(),
      this.refuseEditForOpenChange(),
      this.refuseEditForClosedChange(),
      this.change$.subscribe(change => (this.change = change)),
      this.submittabilityInfo$.subscribe(
        submittabilityInfo => (this.submittabilityInfo = submittabilityInfo)
      ),
      this.patchNum$.subscribe(patchNum => (this.patchNum = patchNum)),
      this.basePatchNum$.subscribe(
        basePatchNum => (this.basePatchNum = basePatchNum)
      ),
      this.latestPatchNum$.subscribe(
        latestPatchNum => (this.latestPatchNum = latestPatchNum)
      ),
      this.viewModel.childView$.subscribe(childView => {
        this.isViewCurrent = childView === ChangeChildView.OVERVIEW;
      }),
    ];
    this.throttledShowUpdateChangeNotification = throttleWrap(
      () => this.showRefreshChangeNotification(),
      60 * 1000
    );
  }

  private reportChangeReload() {
    return this.changeLoadingStatus$.subscribe(loadingStatus => {
      if (loadingStatus === LoadingStatus.LOADING) {
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

  public reloadSubmittability() {
    this.reloadSubmittabilityTrigger$.next();
  }

  private loadSubmittabilityInfo() {
    // Use the same trigger as loadChange, to run SR loading in parallel.
    return combineLatest([
      this.viewModel.changeNum$,
      this.reloadSubmittabilityTrigger$,
    ])
      .pipe(
        map(([changeNum, _]) => changeNum),
        switchMap(changeNum => {
          if (!changeNum) {
            // On change reload changeNum is set to undefined to reset change
            // state. We propagate undefined and reset the state in this case.
            this.updateState({
              submittabilityLoadingStatus: LoadingStatus.NOT_LOADED,
            });
            return of(undefined);
          }
          this.updateState({
            submittabilityLoadingStatus: LoadingStatus.LOADING,
          });
          return from(this.restApiService.getSubmittabilityInfo(changeNum));
        })
      )
      .subscribe(submittabilityInfo => {
        // TODO(b/445644919): Remove once the submit_requirements is never
        // requested as part of the change detail.
        if (
          !this.flagsService.isEnabled(
            KnownExperimentId.ASYNC_SUBMIT_REQUIREMENTS
          )
        ) {
          return;
        }
        const change = fillFromSubmittabilityInfo(
          this.change,
          submittabilityInfo
        );
        this.updateState({
          change,
          submittabilityInfo,
          submittabilityLoadingStatus: submittabilityInfo
            ? LoadingStatus.LOADED
            : LoadingStatus.NOT_LOADED,
        });
      });
  }

  private loadChange() {
    return this.viewModel.changeNum$
      .pipe(
        switchMap(changeNum => {
          this.updateStateLoading(changeNum);
          // if changeNum is undefined restApi calls return undefined.
          const change = this.restApiService.getChangeDetail(changeNum);
          const edit = this.restApiService.getChangeEdit(changeNum);
          return forkJoin([change, edit]);
        }),
        withLatestFrom(this.viewModel.patchNum$),
        map(([[change, edit], patchNum]) =>
          updateChangeWithEdit(change, edit, patchNum)
        ),
        catchError(err => {
          // Reset loading state and re-throw.
          this.updateState({loadingStatus: LoadingStatus.NOT_LOADED});
          throw err;
        })
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

  navigateToDiff(
    diffView: {path: string; lineNum?: number},
    patchNum = this.patchNum,
    basePatchNum = this.basePatchNum
  ) {
    if (!patchNum) return;
    const url = this.viewModel.diffUrl({diffView, patchNum, basePatchNum});
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

  navigateToEdit(editView: {path: string; lineNum?: number}) {
    if (!this.patchNum) return;
    const url = this.viewModel.editUrl({editView, patchNum: this.patchNum});
    if (!url) return;
    this.navigation.setUrl(url);
  }

  // private but used in tests
  async showRefreshChangeNotification() {
    const change = this.change;
    if (!change) return;
    const toastMessage = await this.getChangeUpdateToastMessage(change);
    if (!toastMessage) return;
    // We have to make sure that the update is still relevant for the user.
    // Since starting to fetch the change update the user may have sent a
    // reply, or the change might have been reloaded, or it could be in the
    // process of being reloaded.
    if (change !== this.change || !this.isViewCurrent) {
      return;
    }
    fire(document, 'show-alert', {
      message: toastMessage,
      // Persist this alert.
      dismissOnNavigation: true,
      showDismiss: true,
      action: 'Reload',
      callback: () => this.navigateToChangeResetReload(),
    });
  }

  async getChangeUpdateToastMessage(change: ParsedChangeInfo) {
    const result = await this.fetchChangeUpdates(change);
    let toastMessage = null;
    if (!result.isLatest) {
      toastMessage = ReloadToastMessage.NEWER_REVISION;
    } else if (result.newStatus === ChangeStatus.MERGED) {
      toastMessage = ReloadToastMessage.MERGED;
    } else if (result.newStatus === ChangeStatus.ABANDONED) {
      toastMessage = ReloadToastMessage.ABANDONED;
    } else if (result.newStatus === ChangeStatus.NEW) {
      toastMessage = ReloadToastMessage.RESTORED;
    } else if (result.newMessages) {
      toastMessage = ReloadToastMessage.NEW_MESSAGE;
      if (result.newMessages.author?.name) {
        toastMessage += ` from ${result.newMessages.author.name}`;
      }
    }
    return toastMessage;
  }

  /**
   * Check whether there are new updates on the change.
   *
   * @return The state of the latest change compared with the argument.
   * Callers can use the delta to show certain notifications to users.
   */
  async fetchChangeUpdates(
    change: ChangeInfo | ParsedChangeInfo,
    includeExtraOptions = false
  ) {
    const knownLatest = change.current_revision_number;
    // The extra options need to be passed so that the GrReviewerUpdatesParser.parse
    // can group the messages correctly.
    // getChangeDetail calls the parse method automatically but since we are using
    // getChange to avoid passing all the options, we need to add some options manually.
    // GrReviewerUpdatesParser groups messages so that we properly compare the message length
    const detail = (await this.restApiService.getChange(
      change._number,
      undefined,
      includeExtraOptions
        ? listChangesOptionsToHex(
            ListChangesOption.MESSAGES,
            ListChangesOption.ALL_REVISIONS,
            ListChangesOption.REVIEWER_UPDATES
          )
        : undefined
    )) as ChangeViewChangeInfo | undefined;
    if (!detail) {
      throw new Error('Change request failed.');
    }
    const parsedChange = includeExtraOptions
      ? GrReviewerUpdatesParser.parse(detail)!
      : detail;
    const actualLatest = parsedChange.current_revision_number;
    return {
      isLatest: actualLatest <= knownLatest,
      newStatus:
        change.status !== parsedChange.status ? parsedChange.status : null,
      newMessages:
        (change.messages || []).length < (parsedChange.messages || []).length
          ? parsedChange.messages![parsedChange.messages!.length - 1]
          : undefined,
    };
  }

  /**
   * Called when change detail loading is initiated.
   *
   * We want to set the state to LOADING, but we also want to set the change to
   * undefined right away. Otherwise components could see inconsistent state:
   * a new change number, but an old change.
   */
  private updateStateLoading(changeNum?: NumericChangeId) {
    this.updateState({
      change: undefined,
      loadingStatus: changeNum
        ? LoadingStatus.LOADING
        : LoadingStatus.NOT_LOADED,
    });
  }

  // Private but used in tests.
  /**
   * Update the change information in the state.
   *
   * Since the ChangeModel must maintain consistency with ChangeViewModel
   * The update is only allowed, if the new change has the same number as the
   * current change or if the current change is not set (it was reset to
   * undefined when ChangeViewModel.changeNum updated).
   */
  updateStateChange(change?: ParsedChangeInfo) {
    if (this.change && change?._number !== this.change?._number) {
      return;
    }
    if (!change) {
      this.updateState({
        change: undefined,
        loadingStatus: LoadingStatus.NOT_LOADED,
      });
      return;
    }
    change = updateRevisionsWithCommitShas(change);
    // TODO(b/445644919): Remove once the submit_requirements is never requested
    // as part of the change detail.
    if (
      this.flagsService.isEnabled(KnownExperimentId.ASYNC_SUBMIT_REQUIREMENTS)
    ) {
      change = fillFromSubmittabilityInfo(change, this.submittabilityInfo);
    }
    this.updateState({
      change,
      loadingStatus: LoadingStatus.LOADED,
    });
  }
}
