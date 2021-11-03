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
import {distinctUntilChanged, map} from 'rxjs/operators';
import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {
  CommentInfo,
  PathToCommentsInfoMap,
  RobotCommentInfo,
} from '../../types/common';
import {addPath, DraftInfo} from '../../utils/comment-util';

interface CommentState {
  comments: PathToCommentsInfoMap;
  robotComments: {[path: string]: RobotCommentInfo[]};
  drafts: {[path: string]: DraftInfo[]};
  portedComments: PathToCommentsInfoMap;
  portedDrafts: PathToCommentsInfoMap;
  /**
   * If a draft is discarded by the user, then we temporarily keep it in this
   * array in case the user decides to Undo the discard operation and bring the
   * draft back. Once restored, the draft is removed from this array.
   */
  discardedDrafts: DraftInfo[];
}

const initialState: CommentState = {
  comments: {},
  robotComments: {},
  drafts: {},
  portedComments: {},
  portedDrafts: {},
  discardedDrafts: [],
};

const privateState$ = new BehaviorSubject(initialState);

export function _testOnly_resetState() {
  // We cannot assign a new subject to privateState$, because all the selectors
  // have already subscribed to the original subject. So we have to emit the
  // initial state on the existing subject.
  privateState$.next(initialState);
}

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const commentState$: Observable<CommentState> = privateState$;

export function _testOnly_getState() {
  return privateState$.getValue();
}

export function _testOnly_setState(state: CommentState) {
  privateState$.next(state);
}

export const comments$ = commentState$.pipe(
  map(commentState => commentState.comments),
  distinctUntilChanged()
);

export const drafts$ = commentState$.pipe(
  map(commentState => commentState.drafts),
  distinctUntilChanged()
);

export const portedComments$ = commentState$.pipe(
  map(commentState => commentState.portedComments),
  distinctUntilChanged()
);

export const discardedDrafts$ = commentState$.pipe(
  map(commentState => commentState.discardedDrafts),
  distinctUntilChanged()
);

// Emits a new value even if only a single draft is changed. Components should
// aim to subsribe to something more specific.
export const changeComments$ = commentState$.pipe(
  map(
    commentState =>
      new ChangeComments(
        commentState.comments,
        commentState.robotComments,
        commentState.drafts,
        commentState.portedComments,
        commentState.portedDrafts
      )
  ),
  distinctUntilChanged()
);

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
  nextState.comments = addPath(comments) || {};
  publishState(nextState);
}

export function updateStateRobotComments(robotComments?: {
  [path: string]: RobotCommentInfo[];
}) {
  const nextState = {...privateState$.getValue()};
  nextState.robotComments = addPath(robotComments) || {};
  publishState(nextState);
}

export function updateStateDrafts(drafts?: {[path: string]: DraftInfo[]}) {
  const nextState = {...privateState$.getValue()};
  nextState.drafts = addPath(drafts) || {};
  publishState(nextState);
}

export function updateStatePortedComments(
  portedComments?: PathToCommentsInfoMap
) {
  const nextState = {...privateState$.getValue()};
  nextState.portedComments = portedComments || {};
  publishState(nextState);
}

export function updateStatePortedDrafts(portedDrafts?: PathToCommentsInfoMap) {
  const nextState = {...privateState$.getValue()};
  nextState.portedDrafts = portedDrafts || {};
  publishState(nextState);
}

export function updateStateAddDiscardedDraft(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
  nextState.discardedDrafts = [...nextState.discardedDrafts, draft];
  publishState(nextState);
}

export function updateStateUndoDiscardedDraft(draftID?: string) {
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

export function updateStateAddDraft(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
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
  publishState(nextState);
}

export function updateStateUpdateDraft(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
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
  publishState(nextState);
}

export function updateStateDeleteDraft(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
  if (!draft.path) throw new Error('draft path undefined');
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
  publishState(nextState);
  updateStateAddDiscardedDraft(discardedDraft);
}
