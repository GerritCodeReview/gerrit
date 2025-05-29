/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {
  AccountInfo,
  Comment,
  CommentInfo,
  CommentThread,
  DraftInfo,
  isDraft,
  isError,
  isNew,
  isSaving,
  NumericChangeId,
  PatchSetNumber,
  RevisionId,
  SavingState,
  UrlEncodedCommentId,
} from '../../types/common';
import {
  addPath,
  convertToCommentInput,
  createNew,
  createNewPatchsetLevel,
  getFirstComment,
  hasSuggestion,
  hasUserSuggestion,
  id,
  isDraftThread,
  isNewThread,
  isUnresolved,
  reportingDetails,
} from '../../utils/comment-util';
import {deepEqual} from '../../utils/deep-util';
import {select} from '../../utils/observable-util';
import {define} from '../dependency';
import {
  BehaviorSubject,
  combineLatest,
  forkJoin,
  from,
  Observable,
  of,
} from 'rxjs';
import {fire, fireAlert} from '../../utils/event-util';
import {CURRENT} from '../../utils/patch-set-util';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {ChangeModel} from '../change/change-model';
import {Execution, Interaction, Timing} from '../../constants/reporting';
import {assert, assertIsDefined} from '../../utils/common-util';
import {debounce, DelayedTask} from '../../utils/async-util';
import {ReportingService} from '../../services/gr-reporting/gr-reporting';
import {Model} from '../base/model';
import {Deduping} from '../../api/reporting';
import {extractMentionedUsers, getUserId} from '../../utils/account-util';
import {SpecialFilePath} from '../../constants/constants';
import {AccountsModel} from '../accounts/accounts-model';
import {
  distinctUntilChanged,
  map,
  shareReplay,
  switchMap,
} from 'rxjs/operators';
import {isDefined} from '../../types/types';
import {ChangeViewModel} from '../views/change';
import {NavigationService} from '../../elements/core/gr-navigation/gr-navigation';
import {readJSONResponsePayload} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

export interface CommentState {
  /** undefined means 'still loading' */
  comments?: {[path: string]: CommentInfo[]};
  // All drafts are DraftInfo objects and have `state` state set.
  /** undefined means 'still loading' */
  drafts?: {[path: string]: DraftInfo[]};
  // Ported comments only affect `CommentThread` properties, not individual
  // comments.
  /**
   * Comments ported from earlier patchsets.
   *
   * This only considers current patchset (right side), not the base patchset
   * (left-side).
   *
   * undefined means 'still loading'
   */
  portedComments?: {[path: string]: CommentInfo[]};
  /**
   * Drafts ported from earlier patchsets.
   *
   * undefined means 'still loading'
   */
  portedDrafts?: {[path: string]: DraftInfo[]};
  /**
   * If a draft is discarded by the user, then we temporarily keep it in this
   * array in case the user decides to Undo the discard operation and bring the
   * draft back. Once restored, the draft is removed from this array.
   */
  discardedDrafts: DraftInfo[];
}

const initialState: CommentState = {
  comments: undefined,
  drafts: undefined,
  portedComments: undefined,
  portedDrafts: undefined,
  discardedDrafts: [],
};

const TOAST_DEBOUNCE_INTERVAL = 200;

function getSavingMessage(numPending: number, requestFailed?: boolean) {
  if (requestFailed) {
    return 'Unable to save draft';
  }
  if (numPending === 0) {
    return 'All changes saved';
  }
  return undefined;
}

// Private but used in tests.
export function setComments(
  state: CommentState,
  comments?: {
    [path: string]: CommentInfo[];
  }
): CommentState {
  const nextState = {...state};
  if (deepEqual(comments, nextState.comments)) return state;
  nextState.comments = addPath(comments) || {};
  return nextState;
}

