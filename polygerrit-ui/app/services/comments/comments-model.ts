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
import {distinctUntilChanged, map, shareReplay, tap} from 'rxjs/operators';
import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {
  CommentInfo,
  PathToCommentsInfoMap,
  RobotCommentInfo,
  UrlEncodedCommentId,
} from '../../types/common';
import {
  addDraftProp,
  addPath,
  DraftInfo,
  isDraft,
  isUnsaved,
} from '../../utils/comment-util';

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
  // All drafts have __draft = true set.
  // Drafts that have been created during the current browser session have
  // `__draftID` set. Once they have been persisted for the first time they will
  // also get an `id`. So every draft must either have `__draftID`, `id` or both
  // set.
  drafts: {},
  // Ported comments only affect `CommentThread` properties, not individual
  // comments.
  portedComments: {},
  portedDrafts: {},
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

export const comments$ = commentState$.pipe(
  map(commentState => commentState.comments),
  distinctUntilChanged(),
  shareReplay(1)
);

export const drafts$ = commentState$.pipe(
  map(commentState => commentState.drafts),
  distinctUntilChanged(),
  shareReplay(1)
);

export const portedComments$ = commentState$.pipe(
  map(commentState => commentState.portedComments),
  distinctUntilChanged(),
  shareReplay(1)
);

export const discardedDrafts$ = commentState$.pipe(
  map(commentState => commentState.discardedDrafts),
  distinctUntilChanged(),
  shareReplay(1)
);

// Emits a new value even if only a single draft is changed. Components should
// aim to subsribe to something more specific.
export const changeComments$ = commentState$.pipe(
  tap(s =>
    console.log(
      `comment state changed comments:${count(
        s.comments
      )} robotComments:${count(s.robotComments)} drafts:${count(
        s.drafts
      )} portedComments:${count(s.portedComments)} portedDrafts:${count(
        s.portedDrafts
      )} discardedDrafts:${s.discardedDrafts.length}`
    )
  ),
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
  shareReplay(1)
);

function count(c: {[path: string]: unknown[]} = {}): number {
  let x = 0;
  for (const a of Object.values(c)) {
    x += a.length;
  }
  return x;
}

function isEmpty(c: {[path: string]: unknown[]} = {}): boolean {
  return count(c) === 0;
}

export const threads$ = changeComments$.pipe(
  map(changeComments => changeComments.getAllThreadsForChange()),
  shareReplay(1)
);

export function thread$(id: UrlEncodedCommentId) {
  return threads$.pipe(map(threads => threads.find(t => t.rootId === id)));
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
  if (isEmpty(comments) && isEmpty(nextState.comments)) return;
  console.log(`comments-model-update comments: ${JSON.stringify(comments)}`);
  nextState.comments = addPath(comments) || {};
  publishState(nextState);
}

export function updateStateRobotComments(robotComments?: {
  [path: string]: RobotCommentInfo[];
}) {
  const nextState = {...privateState$.getValue()};
  if (isEmpty(robotComments) && isEmpty(nextState.robotComments)) return;
  console.log(
    `comments-model-update robotComments: ${JSON.stringify(robotComments)}`
  );
  nextState.robotComments = addPath(robotComments) || {};
  publishState(nextState);
}

export function updateStateDrafts(drafts?: {[path: string]: DraftInfo[]}) {
  const nextState = {...privateState$.getValue()};
  if (isEmpty(drafts) && isEmpty(nextState.drafts)) return;
  console.log(`comments-model-update drafts: ${JSON.stringify(drafts)}`);
  nextState.drafts = addDraftProp(addPath(drafts));
  publishState(nextState);
}

export function updateStatePortedComments(
  portedComments?: PathToCommentsInfoMap
) {
  const nextState = {...privateState$.getValue()};
  if (isEmpty(portedComments) && isEmpty(nextState.portedComments)) return;
  console.log(
    `comments-model-update portedComments: ${JSON.stringify(portedComments)}`
  );
  nextState.portedComments = portedComments || {};
  publishState(nextState);
}

export function updateStatePortedDrafts(portedDrafts?: PathToCommentsInfoMap) {
  const nextState = {...privateState$.getValue()};
  if (isEmpty(portedDrafts) && isEmpty(nextState.portedDrafts)) return;
  console.log(
    `comments-model-update portedDrafts: ${JSON.stringify(portedDrafts)}`
  );
  nextState.portedDrafts = portedDrafts || {};
  publishState(nextState);
}

export function updateStateSetDiscardedDraft(draft: DraftInfo) {
  console.log(
    `comments-model-update add discarded draft: ${JSON.stringify(draft)}`
  );
  if (!draft.message?.trim()) {
    console.log('Lets not keep an empty darft around as discarded.');
    return;
  }
  const nextState = {...privateState$.getValue()};
  nextState.discardedDrafts = [...nextState.discardedDrafts, draft];
  publishState(nextState);
}

export function updateStateDeleteDiscardedDraft(draftID?: string) {
  const nextState = {...privateState$.getValue()};
  console.log(`comments-model-update delete discarded draft: ${draftID}`);
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
  console.log(`comments-model-update set draft: ${JSON.stringify(draft)}`);

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
  console.log(`comments-model-update delete draft: ${JSON.stringify(draft)}`);
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
