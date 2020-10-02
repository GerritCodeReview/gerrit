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
// TODO(brohlfs): Do not depend from util/ on elements/.
import {
  CommentThread,
  isDraft,
  UIComment,
} from '../elements/diff/gr-comment-api/gr-comment-api';

export function getLastComment(thread?: CommentThread): UIComment | undefined {
  const len = thread?.comments.length;
  return thread && len ? thread.comments[len - 1] : undefined;
}

export function isUnresolved(thread?: CommentThread): boolean {
  return !!getLastComment(thread)?.unresolved;
}

export function isDraftThread(thread?: CommentThread): boolean {
  return isDraft(getLastComment(thread));
}