/** Updates a single comment in a state. */
export function updateComment(
  state: CommentState,
  comment: CommentInfo
): CommentState {
  if (!comment.path || !state.comments) {
    return state;
  }
  const newCommentsAtPath = [...state.comments[comment.path]];
  for (let i = 0; i < newCommentsAtPath.length; ++i) {
    if (newCommentsAtPath[i].id === comment.id) {
      // TODO: In "delete comment" the returned comment is missing some of the
      // fields (for example patch_set), which would throw errors when
      // rendering. Remove merging with the old comment, once that is fixed in
      // server code.
      newCommentsAtPath[i] = {...newCommentsAtPath[i], ...comment};

      return {
        ...state,
        comments: {
          ...state.comments,
          [comment.path]: newCommentsAtPath,
        },
      };
    }
  }
  throw new Error('Comment to be updated does not exist');
}

// Private but used in tests.
export function setDrafts(
  state: CommentState,
  drafts?: {[path: string]: DraftInfo[]}
): CommentState {
  if (deepEqual(drafts, state.drafts)) return state;
  const nextState = {...state};
  nextState.drafts = addPath(drafts);
  return nextState;
}

// Private but used in tests.
export function setPortedComments(
  state: CommentState,
  portedComments?: {[path: string]: CommentInfo[]}
): CommentState {
  if (deepEqual(portedComments, state.portedComments)) return state;
  const nextState = {...state};
  nextState.portedComments = portedComments || {};
  return nextState;
}

// Private but used in tests.
export function setPortedDrafts(
  state: CommentState,
  portedDrafts?: {[path: string]: DraftInfo[]}
): CommentState {
  if (deepEqual(portedDrafts, state.portedDrafts)) return state;
  const nextState = {...state};
  nextState.portedDrafts = portedDrafts || {};
  return nextState;
}

// Private but used in tests.
export function setDiscardedDraft(
  state: CommentState,
  draft: DraftInfo
): CommentState {
  const nextState = {...state};
  nextState.discardedDrafts = [...nextState.discardedDrafts, draft];
  return nextState;
}

// Private but used in tests.
export function deleteDiscardedDraft(
  state: CommentState,
  draftID?: string
): CommentState {
  const nextState = {...state};
  const drafts = [...nextState.discardedDrafts];
  const index = drafts.findIndex(draft => id(draft) === draftID);
  if (index === -1) {
    throw new Error('discarded draft not found');
  }
  drafts.splice(index, 1);
  nextState.discardedDrafts = drafts;
  return nextState;
}

/** Adds or updates a draft in the state. */
export function setDraft(state: CommentState, draft: DraftInfo): CommentState {
  const nextState = {...state};
  assert(!!draft.path, 'draft without path');
  assert(isDraft(draft), 'draft is not a draft');

  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  if (!drafts[draft.path]) drafts[draft.path] = [] as DraftInfo[];
  else drafts[draft.path] = [...drafts[draft.path]];
  const index = drafts[draft.path].findIndex(d => id(d) === id(draft));
  if (index !== -1) {
    drafts[draft.path][index] = draft;
  } else {
    drafts[draft.path].push(draft);
  }
  return nextState;
}

/** Removes a draft from the state.
 *
 * Removed draft is stored in discardedDrafts for potential undo operation.
 * discardedDrafts however is only a client-side cache and such drafts are not
 * retained in the server.
 */
export function deleteDraft(
  state: CommentState,
  draft: DraftInfo
): CommentState {
  const nextState = {...state};
  assert(!!draft.path, 'draft without path');
  assert(isDraft(draft), 'draft is not a draft');

  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  const index = (drafts[draft.path] || []).findIndex(d => id(d) === id(draft));
  if (index === -1) return state;
  const discardedDraft = drafts[draft.path][index];
  drafts[draft.path] = [...drafts[draft.path]];
  drafts[draft.path].splice(index, 1);
  return setDiscardedDraft(nextState, discardedDraft);
}

/**
 * Remove drafts that correspond to the existing published comments.
 *
 * Deletion of drafts is handled asynchronously by the server, because of that
 * it's possible that after the draft is published the server still returns it
 * as part of the drafts.
 */
