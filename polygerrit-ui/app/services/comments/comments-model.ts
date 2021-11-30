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

import {BehaviorSubject, Observable} from 'rxjs';
import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {
  CommentInfo,
  PathToCommentsInfoMap,
  RobotCommentInfo,
  UrlEncodedCommentId,
} from '../../types/common';
import {addPath, DraftInfo, isDraft, isUnsaved} from '../../utils/comment-util';
import {deepEqual} from '../../utils/deep-util';
import {select} from '../../utils/observable-util';

interface CommentState {
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

const privateState$ = new BehaviorSubject(initialState);

export function _testOnly_resetState() {
  // We cannot assign a new subject to privateState$, because all the selectors
  // have already subscribed to the original subject. So we have to emit the
  // initial state on the existing subject.
  privateState$.next({...initialState});
}

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const commentState$: Observable<CommentState> = privateState$;

export function _testOnly_getState() {
  return privateState$.getValue();
}

export function _testOnly_setState(state: CommentState) {
  privateState$.next(state);
}

export const commentsLoading$ = select(
  commentState$,
  commentState =>
    commentState.comments === undefined ||
    commentState.robotComments === undefined ||
    commentState.drafts === undefined
);

export const comments$ = select(
  commentState$,
  commentState => commentState.comments
);

export const drafts$ = select(
  commentState$,
  commentState => commentState.drafts
);

export const portedComments$ = select(
  commentState$,
  commentState => commentState.portedComments
);

export const discardedDrafts$ = select(
  commentState$,
  commentState => commentState.discardedDrafts
);

// Emits a new value even if only a single draft is changed. Components should
// aim to subsribe to something more specific.
export const changeComments$ = select(
  commentState$,
  commentState =>
    new ChangeComments(
      commentState.comments,
      commentState.robotComments,
      commentState.drafts,
      commentState.portedComments,
      commentState.portedDrafts
    )
);

export const threads$ = select(changeComments$, changeComments =>
  changeComments.getAllThreadsForChange()
);

export function thread$(id: UrlEncodedCommentId) {
  return select(threads$, threads => threads.find(t => t.rootId === id));
}

function publishState(state: CommentState) {
  privateState$.next(state);
}

/** Called when the change number changes. Wipes out all data from the state. */
export function updateStateReset() {
  publishState({...initialState});
}

export function updateStateComments(comments?: {
  [path: string]: CommentInfo[];
}) {
  const nextState = {...privateState$.getValue()};
  if (deepEqual(comments, nextState.comments)) return;
  nextState.comments = addPath(comments) || {};
  publishState(nextState);
}

export function updateStateRobotComments(robotComments?: {
  [path: string]: RobotCommentInfo[];
}) {
  const nextState = {...privateState$.getValue()};
  if (deepEqual(robotComments, nextState.robotComments)) return;
  nextState.robotComments = addPath(robotComments) || {};
  publishState(nextState);
}

export function updateStateDrafts(drafts?: {[path: string]: DraftInfo[]}) {
  const nextState = {...privateState$.getValue()};
  if (deepEqual(drafts, nextState.drafts)) return;
  nextState.drafts = addPath(drafts);
  publishState(nextState);
}

export function updateStatePortedComments(
  portedComments?: PathToCommentsInfoMap
) {
  const nextState = {...privateState$.getValue()};
  if (deepEqual(portedComments, nextState.portedComments)) return;
  nextState.portedComments = portedComments || {};
  publishState(nextState);
}

export function updateStatePortedDrafts(portedDrafts?: PathToCommentsInfoMap) {
  const nextState = {...privateState$.getValue()};
  if (deepEqual(portedDrafts, nextState.portedDrafts)) return;
  nextState.portedDrafts = portedDrafts || {};
  publishState(nextState);
}

export function updateStateSetDiscardedDraft(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
  nextState.discardedDrafts = [...nextState.discardedDrafts, draft];
  publishState(nextState);
}

export function updateStateDeleteDiscardedDraft(draftID?: string) {
  const nextState = {...privateState$.getValue()};
  const drafts = [...nextState.discardedDrafts];
  const index = drafts.findIndex(d => d.id === draftID);
  if (index === -1) {
    throw new Error('discarded draft not found');
  }
  drafts.splice(index, 1);
  nextState.discardedDrafts = drafts;
  publishState(nextState);
}

/** Adds or updates a draft. */
export function updateStateSetDraft(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
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
  publishState(nextState);
}

export function updateStateDeleteDraft(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
  if (!draft.path) throw new Error('draft path undefined');
  if (!isDraft(draft)) throw new Error('draft is not a draft');
  if (isUnsaved(draft)) throw new Error('unsaved drafts dont belong to model');
  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  const index = (drafts[draft.path] || []).findIndex(
    d => d.id && d.id === draft.id
  );
  if (index === -1) return;
  const discardedDraft = drafts[draft.path][index];
  drafts[draft.path] = [...drafts[draft.path]];
  drafts[draft.path].splice(index, 1);
  publishState(nextState);
  updateStateSetDiscardedDraft(discardedDraft);
}
