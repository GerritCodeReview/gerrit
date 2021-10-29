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
  ContextLine,
  BasePatchSetNum,
  RevisionPatchSetNum,
  AccountInfo,
  AccountDetailInfo,
} from '../types/common';
import {CommentSide, Side, SpecialFilePath} from '../constants/constants';
import {parseDate} from './date-util';
import {LineNumber} from '../elements/diff/gr-diff/gr-diff-line';
import {CommentIdToCommentThreadMap} from '../elements/diff/gr-comment-api/gr-comment-api';
import {isMergeParent, getParentIndex} from './patch-set-util';
import {DiffInfo} from '../types/diff';

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

export function createCommentThreads(
  comments: UIComment[],
  patchRange?: PatchRange
) {
  const sortedComments = sortComments(comments);
  const threads: CommentThread[] = [];
  const idThreadMap: CommentIdToCommentThreadMap = {};
  for (const comment of sortedComments) {
    // thread and append to it.
    if (comment.in_reply_to) {
      const thread = idThreadMap[comment.in_reply_to];
      if (thread) {
        thread.comments.push(comment);
        if (comment.id) idThreadMap[comment.id] = thread;
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
      diffSide: Side.LEFT,
      commentSide: comment.side ?? CommentSide.REVISION,
      mergeParentNum: comment.parent,
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
    if (comment.id) idThreadMap[comment.id] = newThread;
  }
  return threads;
}

export interface CommentThread {
  comments: UIComment[];
  path: string;
  commentSide: CommentSide;
  /* mergeParentNum is the merge parent number only valid for merge commits
     when commentSide is PARENT.
     mergeParentNum is undefined for auto merge commits
  */
  mergeParentNum?: number;
  patchNum?: PatchSetNum;
  line?: LineNumber;
  /* rootId is optional since we create a empty comment thread element for
     drafts and then create the draft which becomes the root */
  rootId?: UrlEncodedCommentId;
  diffSide?: Side;
  range?: CommentRange;
  ported?: boolean; // is the comment ported over from a previous patchset
  rangeInfoLost?: boolean; // if BE was unable to determine a range for this
}

export function getLastComment(thread?: CommentThread): UIComment | undefined {
  const len = thread?.comments.length;
  return thread && len ? thread.comments[len - 1] : undefined;
}

export function getFirstComment(thread?: CommentThread): UIComment | undefined {
  return thread?.comments?.[0];
}

export function countComments(thread?: CommentThread) {
  return thread?.comments?.length ?? 0;
}

export function isPatchsetLevel(thread?: CommentThread): boolean {
  return thread?.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
}

export function isUnresolved(thread?: CommentThread): boolean {
  return !isResolved(thread);
}

export function isResolved(thread?: CommentThread): boolean {
  return !getLastComment(thread)?.unresolved;
}

export function isDraftThread(thread?: CommentThread): boolean {
  return isDraft(getLastComment(thread));
}

export function isRobotThread(thread?: CommentThread): boolean {
  return isRobot(getFirstComment(thread));
}

export function hasHumanReply(thread?: CommentThread): boolean {
  return countComments(thread) > 1 && !isRobot(getLastComment(thread));
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
    comment.patch_set === range.patchNum
  ) {
    return true;
  }
  // If the base of the range is not the parent of the patch:
  return (
    range.basePatchNum !== ParentPatchSetNum &&
    comment.side !== CommentSide.PARENT &&
    comment.patch_set === range.basePatchNum
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
    comment.side !== CommentSide.PARENT && comment.patch_set === range.patchNum
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

export function getPatchRangeForCommentUrl(
  comment: UIComment,
  latestPatchNum: RevisionPatchSetNum
) {
  if (!comment.patch_set) throw new Error('Missing comment.patch_set');

  // TODO(dhruvsri): Add handling for comment left on parents of merge commits
  if (comment.side === CommentSide.PARENT) {
    if (comment.patch_set === ParentPatchSetNum)
      throw new Error('diffSide cannot be PARENT');
    return {
      patchNum: comment.patch_set as RevisionPatchSetNum,
      basePatchNum: ParentPatchSetNum,
    };
  } else if (latestPatchNum === comment.patch_set) {
    return {
      patchNum: latestPatchNum,
      basePatchNum: ParentPatchSetNum,
    };
  } else {
    return {
      patchNum: latestPatchNum as RevisionPatchSetNum,
      basePatchNum: comment.patch_set as BasePatchSetNum,
    };
  }
}

export function computeDiffFromContext(
  context: ContextLine[],
  path: string,
  content_type?: string
) {
  // do not render more than 20 lines of context
  context = context.slice(0, 20);
  const diff: DiffInfo = {
    meta_a: {
      name: '',
      content_type: '',
      lines: 0,
      web_links: [],
    },
    meta_b: {
      name: path,
      content_type: content_type || '',
      lines: context.length + context?.[0].line_number,
      web_links: [],
    },
    change_type: 'MODIFIED',
    intraline_status: 'OK',
    diff_header: [],
    content: [
      {
        skip: context[0].line_number - 1,
      },
      {
        b: context.map(line => line.context_line),
      },
    ],
  };
  return diff;
}

export function getCommentAuthors(
  threads?: CommentThread[],
  user?: AccountDetailInfo
) {
  if (!threads || !user) return [];
  const ids = new Set();
  const authors: AccountInfo[] = [];
  threads.forEach(t =>
    t.comments.forEach(c => {
      if (isDraft(c) && !ids.has(user._account_id)) {
        ids.add(user._account_id);
        authors.push(user);
        return;
      }
      if (c.author && !ids.has(c.author._account_id)) {
        ids.add(c.author._account_id);
        authors.push(c.author);
      }
    })
  );
  return authors;
}

export function computeId(comment: UIComment) {
  if (comment.id) return comment.id;
  if (isDraft(comment)) return comment.__draftID;
  throw new Error('Missing id in root comment.');
}

/**
 * Add path info to every comment as CommentInfo returned
 * from server does not have that.
 *
 * TODO(taoalpha): should consider changing BE to send path
 * back within CommentInfo
 */
export function addPath<T>(comments: {[path: string]: T[]} = {}): {
  [path: string]: Array<T & {path: string}>;
} {
  const updatedComments: {[path: string]: Array<T & {path: string}>} = {};
  for (const filePath of Object.keys(comments)) {
    const allCommentsForPath = comments[filePath] || [];
    if (allCommentsForPath.length) {
      updatedComments[filePath] = allCommentsForPath.map(comment => {
        return {...comment, path: filePath};
      });
    }
  }
  return updatedComments;
}
