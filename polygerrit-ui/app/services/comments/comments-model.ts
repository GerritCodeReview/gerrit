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

import {BehaviorSubject} from 'rxjs';
import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {
  PathToCommentsInfoMap,
  RobotCommentInfo,
} from '../../types/common';
import {addPath, DraftInfo} from '../../utils/comment-util';
import {combineLatest, Subscription} from 'rxjs';
import {NumericChangeId, PatchSetNum, RevisionId} from '../../types/common';
import {fireAlert} from '../../utils/event-util';
import {CURRENT} from '../../utils/patch-set-util';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {changeNum$, currentPatchNum$} from '../change/change-model';

import {routerChangeNum$} from '../router/router-model';
import {Finalizable} from '../registry';
import {select} from '../../utils/observable-util';

export interface CommentState {
  /** undefined means 'still loading' */
  comments?: PathToCommentsInfoMap;
  /** undefined means 'still loading' */
  robotComments?: {[path: string]: RobotCommentInfo[]};
  /** undefined means 'still loading' */
  drafts?: {[path: string]: DraftInfo[]};
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

function updateStateAddDraft(state: CommentState, draft: DraftInfo) {
  const nextState = {...state};
  if (!draft.path) throw new Error('draft path undefined');
  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  if (!drafts[draft.path]) drafts[draft.path] = [] as DraftInfo[];
  else drafts[draft.path] = [...drafts[draft.path]];
  const index = drafts[draft.path].findIndex(
    d =>
      (d.__draftID && d.__draftID === draft.__draftID) ||
      (d.id && d.id === draft.id)
  );
  if (index !== -1) {
    drafts[draft.path][index] = draft;
  } else {
    drafts[draft.path].push(draft);
  }
  return nextState;
}

function updateStateUpdateDraft(state: CommentState, draft: DraftInfo) {
  const nextState = {...state};
  if (!draft.path) throw new Error('draft path undefined');
  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  if (!drafts[draft.path])
    throw new Error('draft: trying to edit non-existent draft');
  drafts[draft.path] = [...drafts[draft.path]];
  const index = drafts[draft.path].findIndex(
    d =>
      (d.__draftID && d.__draftID === draft.__draftID) ||
      (d.id && d.id === draft.id)
  );
  if (index === -1) return;
  drafts[draft.path][index] = draft;
  return nextState;
}

function updateStateUndoDiscardedDraft(state: CommentState, draftID?: string) {
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

// Private but used in tests.
export function updateStateDeleteDraft(state: CommentState, draft: DraftInfo) {
  if (!draft.path) throw new Error('draft path undefined');
  const nextState = {...state};
  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  const index = (drafts[draft.path] || []).findIndex(
    d =>
      (d.__draftID && d.__draftID === draft.__draftID) ||
      (d.id && d.id === draft.id)
  );
  if (index === -1) return;
  const discardedDraft = drafts[draft.path][index];
  drafts[draft.path] = [...drafts[draft.path]];
  drafts[draft.path].splice(index, 1);

  nextState.discardedDrafts = [...nextState.discardedDrafts, discardedDraft];
  return nextState;
}

export class CommentsModel implements Finalizable {
  private readonly privateState$: BehaviorSubject<CommentState> =
    new BehaviorSubject(initialState);

  public readonly commentsLoading$ = select(
    this.privateState$,
      commentState =>
        commentState.comments === undefined ||
        commentState.robotComments === undefined ||
        commentState.drafts === undefined
  );

  public readonly comments$ = select(
    this.privateState$,
    commentState => commentState.comments
  );

  public readonly drafts$ = select(
    this.privateState$,
    commentState => commentState.drafts
  );

  public readonly portedComments$ = select(
    this.privateState$,
    commentState => commentState.portedComments
  );

  // Emits a new value even if only a single draft is changed. Components should
  // aim to subsribe to something more specific.
  public readonly changeComments$ = select(
    this.privateState$,
      commentState =>
        new ChangeComments(
          commentState.comments,
          commentState.robotComments,
          commentState.drafts,
          commentState.portedComments,
          commentState.portedDrafts
        )
    );

  public readonly threads$ = select(
    this.changeComments$,
    changeComments => changeComments.getAllThreadsForChange()
  );

  private changeNum?: NumericChangeId;

  private patchNum?: PatchSetNum;

  private readonly reloadListener: () => void;

  private readonly subscriptions: Subscription[] = [];

  constructor(readonly restApiService: RestApiService) {
    this.subscriptions.push(
      routerChangeNum$.subscribe(changeNum => {
        this.changeNum = changeNum;
        this.publishState({...initialState});
        this.reloadAllComments();
      })
    );
    this.subscriptions.push(
      combineLatest([changeNum$, currentPatchNum$]).subscribe(
        ([changeNum, patchNum]) => {
          this.changeNum = changeNum;
          this.patchNum = patchNum;
          this.reloadAllPortedComments();
        }
      )
    );
    this.reloadListener = () => {
      this.reloadAllComments();
      this.reloadAllPortedComments();
    };
    document.addEventListener('reload', this.reloadListener);
  }

  finalize() {
    document.removeEventListener('reload', this.reloadListener!);
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions.splice(0, this.subscriptions.length);
  }

  // Note that this does *not* reload ported comments.
  reloadAllComments() {
    if (!this.changeNum) return;
    this.reloadComments(this.changeNum);
    this.reloadRobotComments(this.changeNum);
    this.reloadDrafts(this.changeNum);
  }

  reloadAllPortedComments() {
    if (!this.changeNum) return;
    if (!this.patchNum) return;
    this.reloadPortedComments(this.changeNum, this.patchNum);
    this.reloadPortedDrafts(this.changeNum, this.patchNum);
  }

  async reloadComments(changeNum: NumericChangeId): Promise<void> {
    const comments = await this.restApiService.getDiffComments(changeNum)
    const nextState = {...this.getState()};
    nextState.comments = addPath(comments) || {};
    this.publishState(nextState);
  }

  async reloadRobotComments(changeNum: NumericChangeId): Promise<void> {
    const robotComments =
      await this.restApiService.getDiffRobotComments(changeNum)
    const nextState = {...this.getState()};
    nextState.robotComments = addPath(robotComments) || {};
    this.publishState(nextState);
  }

  async reloadDrafts(changeNum: NumericChangeId): Promise<void> {
    const drafts = await this.restApiService.getDiffDrafts(changeNum)
    const nextState = {...this.getState()};
    nextState.drafts = addPath(drafts) || {};
    this.publishState(nextState);
  }

  async reloadPortedComments(
    changeNum: NumericChangeId,
    patchNum = CURRENT as RevisionId
  ): Promise<void> {
    const portedComments =
      await this.restApiService.getPortedComments(changeNum, patchNum);
      const nextState = {...this.getState()};
    nextState.portedComments = portedComments || {};
    this.publishState(nextState);
  }

  async reloadPortedDrafts(
    changeNum: NumericChangeId,
    patchNum = CURRENT as RevisionId
  ): Promise<void> {
    const portedDrafts =
      await this.restApiService.getPortedDrafts(changeNum, patchNum);
      const nextState = {...this.getState()};
    nextState.portedDrafts = portedDrafts || {};
    this.publishState(nextState);
  }

  addDraft(draft: DraftInfo) {
    this.publishState(updateStateAddDraft(this.getState(), draft));
  }

  cancelDraft(draft: DraftInfo) {
    const nextState = updateStateUpdateDraft(this.getState(), draft);
    if (nextState) this.publishState(nextState);
  }

  editDraft(draft: DraftInfo) {
    const nextState = updateStateUpdateDraft(this.getState(), draft);
    if (nextState) this.publishState(nextState);
  }

  deleteDraft(draft: DraftInfo) {
    const nextState = updateStateDeleteDraft(this.getState(), draft);
    if (nextState) this.publishState(nextState);
  }

  async restoreDraft(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    draftID: string
  ) {
    const state = this.getState();
    const draft = {...state.discardedDrafts?.find(d => d.id === draftID)};
    if (!draft) throw new Error('discarded draft not found');
    // delete draft ID since we want to treat this as a new draft creation
    delete draft.id;
    const result = await this.restApiService
        .saveDiffDraft(changeNum, patchNum, draft)

    if (!result.ok) {
      fireAlert(document, 'Unable to restore draft');
      return;
    }
    const obj = await this.restApiService.getResponseObject(result);
    const resComment = obj as unknown as DraftInfo;
    resComment.patch_set = draft.patch_set;
    this.publishState(
      updateStateUndoDiscardedDraft(
        updateStateAddDraft(this.getState(), resComment),
        draftID)
    )
  }

  private getState(): CommentState {
    return this.privateState$.getValue();
  }

  // Private except for tests.
  publishState(state: CommentState) {
    this.privateState$.next(state);
  }
}
