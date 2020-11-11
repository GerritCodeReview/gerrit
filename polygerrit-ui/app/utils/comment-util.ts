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
import {
  CommentBasics,
  CommentInfo,
  PatchSetNum,
  RobotCommentInfo,
  Timestamp,
  UrlEncodedCommentId,
} from '../types/common';
import {CommentSide, Side} from '../constants/constants';
import {parseDate} from './date-util';
import { CommentIdToCommentThreadMap } from '../elements/diff/gr-comment-api/gr-comment-api';

export interface DraftCommentProps {
  __draft?: boolean;
  __draftID?: string;
  __date?: Date;
}

export type DraftInfo = CommentBasics & DraftCommentProps;

/**
 * Each of the type implements or extends CommentBasics.
 */
export type Comment = DraftInfo | CommentInfo | RobotCommentInfo;

export interface UIStateCommentProps {
  // The `side` of the comment is PARENT or REVISION, but this is LEFT or RIGHT.
  // TODO(TS): Remove the naming confusion of commentSide being of type of Side,
  // but side being of type CommentSide. :-)
  __commentSide?: Side;
  // TODO(TS): Remove this. Seems to be exactly the same as `path`??
  __path?: string;
  collapsed?: boolean;
  // TODO(TS): Consider allowing this only for drafts.
  __editing?: boolean;
  __otherEditing?: boolean;
}

export type UIDraft = DraftInfo & UIStateCommentProps;

export type UIHuman = CommentInfo & UIStateCommentProps;

export type UIRobot = RobotCommentInfo & UIStateCommentProps;

export type UIComment = UIHuman | UIRobot | UIDraft;

export type CommentMap = {[path: string]: boolean};

export function isRobot<T extends CommentInfo>(
  x: T | DraftInfo | RobotCommentInfo | undefined
): x is RobotCommentInfo {
  return !!x && !!(x as RobotCommentInfo).robot_id;
}

export function isDraft<T extends CommentInfo>(
  x: T | UIDraft | undefined
): x is UIDraft {
  return !!x && !!(x as UIDraft).__draft;
}

interface SortableComment {
  __draft?: boolean;
  __date?: Date;
  updated?: Timestamp;
  id?: UrlEncodedCommentId;
}

export function sortComments<T extends SortableComment>(comments: T[]): T[] {
  return comments.slice(0).sort((c1, c2) => {
    const d1 = !!c1.__draft;
    const d2 = !!c2.__draft;
    if (d1 !== d2) return d1 ? 1 : -1;

    const date1 = (c1.updated && parseDate(c1.updated)) || c1.__date;
    const date2 = (c2.updated && parseDate(c2.updated)) || c2.__date;
    const dateDiff = date1!.valueOf() - date2!.valueOf();
    if (dateDiff !== 0) return dateDiff;

    const id1 = c1.id ?? '';
    const id2 = c2.id ?? '';
    return id1.localeCompare(id2);
  });
}

/**
  * Computes all of the comments in thread format.
  *
  * @param comments sorted by updated timestamp.
  */
export function getCommentThreads(comments: UIComment[]) {
  const threads: CommentThread[] = [];
  const idThreadMap: CommentIdToCommentThreadMap = {};
  for (const comment of comments) {
    if (!comment.id) continue;
    // If the comment is in reply to another comment, find that comment's
    // thread and append to it.
    if (comment.in_reply_to) {
      const thread = idThreadMap[comment.in_reply_to];
      if (thread) {
        thread.comments.push(comment);
        idThreadMap[comment.id] = thread;
        continue;
      }
    }

    // Otherwise, this comment starts its own thread.
    if (!comment.__path && !comment.path) {
      throw new Error('Comment missing required "path".');
    }
    const newThread: CommentThread = {
      comments: [comment],
      patchNum: comment.patch_set,
      path: comment.__path || comment.path!,
      line: comment.line,
      rootId: comment.id,
    };
    if (comment.side) {
      newThread.commentSide = comment.side;
    }
    newThread.diffSide = comment.__commentSide;
    threads.push(newThread);
    idThreadMap[comment.id] = newThread;
  }
  return threads;
}

export interface CommentThread {
  comments: UIComment[];
  patchNum?: PatchSetNum;
  path: string;
  // TODO(TS): It would be nice to use LineNumber here, but the comment thread
  // element actually relies on line to be undefined for file comments. Be
  // aware of element attribute getters and setters, if you try to refactor
  // this. :-) Still worthwhile to do ...
  line?: number;
  rootId: UrlEncodedCommentId;
  diffSide?: Side;
  commentSide?: CommentSide;
}

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