export function filterOutPublishedDrafts(
  drafts: {[path: string]: DraftInfo[]},
  commentIds: Set<UrlEncodedCommentId>
): {drafts: {[path: string]: DraftInfo[]}; removedCnt: number} {
  let removedCnt = 0;
  const filteredDrafts: {[path: string]: DraftInfo[]} = {};
  for (const [path, pathDrafts] of Object.entries(drafts)) {
    for (const draft of pathDrafts) {
      if (draft.id && commentIds.has(draft.id)) {
        ++removedCnt;
        continue;
      }
      if (!(path in filteredDrafts)) {
        filteredDrafts[path] = [];
      }
      filteredDrafts[path].push(draft);
    }
  }
  return {drafts: filteredDrafts, removedCnt};
}

export const commentsModelToken = define<CommentsModel>('comments-model');
/**
 * Model that maintains the state of all comments and drafts for the current
 * change in the context of change-view.
 */
export class CommentsModel extends Model<CommentState> {
  public readonly commentsLoading$ = select(
    this.state$,
    commentState =>
      commentState.comments === undefined || commentState.drafts === undefined
  );

  public readonly comments$ = select(
    this.state$,
    commentState => commentState.comments
  );

  public readonly drafts$ = select(
    this.state$,
    commentState => commentState.drafts
  );

  public readonly draftsLoading$ = select(
    this.drafts$,
    drafts => drafts === undefined
  );

  public readonly draftsArray$ = select(this.drafts$, drafts =>
    Object.values(drafts ?? {}).flat()
  );

  public readonly draftsSaved$ = select(this.draftsArray$, drafts =>
    drafts.filter(d => !isNew(d))
  );

  public readonly draftsCount$ = select(
    this.draftsSaved$,
    drafts => drafts.length
  );

  public readonly portedComments$ = select(
    this.state$,
    commentState => commentState.portedComments
  );

  public readonly discardedDrafts$ = select(
    this.state$,
    commentState => commentState.discardedDrafts
  );

  public readonly savingInProgress$ = select(this.draftsArray$, drafts =>
    drafts.some(isSaving)
  );

  public readonly savingError$ = select(this.draftsArray$, drafts =>
    drafts.some(isError)
  );

  public readonly patchsetLevelDrafts$ = select(this.draftsArray$, drafts =>
    drafts.filter(
      draft =>
        draft.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS &&
        !draft.in_reply_to
    )
  );

  public readonly mentionedUsersInDrafts$: Observable<AccountInfo[]> =
    this.draftsArray$.pipe(
      switchMap(comments => {
        const users: AccountInfo[] = [];
        for (const comment of comments) {
          users.push(...extractMentionedUsers(comment.message));
        }
        const uniqueUsers = users.filter(
          (user, index) =>
            index === users.findIndex(u => getUserId(u) === getUserId(user))
        );
        // forkJoin only emits value when the array is non-empty
        if (uniqueUsers.length === 0) {
          return of(uniqueUsers);
        }
        const filledUsers$: Observable<AccountInfo | undefined>[] =
          uniqueUsers.map(user => from(this.accountsModel.fillDetails(user)));
        return forkJoin(filledUsers$);
      }),
      map(users => users.filter(isDefined)),
      distinctUntilChanged(deepEqual),
      shareReplay(1)
    );

  public readonly mentionedUsersInUnresolvedDrafts$: Observable<AccountInfo[]> =
    this.draftsArray$.pipe(
      switchMap(drafts => {
        const users: AccountInfo[] = [];
        const comments = drafts.filter(c => c.unresolved);
        for (const comment of comments) {
          users.push(...extractMentionedUsers(comment.message));
        }
        const uniqueUsers = users.filter(
          (user, index) =>
            index === users.findIndex(u => getUserId(u) === getUserId(user))
        );
        // forkJoin only emits value when the array is non-empty
        if (uniqueUsers.length === 0) {
          return of(uniqueUsers);
        }
        const filledUsers$: Observable<AccountInfo | undefined>[] =
          uniqueUsers.map(user => from(this.accountsModel.fillDetails(user)));
        return forkJoin(filledUsers$);
      }),
      map(users => users.filter(isDefined)),
      distinctUntilChanged(deepEqual),
      shareReplay(1)
    );

  // Emits a new value even if only a single draft is changed. Components should
  // aim to subsribe to something more specific.
  public readonly changeComments$ = select(
    this.state$,
    commentState =>
      new ChangeComments(
        commentState.comments,
        commentState.drafts,
        commentState.portedComments,
        commentState.portedDrafts
      )
  );

