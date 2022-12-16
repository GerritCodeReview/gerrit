/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {
  CommentBasics,
  CommentInfo,
  NumericChangeId,
  PatchSetNum,
  RevisionId,
  UrlEncodedCommentId,
  PathToCommentsInfoMap,
  RobotCommentInfo,
  PathToRobotCommentsInfoMap,
  AccountInfo,
} from '../../types/common';
import {
  addPath,
  Comment,
  DraftInfo,
  isDraft,
  isDraftOrUnsaved,
  isDraftThread,
  isUnsaved,
  reportingDetails,
  UnsavedInfo,
} from '../../utils/comment-util';
import {deepEqual} from '../../utils/deep-util';
import {select} from '../../utils/observable-util';
import {define} from '../dependency';
import {combineLatest, forkJoin, from, Observable, of} from 'rxjs';
import {fire, fireAlert, fireEvent} from '../../utils/event-util';
import {CURRENT} from '../../utils/patch-set-util';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {ChangeModel} from '../change/change-model';
import {Interaction, Timing} from '../../constants/reporting';
import {assertIsDefined} from '../../utils/common-util';
import {debounce, DelayedTask} from '../../utils/async-util';
import {pluralize} from '../../utils/string-util';
import {ReportingService} from '../../services/gr-reporting/gr-reporting';
import {Model} from '../model';
import {Deduping} from '../../api/reporting';
import {extractMentionedUsers, getUserId} from '../../utils/account-util';
import {EventType} from '../../types/events';
import {SpecialFilePath} from '../../constants/constants';
import {AccountsModel} from '../accounts-model/accounts-model';
import {
  distinctUntilChanged,
  map,
  shareReplay,
  switchMap,
} from 'rxjs/operators';
import {isDefined} from '../../types/types';
import {ChangeViewModel} from '../views/change';

export interface CommentState {
  /** undefined means 'still loading' */
  comments?: PathToCommentsInfoMap;
  /** undefined means 'still loading' */
  robotComments?: {[path: string]: RobotCommentInfo[]};
  // All drafts are DraftInfo objects and have __draft = true set.
  // Drafts have an id and are known to the backend. Unsaved drafts
  // (see UnsavedInfo) do NOT belong in the application model.
  /** undefined means 'still loading' */
  drafts?: {[path: string]: DraftInfo[]};
  // Ported comments only affect `CommentThread` properties, not individual
  // comments.
  /** undefined means 'still loading' */
  portedComments?: PathToCommentsInfoMap;
  /** undefined means 'still loading' */
  portedDrafts?: PathToCommentsInfoMap;
  /**
   * If a draft is discarded by the user, then we temporarily keep it in this
   * array in case the user decides to Undo the discard operation and bring the
   * draft back. Once restored, the draft is removed from this array.
   */
  discardedDrafts: DraftInfo[];
}

