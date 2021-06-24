/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

interface CommentState {
  changeComments?: ChangeComments;
}

const initialState: CommentState = {};

const privateState$ = new BehaviorSubject(initialState);

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const commentState$: Observable<CommentState> = privateState$;

export const changeComments$ = commentState$.pipe(
  map(commentState => commentState.changeComments)
);

export const drafts$ = changeComments$.pipe(
  map(commentState => commentState?.drafts)
);

export function updateState(changeComments: ChangeComments) {
  const current = privateState$.getValue();
  privateState$.next({...current, changeComments});
}
