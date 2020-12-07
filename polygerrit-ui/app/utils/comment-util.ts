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
  CommentRange,
  PatchRange,
  ParentPatchSetNum,
} from '../types/common';
import {CommentSide, Side} from '../constants/constants';
import {parseDate} from './date-util';
import {LineNumber} from '../elements/diff/gr-diff/gr-diff-line';
import {CommentIdToCommentThreadMap} from '../elements/diff/gr-comment-api/gr-comment-api';
import {isMergeParent, getParentIndex, patchNumEquals} from './patch-set-util';

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
  collapsed?: boolean;
}

export interface UIStateDraftProps {
  __editing?: boolean;
}

export type UIDraft = DraftInfo & UIStateCommentProps & UIStateDraftProps;

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

export function createCommentThreads(
  comments: UIComment[],
  patchRange?: PatchRange
) {
  const sortedComments = sortComments(comments);
  const threads: CommentThread[] = [];
  const idThreadMap: CommentIdToCommentThreadMap = {};
  for (const comment of sortedComments) {
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
    if (!comment.path) {
      throw new Error('Comment missing required "path".');
    }
    const newThread: CommentThread = {
      comments: [comment],
      patchNum: comment.patch_set,
      commentSide: comment.side ?? CommentSide.REVISION,
      path: comment.path,
      line: comment.line,
      range: comment.range,
      rootId: comment.id,
    };
    if (patchRange) {
      if (isInBaseOfPatchRange(comment, patchRange))
        newThread.diffSide = Side.LEFT;
      else if (isInRevisionOfPatchRange(comment, patchRange))
        newThread.diffSide = Side.RIGHT;
      else throw new Error('comment does not belong in given patchrange');
    }
    if (!comment.line && !comment.range) {
      newThread.line = 'FILE';
    }
    threads.push(newThread);
    idThreadMap[comment.id] = newThread;
  }
  return threads;
}

export interface CommentThread {
  comments: UIComment[];
  path: string;
  commentSide: CommentSide;
  patchNum?: PatchSetNum;
  line?: LineNumber;
  /* rootId is optional since we create a empty comment thread element for
     drafts and then create the draft which becomes the root */
  rootId?: UrlEncodedCommentId;
  diffSide?: Side;
  range?: CommentRange;
  ported?: boolean; // is the comment ported over from a previous patchset
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

/**
 * Whether the given comment should be included in the base side of the
 * given patch range.
 */
export function isInBaseOfPatchRange(
  comment: CommentBasics,
  range: PatchRange
) {
  // If the base of the patch range is a parent of a merge, and the comment
  // appears on a specific parent then only show the comment if the parent
  // index of the comment matches that of the range.
  if (comment.parent && comment.side === CommentSide.PARENT) {
    return (
      isMergeParent(range.basePatchNum) &&
      comment.parent === getParentIndex(range.basePatchNum)
    );
  }

  // If the base of the range is the parent of the patch:
  if (
    range.basePatchNum === ParentPatchSetNum &&
    comment.side === CommentSide.PARENT &&
    patchNumEquals(comment.patch_set, range.patchNum)
  ) {
    return true;
  }
  // If the base of the range is not the parent of the patch:
  return (
    range.basePatchNum !== ParentPatchSetNum &&
    comment.side !== CommentSide.PARENT &&
    patchNumEquals(comment.patch_set, range.basePatchNum)
  );
}

/**
 * Whether the given comment should be included in the revision side of the
 * given patch range.
 */
export function isInRevisionOfPatchRange(
  comment: CommentBasics,
  range: PatchRange
) {
  return (
    comment.side !== CommentSide.PARENT &&
    patchNumEquals(comment.patch_set, range.patchNum)
  );
}

/**
 * Whether the given comment should be included in the given patch range.
 */
export function isInPatchRange(
  comment: CommentBasics,
  range: PatchRange
): boolean {
  return (
    isInBaseOfPatchRange(comment, range) ||
    isInRevisionOfPatchRange(comment, range)
  );
}