  public readonly threads$ = select(this.changeComments$, changeComments =>
    changeComments.getAllThreadsForChange()
  );

  public readonly threadsSaved$ = select(this.threads$, threads =>
    threads.filter(t => !isNewThread(t))
  );

  public readonly draftThreadsSaved$ = select(this.threads$, threads =>
    threads.filter(t => !isNewThread(t) && isDraftThread(t))
  );

  public readonly threadsWithUnappliedSuggestions$;

  public readonly commentedPaths$;

  public readonly reloadAllComments$;

  public thread$(id: UrlEncodedCommentId) {
    return select(this.threads$, threads => threads.find(t => t.rootId === id));
  }

  private numPendingDraftRequests = 0;

  private changeNum?: NumericChangeId;

  private drafts: {[path: string]: DraftInfo[]} = {};

  private draftToastTask?: DelayedTask;

  private discardedDrafts: DraftInfo[] = [];

  constructor(
    private readonly changeViewModel: ChangeViewModel,
    private readonly changeModel: ChangeModel,
    private readonly accountsModel: AccountsModel,
    private readonly restApiService: RestApiService,
    private readonly reporting: ReportingService,
    private readonly navigation: NavigationService
  ) {
    super(initialState);

    this.threadsWithUnappliedSuggestions$ = select(
      combineLatest([this.threads$, this.changeModel.latestPatchNum$]),
      ([threads, latestPs]) =>
        threads.filter(
          t =>
            isUnresolved(t) &&
            hasSuggestion(t) &&
            getFirstComment(t)?.patch_set === latestPs
        )
    );
    this.commentedPaths$ = select(
      combineLatest([
        this.changeComments$,
        this.changeModel.basePatchNum$,
        this.changeModel.patchNum$,
      ]),
      ([changeComments, basePatchNum, patchNum]) => {
        if (!patchNum) return [];
        const pathsMap = changeComments.getPaths({basePatchNum, patchNum});
        return Object.keys(pathsMap);
      }
    );
    this.reloadAllComments$ = new BehaviorSubject(undefined);

    this.subscriptions.push(
      this.savingInProgress$.subscribe(savingInProgress => {
        if (savingInProgress) {
          this.navigation.blockNavigation('draft comment still saving');
        } else {
          this.navigation.releaseNavigation('draft comment still saving');
        }
      })
    );
    this.subscriptions.push(
      this.savingError$.subscribe(savingError => {
        if (savingError) {
          this.navigation.blockNavigation('draft comment failed to save');
        } else {
          this.navigation.releaseNavigation('draft comment failed to save');
        }
      })
    );
    this.subscriptions.push(
      this.discardedDrafts$.subscribe(x => (this.discardedDrafts = x))
    );
    this.subscriptions.push(
      this.drafts$.subscribe(x => (this.drafts = x ?? {}))
    );
    // Patchset-level draft should always exist when opening reply dialog.
    // If there are none, create an empty one.
    this.subscriptions.push(
      combineLatest([
        this.draftsLoading$,
        this.patchsetLevelDrafts$,
        this.changeModel.latestPatchNum$,
      ]).subscribe(([loading, plDraft, latestPatchNum]) => {
        if (loading || plDraft.length > 0 || !latestPatchNum) return;
        this.addNewDraft(createNewPatchsetLevel(latestPatchNum, '', false));
      })
    );
    this.subscriptions.push(
      combineLatest([this.changeViewModel.changeNum$, this.reloadAllComments$])
        .pipe(
          switchMap(([changeNum, _]) => {
            this.changeNum = changeNum;
            this.setState({...initialState});
            if (!changeNum) return of([undefined, undefined, undefined]);
            return forkJoin([
              this.restApiService.getDiffComments(changeNum),
              this.restApiService.getDiffDrafts(changeNum),
            ]);
          })
        )
        .subscribe(([comments, drafts]) => {
          this.modifyState(s => {
            s = setComments(s, comments);
            return setDrafts(s, drafts);
          });
        })
    );
    // When the patchset selection changes update information about comments
    // ported from earlier patchsets.
    this.subscriptions.push(
      combineLatest([this.changeModel.changeNum$, this.changeModel.patchNum$])
        .pipe(
          switchMap(([changeNum, patchNum]) => {
            this.changeNum = changeNum;
            if (!changeNum) return of([undefined, undefined]);
            const revision = patchNum ?? (CURRENT as RevisionId);
            return forkJoin([
              this.restApiService.getPortedComments(changeNum, revision),
              this.restApiService.getPortedDrafts(changeNum, revision),
            ]);
          })
        )
        .subscribe(([portedComments, portedDrafts]) =>
          this.modifyState(s => {
            s = setPortedComments(s, portedComments);
            return setPortedDrafts(s, portedDrafts);
          })
        )
    );
    this.subscriptions.push(
      combineLatest([
        this.comments$,
        this.changeModel.latestPatchNum$,
      ]).subscribe(([comments, latestPatchset]) => {
        this.reportCommentStats(comments, latestPatchset);
      })
    );
    this.subscriptions.push(
      combineLatest([
        this.threadsSaved$,
        this.changeModel.latestPatchNum$,
      ]).subscribe(([threads, latestPatchset]) => {
        this.reportThreadsStats(threads, latestPatchset);
      })
    );
  }