const initialState: CommentState = {
  comments: undefined,
  robotComments: undefined,
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
  return `Saving ${pluralize(numPending, 'draft')}...`;
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
export function setRobotComments(
  state: CommentState,
  robotComments?: {
    [path: string]: RobotCommentInfo[];
  }
): CommentState {
  if (deepEqual(robotComments, state.robotComments)) return state;
  const nextState = {...state};
  nextState.robotComments = addPath(robotComments) || {};
  return nextState;
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
  portedComments?: PathToCommentsInfoMap
): CommentState {
  if (deepEqual(portedComments, state.portedComments)) return state;
  const nextState = {...state};
  nextState.portedComments = portedComments || {};
  return nextState;
}

// Private but used in tests.
export function setPortedDrafts(
  state: CommentState,
  portedDrafts?: PathToCommentsInfoMap
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
  const index = drafts.findIndex(d => d.id === draftID);
  if (index === -1) {
    throw new Error('discarded draft not found');
  }
  drafts.splice(index, 1);
  nextState.discardedDrafts = drafts;
  return nextState;
}

/** Adds or updates a draft. */
export function setDraft(state: CommentState, draft: DraftInfo): CommentState {
  const nextState = {...state};
  if (!draft.path) throw new Error('draft path undefined');
  if (!isDraft(draft)) throw new Error('draft is not a draft');
  if (isUnsaved(draft)) throw new Error('unsaved drafts dont belong to model');

  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  if (!drafts[draft.path]) drafts[draft.path] = [] as DraftInfo[];
  else drafts[draft.path] = [...drafts[draft.path]];
  const index = drafts[draft.path].findIndex(d => d.id && d.id === draft.id);
  if (index !== -1) {
    drafts[draft.path][index] = draft;
  } else {
    drafts[draft.path].push(draft);
  }
  return nextState;
}

export function deleteDraft(
  state: CommentState,
  draft: DraftInfo
): CommentState {
  const nextState = {...state};
  if (!draft.path) throw new Error('draft path undefined');
  if (!isDraft(draft)) throw new Error('draft is not a draft');
  if (isUnsaved(draft)) throw new Error('unsaved drafts dont belong to model');
  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  const index = (drafts[draft.path] || []).findIndex(
    d => d.id && d.id === draft.id
  );
  if (index === -1) return state;
  const discardedDraft = drafts[draft.path][index];
  drafts[draft.path] = [...drafts[draft.path]];
  drafts[draft.path].splice(index, 1);
  return setDiscardedDraft(nextState, discardedDraft);
}

export const commentsModelToken = define<CommentsModel>('comments-model');
export class CommentsModel extends Model<CommentState> {
  public readonly commentsLoading$ = select(
    this.state$,
    commentState =>
      commentState.comments === undefined ||
      commentState.robotComments === undefined ||
      commentState.drafts === undefined
  );

  public readonly comments$ = select(
    this.state$,
    commentState => commentState.comments
  );

  public readonly robotComments$ = select(
    this.state$,
    commentState => commentState.robotComments
  );

  public readonly robotCommentCount$ = select(
    this.robotComments$,
    robotComments => Object.values(robotComments ?? {}).flat().length
  );

  public readonly drafts$ = select(
    this.state$,
    commentState => commentState.drafts
  );

  public readonly draftsCount$ = select(
    this.drafts$,
    drafts => Object.values(drafts ?? {}).flat().length
  );

  public readonly portedComments$ = select(
    this.state$,
    commentState => commentState.portedComments
  );

  public readonly discardedDrafts$ = select(
    this.state$,
    commentState => commentState.discardedDrafts
  );

  public readonly patchsetLevelDrafts$ = select(this.drafts$, drafts =>
    Object.values(drafts ?? {})
      .flat()
      .filter(
        draft =>
          draft.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS &&
          !draft.in_reply_to
      )
  );

  public readonly mentionedUsersInDrafts$: Observable<AccountInfo[]> =
    this.drafts$.pipe(
      switchMap(drafts => {
        const users: AccountInfo[] = [];
        const comments = Object.values(drafts ?? {}).flat();
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
    this.drafts$.pipe(
      switchMap(drafts => {
        const users: AccountInfo[] = [];
        const comments = Object.values(drafts ?? {})
          .flat()
          .filter(c => c.unresolved);
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
        commentState.robotComments,
        commentState.drafts,
        commentState.portedComments,
        commentState.portedDrafts
      )
  );

  public readonly threads$ = select(this.changeComments$, changeComments =>
    changeComments.getAllThreadsForChange()
  );

  public readonly draftThreads$ = select(this.threads$, threads =>
    threads.filter(isDraftThread)
  );

  public readonly commentedPaths$ = select(
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

  public thread$(id: UrlEncodedCommentId) {
    return select(this.threads$, threads => threads.find(t => t.rootId === id));
  }

  private numPendingDraftRequests = 0;

  private changeNum?: NumericChangeId;

  private patchNum?: PatchSetNum;

  private readonly reloadListener: () => void;

  private drafts: {[path: string]: DraftInfo[]} = {};

  private draftToastTask?: DelayedTask;

  private discardedDrafts: DraftInfo[] = [];

  constructor(
    private readonly changeViewModel: ChangeViewModel,
    private readonly changeModel: ChangeModel,
    private readonly accountsModel: AccountsModel,
    private readonly restApiService: RestApiService,
    private readonly reporting: ReportingService
  ) {
    super(initialState);
    this.subscriptions.push(
      this.discardedDrafts$.subscribe(x => (this.discardedDrafts = x))
    );
    this.subscriptions.push(
      this.drafts$.subscribe(x => (this.drafts = x ?? {}))
    );
    this.subscriptions.push(
      this.changeModel.patchNum$.subscribe(x => (this.patchNum = x))
    );
    this.subscriptions.push(
      this.changeViewModel.changeNum$.subscribe(changeNum => {
        this.changeNum = changeNum;
        this.setState({...initialState});
        this.reloadAllComments();
      })
    );
    this.subscriptions.push(
      combineLatest([
        this.changeModel.changeNum$,
        this.changeModel.patchNum$,
      ]).subscribe(([changeNum, patchNum]) => {
        this.changeNum = changeNum;
        this.patchNum = patchNum;
        this.reloadAllPortedComments();
      })
    );
    this.reloadListener = () => {
      this.reloadAllComments();
      this.reloadAllPortedComments();
    };
    document.addEventListener('reload', this.reloadListener);
  }

  override finalize() {
    document.removeEventListener('reload', this.reloadListener);
    super.finalize();
  }

  // Note that this does *not* reload ported comments.
  async reloadAllComments() {
    if (!this.changeNum) return;
    await Promise.all([
      this.reloadComments(this.changeNum),
      this.reloadRobotComments(this.changeNum),
      this.reloadDrafts(this.changeNum),
    ]);
  }

  async reloadAllPortedComments() {
    if (!this.changeNum) return;
    if (!this.patchNum) return;
    await Promise.all([
      this.reloadPortedComments(this.changeNum, this.patchNum),
      this.reloadPortedDrafts(this.changeNum, this.patchNum),
    ]);
  }

  // visible for testing
  modifyState(reducer: (state: CommentState) => CommentState) {
    this.setState(reducer({...this.getState()}));
  }

  async reloadComments(changeNum: NumericChangeId): Promise<void> {
    const comments = await this.restApiService.getDiffComments(changeNum);
    this.modifyState(s => setComments(s, comments));
  }

  async reloadRobotComments(changeNum: NumericChangeId): Promise<void> {
    const robotComments = await this.restApiService.getDiffRobotComments(
      changeNum
    );
    this.reportRobotCommentStats(robotComments);
    this.modifyState(s => setRobotComments(s, robotComments));
  }

  private reportRobotCommentStats(obj?: PathToRobotCommentsInfoMap) {
    if (!obj) return;
    const comments = Object.values(obj).flat();
    if (comments.length === 0) return;
    const ids = comments.map(c => c.robot_id);
    const latestPatchset = comments.reduce(
      (latestPs, comment) =>
        Math.max(latestPs, (comment?.patch_set as number) ?? 0),
      0
    );
    const commentsLatest = comments.filter(c => c.patch_set === latestPatchset);
    const commentsFixes = comments
      .map(c => c.fix_suggestions?.length ?? 0)
      .filter(l => l > 0);
    const details = {
      firstId: ids[0],
      ids: [...new Set(ids)],
      count: comments.length,
      countLatest: commentsLatest.length,
      countFixes: commentsFixes.length,
    };
    this.reporting.reportInteraction(
      Interaction.ROBOT_COMMENTS_STATS,
      details,
      {deduping: Deduping.EVENT_ONCE_PER_CHANGE}
    );
  }

  async reloadDrafts(changeNum: NumericChangeId): Promise<void> {
    const drafts = await this.restApiService.getDiffDrafts(changeNum);
    this.modifyState(s => setDrafts(s, drafts));
  }

  async reloadPortedComments(
    changeNum: NumericChangeId,
    patchNum = CURRENT as RevisionId
  ): Promise<void> {
    const portedComments = await this.restApiService.getPortedComments(
      changeNum,
      patchNum
    );
    this.modifyState(s => setPortedComments(s, portedComments));
  }

  async reloadPortedDrafts(
    changeNum: NumericChangeId,
    patchNum = CURRENT as RevisionId
  ): Promise<void> {
    const portedDrafts = await this.restApiService.getPortedDrafts(
      changeNum,
      patchNum
    );
    this.modifyState(s => setPortedDrafts(s, portedDrafts));
  }

  async restoreDraft(id: UrlEncodedCommentId) {
    const found = this.discardedDrafts?.find(d => d.id === id);
    if (!found) throw new Error('discarded draft not found');
    const newDraft = {
      ...found,
      id: undefined,
      updated: undefined,
      __draft: undefined,
      __unsaved: true,
    };
    await this.saveDraft(newDraft);
    this.modifyState(s => deleteDiscardedDraft(s, id));
  }

  /**
   * Saves a new or updates an existing draft.
   * The model will only be updated when a successful response comes back.
   */
  async saveDraft(
    draft: DraftInfo | UnsavedInfo,
    showToast = true
  ): Promise<DraftInfo> {
    assertIsDefined(this.changeNum, 'change number');
    assertIsDefined(draft.patch_set, 'patchset number of comment draft');
    if (!draft.message?.trim()) throw new Error('Cannot save empty draft.');

    // Saving the change number as to make sure that the response is still
    // relevant when it comes back. The user maybe have navigated away.
    const changeNum = this.changeNum;
    this.report(Interaction.SAVE_COMMENT, draft);
    if (showToast) this.showStartRequest();
    const timing = isUnsaved(draft) ? Timing.DRAFT_CREATE : Timing.DRAFT_UPDATE;
    const timer = this.reporting.getTimer(timing);
    const result = await this.restApiService.saveDiffDraft(
      changeNum,
      draft.patch_set,
      draft
    );
    if (changeNum !== this.changeNum) throw new Error('change changed');
    if (!result.ok) {
      if (showToast) this.handleFailedDraftRequest();
      throw new Error(
        `Failed to save draft comment: ${JSON.stringify(result)}`
      );
    }
    const obj = await this.restApiService.getResponseObject(result);
    const savedComment = obj as unknown as CommentInfo;
    const updatedDraft = {
      ...draft,
      id: savedComment.id,
      updated: savedComment.updated,
      __draft: true,
      __unsaved: undefined,
    };
    timer.end({id: updatedDraft.id});
    if (showToast) this.showEndRequest();
    this.modifyState(s => setDraft(s, updatedDraft));
    this.report(Interaction.COMMENT_SAVED, updatedDraft);
    return updatedDraft;
  }

  async discardDraft(draftId: UrlEncodedCommentId) {
    const draft = this.lookupDraft(draftId);
    assertIsDefined(this.changeNum, 'change number');
    assertIsDefined(draft, `draft not found by id ${draftId}`);
    assertIsDefined(draft.patch_set, 'patchset number of comment draft');

    if (!draft.message?.trim()) throw new Error('saved draft cant be empty');
    // Saving the change number as to make sure that the response is still
    // relevant when it comes back. The user maybe have navigated away.
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
      throw new Error(
        `Failed to discard draft comment: ${JSON.stringify(result)}`
      );
    }
    this.showEndRequest();
    this.modifyState(s => deleteDraft(s, draft));
    // We don't store empty discarded drafts and don't need an UNDO then.
    if (draft.message?.trim()) {
      fire(document, EventType.SHOW_ALERT, {
        message: 'Draft Discarded',
        action: 'Undo',
        callback: () => this.restoreDraft(draft.id),
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
    if (isDraftOrUnsaved(comment)) {
      throw new Error('Admin deletion is only for published comments.');
    }

    const newComment = await this.restApiService.deleteComment(
      changeNum,
      comment.patch_set,
      comment.id,
      reason
    );
    this.modifyState(s => updateComment(s, newComment));
  }

  private report(interaction: Interaction, comment: CommentBasics) {
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
      fireEvent(document, 'hide-alert');
      return;
    }
    const message = getSavingMessage(
      this.numPendingDraftRequests,
      requestFailed
    );
    this.draftToastTask = debounce(
      this.draftToastTask,
      () => {
        // Note: the event is fired on the body rather than this element because
        // this element may not be attached by the time this executes, in which
        // case the event would not bubble.
        fireAlert(document.body, message);
      },
      TOAST_DEBOUNCE_INTERVAL
    );
  }

  private lookupDraft(id: UrlEncodedCommentId): DraftInfo | undefined {
    return Object.values(this.drafts)
      .flat()
      .find(d => d.id === id);
  }
}
