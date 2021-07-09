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
import {DraftInfo} from '../../utils/comment-util';

interface CommentState {
  comments: PathToCommentsInfoMap;
  robotComments: {[path: string]: RobotCommentInfo[]};
  drafts: {[path: string]: DraftInfo[]};
  portedComments: PathToCommentsInfoMap;
  portedDrafts: PathToCommentsInfoMap;
}

const initialState: CommentState = {
  comments: {},
  robotComments: {},
  drafts: {},
  portedComments: {},
  portedDrafts: {},
};

const privateState$ = new BehaviorSubject(initialState);

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const commentState$: Observable<CommentState> = privateState$;

export const drafts$ = commentState$.pipe(
  map(commentState => commentState.drafts),
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

export function updateStateComments(comments?: {
  [path: string]: CommentInfo[];
}) {
  const nextState = {...privateState$.getValue()};
  nextState.comments = comments || {};
  privateState$.next(nextState);
}

export function updateStateRobotComments(robotComments?: {
  [path: string]: RobotCommentInfo[];
}) {
  const nextState = {...privateState$.getValue()};
  nextState.robotComments = robotComments || {};
  privateState$.next(nextState);
}

export function updateStateDrafts(drafts?: {[path: string]: DraftInfo[]}) {
  const nextState = {...privateState$.getValue()};
  nextState.drafts = drafts || {};
  privateState$.next(nextState);
}

export function updateStatePortedComments(
  portedComments?: PathToCommentsInfoMap
) {
  const nextState = {...privateState$.getValue()};
  nextState.portedComments = portedComments || {};
  privateState$.next(nextState);
}

export function updateStatePortedDrafts(portedDrafts?: PathToCommentsInfoMap) {
  const nextState = {...privateState$.getValue()};
  nextState.portedDrafts = portedDrafts || {};
  privateState$.next(nextState);
}

export function updateStateAddDraft(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
  if (!draft.path) throw new Error('draft path undefined');
  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  if (!drafts[draft.path]) drafts[draft.path] = [] as DraftInfo[];
  else drafts[draft.path] = [...drafts[draft.path]];
  const index = drafts[draft.path].findIndex(
    d => d.__draftID === draft.__draftID || d.id === draft.id
  );
  if (index !== -1) {
    drafts[draft.path][index] = draft;
  } else {
    drafts[draft.path].push(draft);
  }
  privateState$.next(nextState);
}

export function updateStateDeleteDraft(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
  if (!draft.path) throw new Error('draft path undefined');
  nextState.drafts = {...nextState.drafts};
  const drafts = nextState.drafts;
  const index = drafts[draft.path].findIndex(
    d => d.__draftID === draft.__draftID || d.id === draft.id
  );
  if (index === -1) return;
  drafts[draft.path] = [...drafts[draft.path]].splice(index, 1);
  privateState$.next(nextState);
}