  // Note that this does *not* reload ported comments.
  reloadAllComments() {
    this.reloadAllComments$.next(undefined);
  }

  // visible for testing
  modifyState(reducer: (state: CommentState) => CommentState) {
    this.setState(reducer({...this.getState()}));
  }

  override setState(state: CommentState): void {
    const commentIds = new Set<UrlEncodedCommentId>();
    if (state.comments) {
      Object.values(state.comments)
        .flatMap(x => x)
        .forEach(c => commentIds.add(c.id));
    }
    if (state.drafts) {
      const filterResult = filterOutPublishedDrafts(state.drafts, commentIds);
      state.drafts = filterResult.drafts;
      if (filterResult.removedCnt !== 0) {
        this.reporting.reportExecution(
          Execution.PUBLISHED_DRAFTS_DEDUPLICATED,
          {
            message: `Detected ${filterResult.removedCnt} drafts that are also published comments`,
            count: filterResult.removedCnt,
          }
        );
      }
    }
    if (state.portedDrafts) {
      state.portedDrafts = filterOutPublishedDrafts(
        state.portedDrafts,
        commentIds
      ).drafts;
    }
    super.setState(state);
  }

  private reportCommentStats(
    obj?: {[path: string]: CommentInfo[]},
    latestPatchset?: PatchSetNumber
  ) {
    if (!obj || !latestPatchset) return;
    const comments = Object.values(obj).flat();
    if (comments.length === 0) return;

    const commentsLatest = comments.filter(c => c.patch_set === latestPatchset);
    const commentsUnresolved = comments.filter(c => c.unresolved);
    const commentsLatestUnresolved = commentsLatest.filter(c => c.unresolved);

    const hasFix = (c: CommentInfo) => (c.fix_suggestions?.length ?? 0) > 0;

    const details = {
      countLatest: commentsLatest.length,
      countLatestWithFix: commentsLatest.filter(hasFix).length,
      countLatestWithUserFix: commentsLatest.filter(hasUserSuggestion).length,
      countLatestUnresolved: commentsLatestUnresolved.length,
      countLatestUnresolvedWithFix:
        commentsLatestUnresolved.filter(hasFix).length,
      countLatestUnresolvedWithUserFix:
        commentsLatestUnresolved.filter(hasUserSuggestion).length,
      countAll: comments.length,
      countAllUnresolved: commentsUnresolved.length,
      countAllWithFix: comments.filter(hasFix).length,
      countAllUnresolvedWithFix: commentsUnresolved.filter(hasFix).length,
      countAllWithUserFix: comments.filter(hasUserSuggestion).length,
      countAllUnresolvedWithUserFix: comments.filter(
        c => c.unresolved && hasUserSuggestion(c)
      ).length,
    };
    this.reporting.reportInteraction(Interaction.COMMENTS_STATS, details, {
      deduping: Deduping.EVENT_ONCE_PER_CHANGE,
    });
  }

