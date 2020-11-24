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
  PathToCommentsInfoMap,
  PatchRange,
} from '../types/common';
import {CommentSide, Side} from '../constants/constants';
import {parseDate} from './date-util';
import {LineNumber, FILE} from '../elements/diff/gr-diff/gr-diff-line';
import {CommentIdToCommentThreadMap} from '../elements/diff/gr-comment-api/gr-comment-api';
import {ChangeComments} from '../elements/diff/gr-comment-api/gr-comment-api';
import {patchNumEquals} from './patch-set-util';

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
  ported?: boolean; // is the comment ported over from a previous patchset
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

export function createCommentThreads(comments: UIComment[]) {
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
    if (!comment.__path && !comment.path) {
      throw new Error('Comment missing required "path".');
    }
    const newThread: CommentThread = {
      comments: [comment],
      patchNum: comment.patch_set,
      commentSide: comment.side ?? CommentSide.REVISION,
      path: comment.__path || comment.path!,
      line: comment.line,
      range: comment.range,
      rootId: comment.id,
      diffSide: comment.__commentSide,
    };
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

function convertToFileThread(thread: CommentThread): CommentThread {
  // create a copy so that original object is not modified
  const threadCopy = {...thread};
  threadCopy.line = FILE;
  delete threadCopy.range;
  return threadCopy;
}

export function getPortedCommentThreads(
  comments: PathToCommentsInfoMap,
  path: string,
  changeComments: ChangeComments,
  patchRange: PatchRange
): {[key in Side]: CommentThread[]} {
  const portedCommentThreads: {[key in Side]: CommentThread[]} = {
    [Side.LEFT]: [],
    [Side.RIGHT]: [],
  };
  if (!comments[path]) return portedCommentThreads;
  const portedComments = comments[path];

  // when forming threads in diff view, we filter for current patchrange but
  // ported comments will involve comments that may not belong to the
  // current patchrange, so we need to form threads for them using all
  // comments
  const allComments: UIComment[] = changeComments.getAllCommentsForPath(
    path,
    undefined,
    true
  );

  // assign __commentSide to allComments so createThreads does not throw an
  // error, proper __commentSide is assigned in gr-diff-host
  allComments.forEach(comment => (comment.__commentSide = Side.RIGHT));

  const threads: CommentThread[] = createCommentThreads(allComments).filter(
    thread => {
      const portedComment = portedComments.find(portedComment =>
        thread.comments.some(c => portedComment.id === c.id)
      );
      if (!portedComment) return false;

      const isUnresolvedOrDraft = (comment: UIComment) => {
        if ('__draft' in comment) return true;
        return 'unresolved' in comment && !!comment.unresolved;
      };

      // remove thread if resolved unless last comment is a draft
      if (!isUnresolvedOrDraft(thread.comments[thread.comments.length - 1]))
        return false;

      // assign range to threads based on ported comment
      thread.range = portedComment.range;
      thread.line = portedComment.line;
      return true;
    }
  );

  threads.forEach(thread => {
    const c = thread.comments[0];
    c.ported = true;
    c.path = path;
    if (c.side === CommentSide.PARENT) {
      // comment left on Base when comparing Base vs X
      if (
        patchRange.basePatchNum === 'PARENT' &&
        c.patch_set === patchRange!.patchNum
      ) {
        // user is comparing Base vs X so comment shows up by default
      } else {
        // comparing Base vs Y
        // comparing X vs Y
        // comparing Y vs Z
        portedCommentThreads.left.push(convertToFileThread(thread));
      }
      return;
    }

    if (
      patchNumEquals(c.patch_set, patchRange!.basePatchNum) ||
      patchNumEquals(c.patch_set, patchRange!.patchNum)
    ) {
      // no need to port this thread as it will be rendered by default
      return;
    }
    portedCommentThreads.right.push(thread);
  });

  return portedCommentThreads;
}
