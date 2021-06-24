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
import {map} from 'rxjs/operators';
import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {PathToCommentsInfoMap} from '../../types/common';
import {DraftInfo, UIDraft, UIHuman, UIRobot} from '../../utils/comment-util';

interface CommentState {
  comments?: {[path: string]: UIHuman[]};
  robotComments?: {[path: string]: UIRobot[]};
  drafts?: {[path: string]: UIDraft[]};
  portedComments?: PathToCommentsInfoMap;
  portedDrafts?: PathToCommentsInfoMap;
}

const initialState: CommentState = {};

const privateState$ = new BehaviorSubject(initialState);

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const commentState$: Observable<CommentState> = privateState$;

export const drafts$ = commentState$.pipe(
  map(commentState => commentState.drafts)
);

export function changeCommentsUpdateState(changeComments: ChangeComments) {
  privateState$.next({...changeComments});
}

export function addDraftUpdateState(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
  if (!draft.path) throw new Error('draft path undefined');
  nextState.drafts = nextState.drafts || {};
  const drafts = nextState.drafts;
  if (!drafts[draft.path]) drafts[draft.path] = [] as DraftInfo[];
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

export function deleteDraftUpdateState(draft: DraftInfo) {
  const nextState = {...privateState$.getValue()};
  if (!draft.path) throw new Error('draft path undefined');
  nextState.drafts = nextState.drafts || {};
  const drafts = nextState.drafts;
  const index = drafts[draft.path].findIndex(
    d => d.__draftID === draft.__draftID || d.id === draft.id
  );
  if (index === -1) return;
  drafts[draft.path].splice(index, 1);
  privateState$.next(nextState);
}