  private reportThreadsStats(
    threads?: CommentThread[],
    latestPatchset?: PatchSetNumber
  ) {
    if (!threads || !latestPatchset) return;
    if (threads.length === 0) return;

    const threadsLatest = threads.filter(
      t => getFirstComment(t)?.patch_set === latestPatchset
    );
    const threadsUnresolved = threads.filter(isUnresolved);
    const commentsLatestUnresolved = threadsLatest.filter(isUnresolved);

    const hasFix = (t: CommentThread) =>
      (getFirstComment(t)?.fix_suggestions?.length ?? 0) > 0;

    const hasUserFix = (t: CommentThread) => {
      const firstComment = getFirstComment(t);
      return firstComment && hasUserSuggestion(firstComment);
    };

    const details = {
      countLatest: threadsLatest.length,
      countLatestWithFix: threadsLatest.filter(hasFix).length,
      countLatestWithUserFix: threadsLatest.filter(hasUserFix).length,
      countLatestUnresolved: commentsLatestUnresolved.length,
      countLatestUnresolvedWithFix:
        commentsLatestUnresolved.filter(hasFix).length,
      countLatestUnresolvedWithUserFix:
        commentsLatestUnresolved.filter(hasUserFix).length,
      countAll: threads.length,
      countAllUnresolved: threadsUnresolved.length,
      countAllWithFix: threads.filter(hasFix).length,
      countAllUnresolvedWithFix: threadsUnresolved.filter(hasFix).length,
      countAllWithUserFix: threads.filter(hasUserFix).length,
      countAllUnresolvedWithUserFix:
        threadsUnresolved.filter(hasUserFix).length,
    };
    this.reporting.reportInteraction(Interaction.THREADS_STATS, details, {
      deduping: Deduping.EVENT_ONCE_PER_CHANGE,
    });
  }

  async restoreDraft(draftId: UrlEncodedCommentId) {
    const found = this.discardedDrafts?.find(d => id(d) === draftId);
    if (!found) throw new Error('discarded draft not found');
    const newDraft: DraftInfo = {
      ...found,
      ...createNew(),
    };
    await this.saveDraft(newDraft);
    this.modifyState(s => deleteDiscardedDraft(s, draftId));
  }

  /**
   * Adds a new draft without saving it.
   *
   * There is no equivalent `removeNewDraft()` method, because
   * `discardDraft()` can be used.
   */
  addNewDraft(draft: DraftInfo) {
    assert(isNew(draft), 'draft must be new');
    this.modifyState(s => setDraft(s, draft));
  }

  /**
   * Saves a new or updates an existing draft.
   *
   * `draft.message` must not be empty: Use `discardDraft()` instead.
   *
   * Draft must not be in `SAVING` state already.
   */
  async saveDraft(draft: DraftInfo, showToast = true): Promise<DraftInfo> {
    assertIsDefined(this.changeNum, 'change number');
    assertIsDefined(draft.patch_set, 'patchset number of comment draft');
    assert(!!draft.message?.trim(), 'cannot save empty draft');
    assert(!isSaving(draft), 'saving already in progress');

    // optimistic update
    const draftSaving: DraftInfo = {...draft, savingState: SavingState.SAVING};
    this.modifyState(s => setDraft(s, draftSaving));

    // Saving the change number as to make sure that the response is still
    // relevant when it comes back. The user maybe have navigated away.
    const changeNum = this.changeNum;
    this.report(Interaction.SAVE_COMMENT, draft);
    if (showToast) this.showStartRequest();
    const timing = isNew(draft) ? Timing.DRAFT_CREATE : Timing.DRAFT_UPDATE;
    const timer = this.reporting.getTimer(timing);

    let savedComment;
    try {
      const result = await this.restApiService.saveDiffDraft(
        changeNum,
        draft.patch_set,
        convertToCommentInput(draft)
      );
      if (changeNum !== this.changeNum) return draft;
      if (!result.ok) throw new Error('request failed');
      savedComment = (await readJSONResponsePayload(result))
        .parsed as unknown as CommentInfo;
    } catch (error) {
      if (showToast) this.handleFailedDraftRequest();
      const draftError: DraftInfo = {...draft, savingState: SavingState.ERROR};
      this.modifyState(s => setDraft(s, draftError));
      return draftError;
    }

    const draftSaved: DraftInfo = {
      ...draft,
      id: savedComment.id,
      updated: savedComment.updated,
      savingState: SavingState.OK,
    };
    timer.end({id: draftSaved.id});
    if (showToast) this.showEndRequest();
    this.modifyState(s => setDraft(s, draftSaved));
    this.report(Interaction.COMMENT_SAVED, draftSaved);
    return draftSaved;
  }

  async discardDraft(draftId: UrlEncodedCommentId) {
    const draft = this.lookupDraft(draftId);
    assertIsDefined(draft, `draft not found by id ${draftId}`);
    assertIsDefined(draft.patch_set, 'patchset number of comment draft');
    assert(!isSaving(draft), 'saving already in progress');

    // optimistic update
    this.modifyState(s => deleteDraft(s, draft));

    // For "unsaved" drafts there is nothing to discard on the server side.
    if (draft.id) {
      if (!draft.message?.trim()) throw new Error('empty draft');
      // Saving the change number as to make sure that the response is still
      // relevant when it comes back. The user maybe have navigated away.
      assertIsDefined(this.changeNum, 'change number');
      const changeNum = this.changeNum;
      this.report(Interaction.DISCARD_COMMENT, draft);
      this.showStartRequest();
      const timer = this.reporting.getTimer(Timing.DRAFT_DISCARD);
      const result = await this.restApiService.deleteDiffDraft(
        changeNum,
        draft.patch_set,
        {id: draft.id}
      );
      timer.end({id: draft.id});
      if (changeNum !== this.changeNum) throw new Error('change changed');
      if (!result.ok) {
        this.handleFailedDraftRequest();
        await this.restoreDraft(draftId);
        throw new Error(
          `Failed to discard draft comment: ${JSON.stringify(result)}`
        );
      }
      this.showEndRequest();
    }

    // We don't store empty discarded drafts and don't need an UNDO then.
    if (draft.message?.trim()) {
      fire(document, 'show-alert', {
        message: 'Draft Discarded',
        action: 'Undo',
        callback: () => this.restoreDraft(draftId),
      });
    }
    this.report(Interaction.COMMENT_DISCARDED, draft);
  }

  async deleteComment(
    changeNum: NumericChangeId,
    comment: Comment,
    reason: string
  ) {
    assertIsDefined(comment.patch_set, 'comment.patch_set');
    assert(!isDraft(comment), 'Admin deletion is only for published comments.');

    const newComment = await this.restApiService.deleteComment(
      changeNum,
      comment.patch_set,
      comment.id,
      reason
    );
    // Don't update state on server error.
    if (newComment) {
      this.modifyState(s => updateComment(s, newComment));
    }
  }

  private report(interaction: Interaction, comment: Comment) {
    const details = reportingDetails(comment);
    this.reporting.reportInteraction(interaction, details);
  }

  private showStartRequest() {
    this.numPendingDraftRequests += 1;
    this.updateRequestToast();
  }

  private showEndRequest() {
    this.numPendingDraftRequests -= 1;
    this.updateRequestToast();
  }

  private handleFailedDraftRequest() {
    this.numPendingDraftRequests -= 1;
    this.updateRequestToast(/* requestFailed=*/ true);
  }

  private updateRequestToast(requestFailed?: boolean) {
    if (this.numPendingDraftRequests === 0 && !requestFailed) {
      fire(document, 'hide-alert', {});
      return;
    }
    const message = getSavingMessage(
      this.numPendingDraftRequests,
      requestFailed
    );
    if (!message) return;
    this.draftToastTask = debounce(
      this.draftToastTask,
      () => fireAlert(document.body, message),
      TOAST_DEBOUNCE_INTERVAL
    );
  }

  private lookupDraft(commentId: UrlEncodedCommentId): DraftInfo | undefined {
    return Object.values(this.drafts)
      .flat()
      .find(draft => id(draft) === commentId);
  }
}
